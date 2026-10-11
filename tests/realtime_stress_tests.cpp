#include "realtime_search.hpp"
#include "verified_input_queue.hpp"
#include "trace_geometry.hpp"
#include "prefix_cache.hpp"
#include "input_motion.hpp"
#include "flight_certificate.hpp"
#include <iostream>
#include <random>
#include <stdexcept>
#include <string>

using namespace agb;
static std::string context;
static void verify(bool ok, char const *why) {
    if (!ok) throw std::runtime_error(context + ": " + why);
}
static double distance(Point p, Point a, Point b) {
    double dx = b.x - a.x, dy = b.y - a.y;
    double norm = dx * dx + dy * dy;
    double u = norm > 0 ? std::clamp(((p.x-a.x)*dx+(p.y-a.y)*dy)/norm,0.,1.) : 0;
    return std::hypot(p.x-a.x-u*dx,p.y-a.y-u*dy);
}
static void result(char const *name, int cases, int extra = -1) {
    std::cout << "{\"group\":\"" << name << "\",\"cases\":" << cases;
    if (extra >= 0) std::cout << ",\"extra\":" << extra;
    std::cout << "}\n";
}

// Independent variable-step fixture, NOT Geometry Dash physics. A point body
// follows an analytic parabola on a flat floor. Collision tests sweep the
// whole horizontal obstacle interval, rather than sampling only its ends.
struct JumpBody { double x=0, y=0, vy=0; bool ground=true; };
struct ObstacleOracle {
    double dt=1./240, speed=240, obstacle=75, width=15, height=12;
    JumpBody root;
    int calls=0, coldCalls=0;
    bool corrupt=false;
    static constexpr double impulse=528, gravity=4608;
    bool step(JumpBody &b, bool down) const {
        if (down && b.ground) { b.vy=impulse; b.ground=false; }
        auto old=b;
        double lo=std::max(old.x,obstacle), hi=std::min(old.x+speed*dt,obstacle+width);
        auto yAt=[&](double x) {
            double t=(x-old.x)/speed;
            return old.ground ? 0. : old.y+old.vy*t-.5*gravity*t*t;
        };
        if (lo<=hi && std::min(yAt(lo),yAt(hi))<height) return false;
        b.x+=speed*dt;
        if (!b.ground) {
            b.y+=b.vy*dt-.5*gravity*dt*dt; b.vy-=gravity*dt;
            if (b.y<=0 && b.vy<=0) { b.y=b.vy=0; b.ground=true; }
        }
        return true;
    }
    Evaluation prefix(Plan const &p, Clock::time_point end, bool memo) {
        ++calls; coldCalls+=!memo;
        Evaluation e; e.hasRealized=true;
        e.clearance=e.nearClearance=e.landingMargin=120;
        auto b=root; FlightCertificate cert(b.ground); CubePulse pulse;
        ModeState mode; mode.mode=PlannerMode::Cube;
        bool held=false;
        auto sample=[&] {
            e.traces[0].push_back({float(b.x),float(b.y)});
            e.modeTraces[0].push_back(mode);
            MotionSample m; m.velocity=b.vy; m.canJump=b.ground;
            // Secondary contact can permit a press before primary ground.
            m.grounded=false; m.nativeDetails=true; m.xVelocity=speed;
            m.gravity=gravity; m.contactFlags=b.ground ? 2 : 0;
            if (!memo && corrupt) m.usedInteractions=1;
            e.motions[0].push_back(m);
        };
        sample();
        for (int t=0;t<p.length;++t) {
            if (Clock::now()>=end) { e.interrupted=true; break; }
            bool ground=b.ground;
            if (ground&&!pulse.mustRelease()) {
                e.groundedActions.push_back(t); e.controlActions.push_back({t,mode.mode});
            }
            bool down=pulse.command(p.at(t),true,ground);
            e.realized.buttons[t]=down;
            e.buttonChanges+=down!=held; held=down;
            if (!step(b,down)) { e.died=e.nativeDeath=true; break; }
            if (ground&&!b.ground&&down) ++e.cubeTakeoffs;
            if (!ground&&b.ground) {
                ++e.landingCount;
                if (e.firstLandingTick<0) e.firstLandingTick=t;
            }
            if (!down&&ground&&b.ground&&e.groundedPrefixTicks==t) ++e.groundedPrefixTicks;
            cert.observe(ground,b.ground,down);
            double dx=std::max({obstacle-b.x,b.x-obstacle-width,0.});
            double dy=std::max(b.y-height,0.);
            float margin=std::hypot(dx,dy);
            e.endpointClearance=margin; e.clearance=std::min(e.clearance,margin);
            if (!b.ground) e.landingMargin=std::min(e.landingMargin,margin);
            ++e.survived; e.realized.length=e.survived; sample();
            if (p.stopAtLanding&&cert.required&&cert.verified()&&t>=lastRequestedTick(p)) break;
        }
        e.requiresLanding=cert.required;
        e.landingVerified=cert.required&&cert.verified();
        e.firstActionVerified=cert.verified();
        return e;
    }
};

static void obstacleMatrix() {
    int cases=0, feasible=0, full=0, waits=0, roomy=0, cleared=0;
    int safeNeighbours=0, wideAvailable=0, widestSelected=0;
    std::vector<double> timings;
    for (int tps : {60,120,240,360})
    for (double factor : {.75,1.,1.25,1.5})
    for (double offset : {45.,60.,75.,90.,105.,120.})
    for (double width : {5.,10.,15.})
    for (double height : {6.,12.,18.}) {
        ++cases; context="obstacle case="+std::to_string(cases)+" tps="+std::to_string(tps);
        ObstacleOracle oracle; oracle.dt=1./tps; oracle.speed=240*factor;
        oracle.obstacle=offset; oracle.width=width; oracle.height=height;
        Config c; c.tickSeconds=oracle.dt; c.horizon=int(std::ceil(.9/ oracle.dt));
        c.budgetMs=8; c.candidates=96; c.priorityControl=c.cubePulsePlanning=true;
        c.plannerMode=PlannerMode::Cube;
        int edge=int(std::floor(offset/(oracle.speed*oracle.dt)));
        // Geometry-only timing hints: regular earlier offsets, without asking
        // the exhaustive safety oracle which of them actually works.
        for (double lead : {.035,.055,.075,.095,.115,.135,.155,.175}) {
            int at=std::max(0,edge-int(std::ceil(lead/oracle.dt)));
            if (c.jumpEventCount==0 || c.jumpEvents[c.jumpEventCount-1]!=at)
                c.jumpEvents[c.jumpEventCount++]=at;
        }
        // Independent exhaustive ground truth for this single obstacle.
        int safeCount=0;
        std::vector<bool> safeTakeoffs(edge);
        for (int at=0;at<edge;++at) {
            JumpBody b; bool safe=true;
            for (int t=0;t<c.horizon;++t) if (!oracle.step(b,t==at)) { safe=false; break; }
            safeTakeoffs[at]=safe&&b.ground&&b.x>offset+width;
            safeCount+=safeTakeoffs[at];
        }
        bool wide=false;
        for(int at=1;at+1<edge;++at) wide|=safeTakeoffs[at-1]&&safeTakeoffs[at]&&safeTakeoffs[at+1];
        wideAvailable+=wide;
        feasible+=safeCount>0;
        RealtimeSearch search;
        auto d=search.decide(c,false,oracle);
        timings.push_back(d.elapsedMs);
        verify(d.candidates<=96&&oracle.calls<=100,"candidate or oracle bound exceeded");
        verify(!(d.hasValidatedInput&&d.recoveryInput),"input classified both validated and recovery");
        if (d.hasValidatedInput) {
            verify(d.certificateTicks>0&&d.certificateTicks==d.plan.length,"bad certificate length");
            verify(!d.selected.died&&!d.selected.interrupted&&d.selected.firstActionVerified,"bad control proof");
            JumpBody replay; bool jumped=false;
            for (int t=0;t<d.plan.length;++t) {
                jumped|=d.plan.at(t);
                verify(oracle.step(replay,d.plan.at(t)),"authorized path dies on independent replay");
            }
            if (jumped) {
                ++full; verify(replay.ground&&d.selected.landingVerified,"authorized launch lacks landing");
                cleared+=replay.x>offset+width;
                int at=0; while(at<d.plan.length&&!d.plan.at(at)) ++at;
                bool neighbors=at>0&&at+1<edge&&safeTakeoffs[at-1]&&safeTakeoffs[at+1];
                safeNeighbours+=neighbors;
                widestSelected+=wide&&neighbors;
                verify(safeCount>0,"launch authorized for an exhaustively impossible scene");
                roomy+=d.selected.landingMargin>4;
            } else ++waits;
            std::array<ModeState,2> modes{d.selected.modeTraces[0][0],{}};
            VerifiedInputQueue q;
            verify(q.seed(d,modes,false,c.tickSeconds),"checked path cannot seed executable queue");
            for (int t=0;t<d.plan.length;++t) {
                std::array<Point,2> points{d.selected.traces[0][t],{}};
                std::array<MotionSample,2> motions{d.selected.motions[0][t],{}};
                verify(q.matches(points,modes,false,c.tickSeconds,&motions),"queue rejects exact replay state");
                verify(q.down()==d.plan.at(t),"queue changes a certified command");
                q.consume(d.plan.at(t));
            }
            verify(q.remaining()==0,"queue extends past certificate");
        } else if (d.recoveryInput) {
            verify(!d.down&&d.recoveryTicks==1,"unproved cube recovery launches or queues a tail");
            JumpBody replay; verify(oracle.step(replay,d.down),"one-step recovery dies");
        }
        // Finite search may miss feasible routes; report its actual coverage.
    }
    std::sort(timings.begin(),timings.end());
    result("obstacle_matrix",cases,feasible);
    std::cout<<"{\"group\":\"obstacle_coverage\",\"landed_routes\":"<<full
             <<",\"safe_waits\":"<<waits<<",\"margin_over_4\":"<<roomy
             <<",\"cleared_obstacles\":"<<cleared
             <<",\"both_adjacent_takeoffs_safe\":"<<safeNeighbours
             <<",\"three_tick_window_available\":"<<wideAvailable
             <<",\"selected_in_three_tick_window\":"<<widestSelected
             <<",\"p50_ms\":"<<timings[timings.size()/2]
             <<",\"p95_ms\":"<<timings[timings.size()*95/100]
             <<",\"max_ms\":"<<timings.back()<<"}\n";
}

static void closedLoopMatrix() {
    int cases=0, completed=0, deaths=0, noInput=0, commands=0;
    for (int tps:{60,120,240,360}) for (double factor:{.75,1.,1.25,1.5})
    for (double offset:{45.,75.,105.}) for (double width:{5.,15.}) {
        context="closed loop case="+std::to_string(cases);
        ObstacleOracle oracle; oracle.dt=1./tps; oracle.speed=240*factor;
        oracle.obstacle=offset; oracle.width=width; oracle.height=12;
        RealtimeSearch search;
        bool held=false, dead=false, stalled=false;
        for (int tick=0;tick<int(tps*1.2)&&oracle.root.x<offset+width+30;++tick) {
            Config c; c.tickSeconds=oracle.dt; c.horizon=int(std::ceil(.9/oracle.dt));
            c.budgetMs=8; c.candidates=96; c.priorityControl=c.cubePulsePlanning=true;
            c.plannerMode=PlannerMode::Cube; c.forceRelease=held||!oracle.root.ground;
            int edge=int(std::floor((offset-oracle.root.x)/(oracle.speed*oracle.dt)));
            for(double lead:{.035,.055,.075,.095,.115,.135,.155,.175}) {
                int at=std::max(0,edge-int(std::ceil(lead/oracle.dt)));
                if(c.jumpEventCount==0||c.jumpEvents[c.jumpEventCount-1]!=at)
                    c.jumpEvents[c.jumpEventCount++]=at;
            }
            auto d=search.decide(c,held,oracle); ++commands;
            if(!d.hasValidatedInput&&!d.recoveryInput) { stalled=true; break; }
            if (d.hasValidatedInput) verify(!d.selected.died&&!d.selected.interrupted,"closed loop accepted failed proof");
            bool down=d.down;
            if(!oracle.step(oracle.root,down)) { dead=true; break; }
            held=down; search.advance();
        }
        completed+=!dead&&!stalled&&oracle.root.x>=offset+width+30;
        deaths+=dead; noInput+=stalled; ++cases;
    }
    // Quality statistics are separate from proof-safety assertions. A finite
    // planner can miss a route; keep these counts visible instead of hiding
    // unsuccessful closed-loop scenes behind a successful test exit code.
    std::cout<<"{\"group\":\"closed_loop_matrix\",\"cases\":"<<cases
             <<",\"completed\":"<<completed<<",\"deaths\":"<<deaths
             <<",\"no_checked_input\":"<<noInput<<",\"commands\":"<<commands<<"}\n";
}

static Decision generatedProof(std::mt19937 &rng, int length, bool dual, PlannerMode mode) {
    Decision d; d.hasValidatedInput=true; d.plan.length=d.certificateTicks=length;
    d.selected.survived=length; d.selected.firstActionVerified=true; d.selected.hasRealized=true;
    d.forecastPlan.length=std::min(length+17,MaxHorizon);
    d.forecast.survived=d.forecastPlan.length;
    for (int t=0;t<d.forecastPlan.length;++t) {
        bool down=rng()%2;
        d.forecastPlan.buttons[t]=down;
        if (t<length) d.plan.buttons[t]=down;
    }
    d.down=d.plan.at(0); d.selected.realized=d.plan; d.forecast.realized=d.forecastPlan;
    for (int i=0;i<(dual?2:1);++i) for (int t=0;t<=d.forecastPlan.length;++t) {
        Point p{float(t)*1.125f,float(i)*20+std::sin(float(t)*.04f)};
        ModeState s; s.mode=mode; s.mini=i; s.speed=900;
        if (t>=length/2) s.upsideDown=true;
        MotionSample m; m.nativeDetails=true; m.velocity=float(t)*.125;
        m.xVelocity=9; m.gravity=.216; m.gravityMod=1; m.rotation=float(t)*7;
        m.contactFlags=(t%7); m.usedInteractions=t/13;
        m.force={.125f,-.25f}; m.buffered=t%2; m.grounded=m.canJump=t%5==0;
        d.forecast.traces[i].push_back(p); d.forecast.modeTraces[i].push_back(s); d.forecast.motions[i].push_back(m);
        if (t<=length) {
            d.selected.traces[i].push_back(p); d.selected.modeTraces[i].push_back(s); d.selected.motions[i].push_back(m);
        }
    }
    return d;
}
static void queueMatrix() {
    std::mt19937 rng(0xa17b09);
    int cases=0, steps=0;
    for (int iteration=0;iteration<1200;++iteration) {
        context="queue case="+std::to_string(iteration);
        bool dual=iteration%2;
        auto mode=PlannerMode(iteration%9);
        int length=iteration<3 ? std::array{1,768,1024}[iteration] : 1+rng()%80;
        auto d=generatedProof(rng,length,dual,mode);
        auto dt=1./std::array{60,120,240,360}[iteration%4];
        std::array<ModeState,2> states{};
        for (int i=0;i<(dual?2:1);++i) states[i]=d.selected.modeTraces[i][0];
        VerifiedInputQueue q;
        verify(q.seed(d,states,dual,dt),"valid generated certificate rejected");
        for (int t=0;t<length;++t) {
            ++steps;
            std::array<Point,2> positions{}; std::array<MotionSample,2> motions{};
            for (int i=0;i<(dual?2:1);++i) {
                positions[i]=d.selected.traces[i][t]; states[i]=d.selected.modeTraces[i][t]; motions[i]=d.selected.motions[i][t];
            }
            verify(q.matches(positions,states,dual,dt,&motions),"exact certificate rejected");
            auto bad=positions; bad[dual?1:0].x+=.08f;
            verify(!q.matches(bad,states,dual,dt,&motions),"position divergence accepted");
            auto wrongMode=states; wrongMode[dual?1:0].goingLeft=true;
            verify(!q.matches(positions,wrongMode,dual,dt,&motions),"mode divergence accepted");
            auto wrongMotion=motions; wrongMotion[dual?1:0].usedInteractions^=1;
            verify(!q.matches(positions,states,dual,dt,&wrongMotion),"consumed interaction divergence accepted");
            wrongMotion=motions; wrongMotion[dual?1:0].force.x+=.002f;
            verify(!q.matches(positions,states,dual,dt,&wrongMotion),"force divergence accepted");
            verify(!q.matches(positions,states,!dual,dt,&motions),"dual divergence accepted");
            verify(!q.matches(positions,states,dual,dt*1.051,&motions),"clock divergence accepted");
            bad=positions; bad[0].y=std::numeric_limits<float>::quiet_NaN();
            verify(!q.matches(bad,states,dual,dt,&motions),"nonfinite position accepted");
            verify(q.down()==d.plan.at(t),"certified pulse changed");
            if (t==length/2) {
                auto suffix=q.continuation();
                verify(suffix.certificateTicks==length-t&&suffix.plan.length==length-t,"bad checked suffix length");
                verify(suffix.forecastTicks==d.forecast.survived-t,"far forecast suffix lost");
                for (int k=0;k<suffix.plan.length;++k) verify(suffix.plan.at(k)==d.plan.at(t+k),"suffix inputs changed");
            }
            q.consume(d.plan.at(t));
        }
        verify(q.remaining()==0&&!q.continuation().hasValidatedInput,"expired queue remains authorized");
        // Every kind of non-certificate must fail to seed an executable queue.
        auto rejected=d; rejected.hasValidatedInput=false;
        verify(!q.seed(rejected,states,dual,dt),"unvalidated proposal seeded queue");
        rejected=d; rejected.selected.died=true;
        verify(!q.seed(rejected,states,dual,dt),"fatal proposal seeded queue");
        rejected=d; rejected.selected.interrupted=true;
        verify(!q.seed(rejected,states,dual,dt),"unfinished proposal seeded queue");
        verify(!q.seed(d,states,dual,0)&&!q.seed(d,states,dual,NAN),"invalid clock seeded queue");
        verify(q.seed(d,states,dual,dt),"reseed failed");
        q.consume(!d.plan.at(0)); verify(q.remaining()==0,"wrong actual command keeps queue alive");
        ++cases;
    }
    result("queue_matrix",cases,steps);
}

static void traceMatrix() {
    std::mt19937 rng(0x14ce);
    int cases=0, segments=0;
    for (int iteration=0;iteration<2000;++iteration) {
        context="trace case="+std::to_string(iteration);
        int n=iteration<3 ? iteration : 2+rng()%1023;
        std::vector<Point> p; std::vector<int> breaks;
        float y=0;
        for (int t=0;t<n;++t) {
            if (t&&rng()%83==0) { breaks.push_back(t); y+=400; }
            y+=float(int(rng()%11)-5)*.05f;
            p.push_back({float(t)*.4f,y+std::sin(t*.09f)*5});
        }
        size_t first=n>8 ? rng()%(n/3) : 0, last=n>8 ? n-rng()%(n/3) : n;
        auto out=traceSegments(p,breaks,.18f,first,last);
        for (auto s:out) {
            ++segments;
            int a=std::lround(s.a.x/.4f), b=std::lround(s.b.x/.4f);
            verify(a>=int(first)&&b<int(last)&&a<b,"segment endpoints leave selected native range");
            for (int k:breaks) verify(!(a<k&&k<=b),"trace connects across teleport");
            for (int t=a;t<=b;++t) verify(distance(p[t],s.a,s.b)<=.1801,"trace exceeds simplification error");
        }
        // Every original edge except explicit breaks is covered exactly once.
        for (size_t t=first+1;t<last;++t) {
            if (std::find(breaks.begin(),breaks.end(),int(t))!=breaks.end()) continue;
            int count=0;
            for (auto s:out) count+=s.a.x<=p[t-1].x&&s.b.x>=p[t].x;
            verify(count==1,"native trace edge disappears or is duplicated");
        }
        ++cases;
    }
    result("trace_matrix",cases,segments);
}

struct CacheState { double x=0,v=0; uint64_t interactions=0; };
static void cacheMatrix() {
    std::mt19937 rng(0xcac4e);
    int cases=0;
    auto integrate=[](CacheState &s,bool down) {
        s.v+=down?.12:-.16; s.x+=s.v*.25;
        if (down) s.interactions=s.interactions*31+17;
    };
    for (int iteration=0;iteration<5000;++iteration) {
        context="cache case="+std::to_string(iteration);
        PrefixCache<CacheState,4> cache;
        Plan base; base.length=32+rng()%737; base.landingDelay=int(rng()%5)-1;
        for (int t=0;t<base.length;++t) base.buttons[t]=rng()%2;
        int at=1+rng()%base.length;
        CacheState original;
        for (int t=0;t<at;++t) integrate(original,base.at(t));
        cache.save(base,at,original);
        Plan child=base;
        for (int t=at;t<child.length;++t) child.buttons[t]=rng()%2;
        int resumedAt=0; auto found=cache.find(child,resumedAt);
        verify(found&&resumedAt==at,"identical requested prefix not found");
        auto resumed=*found; CacheState cold;
        for (int t=at;t<child.length;++t) integrate(resumed,child.at(t));
        for (int t=0;t<child.length;++t) integrate(cold,child.at(t));
        verify(resumed.x==cold.x&&resumed.v==cold.v&&resumed.interactions==cold.interactions,
               "cached and cold independent integration diverge");
        child=base; child.buttons[rng()%at]^=1;
        verify(!cache.find(child,resumedAt),"different past input reuses state");
        child=base; ++child.landingDelay;
        verify(!cache.find(child,resumedAt),"different touchdown policy reuses state");
        child=base; child.length=at-1;
        verify(!cache.find(child,resumedAt),"resume exceeds active interval");
        cache.reset(); verify(!cache.find(base,resumedAt)&&cache.size()==0,"old live world cached after reset");
        ++cases;
    }
    result("cache_matrix",cases);
}

static void deadlineMatrix() {
    int cases=0;
    for (int tps : {60,120,240,360}) for (bool held : {false,true})
    for (bool force : {false,true}) for (int limit : {-100,4,16,96,1000}) {
        context="deadline case="+std::to_string(cases);
        RealtimeSearch search; ObstacleOracle oracle; oracle.dt=1./tps;
        Config c; c.tickSeconds=oracle.dt; c.priorityControl=c.cubePulsePlanning=true;
        c.plannerMode=PlannerMode::Cube; c.candidates=limit; c.forceRelease=force;
        c.sharedDeadline=Clock::now();
        auto d=search.decide(c,held,oracle);
        verify(d.exhausted&&oracle.calls==0&&!d.hasValidatedInput&&!d.recoveryInput,
               "expired shared deadline runs physics or authorizes input");
        c.sharedDeadline=Clock::time_point::max(); c.budgetMs=.2; c.horizon=768;
        auto limited=search.decide(c,held,oracle);
        verify(limited.candidates<=std::clamp(limit,4,96),"candidate cap violated");
        if (force) verify(!limited.down,"mandatory release overridden");
        if (limited.hasValidatedInput) {
            JumpBody b;
            for(int t=0;t<limited.plan.length;++t) verify(oracle.step(b,limited.plan.at(t)),"tiny-budget checked path dies");
        }
        search.reset(); oracle.corrupt=true; c.budgetMs=8;
        auto corrupt=search.decide(c,held,oracle);
        verify(!corrupt.hasValidatedInput&&!corrupt.recoveryInput,"cold consumed-interaction mismatch accepted");
        ++cases;
    }
    result("deadline_and_corruption_matrix",cases);
}

int main() {
    try {
        obstacleMatrix(); closedLoopMatrix(); queueMatrix(); traceMatrix(); cacheMatrix(); deadlineMatrix();
    } catch (std::exception const &e) {
        std::cerr<<"FAIL: "<<e.what()<<'\n'; return 1;
    }
    std::cout<<"PASS: stress matrices use independent fixtures; no native GD gameplay is claimed\n";
}
