#include "online_planner.hpp"
#include "verified_input_queue.hpp"
#include "input_motion.hpp"
#include "flight_certificate.hpp"
#include "landing_schedule.hpp"
#include "hazard_guard.hpp"
#include <iostream>
#include <stdexcept>
using namespace agb;
static void check(bool b,char const *s){if(!b)throw std::runtime_error(s);}
static Config config(PlannerMode m=PlannerMode::Cube){Config c;c.plannerMode=m;c.cubePulsePlanning=m==PlannerMode::Cube;
    c.horizon=180;c.candidates=96;c.budgetMs=8;return c;}
static Evaluation trace(Plan const&p,int survived,bool dead=false){
    Evaluation e;e.survived=survived;e.died=e.nativeDeath=dead;e.hasRealized=true;e.realized=p;e.realized.length=survived;
    e.clearance=e.nearClearance=30;
    for(int t=0;t<=survived;++t)e.traces[0].push_back({float(t),15});return e;
}
int main(){
    int failures=0;auto run=[&](char const *name,auto test){try{test();std::cout<<"PASS: "<<name<<'\n';}
        catch(std::exception const&e){++failures;std::cerr<<"FAIL: "<<name<<": "<<e.what()<<'\n';}};
    run("landing does not terminate the planned window",[]{
        check(!routeWindowFinished(96,180,true,true),"first landing before a following pit is not the endpoint");
        check(!routeWindowFinished(180,180,false,true),"unsettled flight cannot finish at horizon");
        check(!routeWindowFinished(180,180,true,false),"unverified portal cannot finish at horizon");
        check(routeWindowFinished(220,180,true,true),"a checked window plus safe landing can finish");
    });
    run("empty floor stays grounded",[]{
        auto c=config();OnlinePlanner solver;
        auto d=solver.decide(c,false,[](Plan const&p,auto){auto e=trace(p,p.length);
            bool jumps=false;for(int t=0;t<p.length;++t)jumps|=p.at(t);
            if(jumps){e.cubeTakeoffs=1;e.requiresLanding=e.landingVerified=true;}
            else e.groundedPrefixTicks=p.length;return e;});
        check(d.hasValidatedInput&&!d.down&&d.selected.cubeTakeoffs==0,"unneeded takeoff must not outrank equally safe coast");
    });
    run("gap branches before the native support loss",[]{
        auto c=config();OnlinePlanner solver;
        auto d=solver.decide(c,false,[](Plan const&p,auto){
            bool bridge=false;for(int t=42;t<=76;++t)bridge|=p.at(t)&&!p.at(t-1);
            auto e=trace(p,bridge?p.length:115,!bridge);
            if(bridge){e.requiresLanding=e.landingVerified=e.decisionPoint=true;e.cubeTakeoffs=1;}
            else{e.supportLossTick=90;e.groundedPrefixTicks=90;e.requiresLanding=true;e.firstActionVerified=false;
                for(int t=0;t<90;++t){e.groundedActions.push_back(t);e.controlActions.push_back({t,PlannerMode::Cube});}}
            return e;
        });
        check(d.hasValidatedInput&&d.completeContinuation,"pit must produce a full landing-proved crossing");
        bool bridge=false;for(int t=42;t<=76;++t)bridge|=d.plan.at(t);check(bridge,"takeoff precedes edge on the selected lineage");
        check(d.firstActionCandidates[0]&&d.firstActionCandidates[1],"pit repairs must not starve either immediate action");
    });
    run("independent cube integration crosses a red kill zone below a pit",[]{
        auto c=config();c.horizon=240;OnlinePlanner solver;
        auto d=solver.decide(c,false,[](Plan const&p,auto){
            Evaluation e;e.hasRealized=true;e.traces[0].push_back({0,15});
            CubePulse pulse;LandingSchedule schedule(p.landingDelay,true);FlightCertificate flight(true);
            HazardGuard guard;guard.set({{160,-200,245,-35}});
            float x=0,y=15,vy=0;bool ground=true;
            for(int t=0;t<MaxHorizon;++t){
                if(ground&&!pulse.mustRelease()){e.groundedActions.push_back(t);e.controlActions.push_back({t,PlannerMode::Cube});}
                bool before=ground;float oldX=x,oldY=y;
                bool down=pulse.command(schedule.request(t<p.length&&p.at(t),ground,pulse.mustRelease(),t<p.length),true,ground);
                schedule.sent(down,ground);e.realized.buttons[t]=down;
                if(down&&ground){ground=false;vy=11.18f;++e.cubeTakeoffs;}
                x+=1.3f;bool support=x<160||x>251;
                if(ground&&!support){ground=false;vy=0;if(e.supportLossTick<0)e.supportLossTick=t;}
                if(!ground){vy=std::max(-15.f,vy-.216f);y+=.225f*vy;
                    if(support&&y<=15&&vy<=0){y=15;vy=0;ground=true;}}
                if(guard.hit({oldX-6,oldY-6,oldX+6,oldY+6},{x-6,y-6,x+6,y+6})){
                    e.died=e.guardDeath=true;break;
                }
                flight.observe(before,ground,down);++e.survived;e.realized.length=e.survived;
                e.traces[0].push_back({x,y});
                if(before&&ground&&!down&&e.groundedPrefixTicks==t)++e.groundedPrefixTicks;
                if(routeWindowFinished(e.survived,p.length,flight.verified(),true))break;
            }
            e.requiresLanding=flight.required;e.firstActionVerified=flight.verified();
            e.landingVerified=flight.required&&flight.verified();e.decisionPoint=e.landingVerified;
            return e;
        });
        check(d.hasValidatedInput&&d.completeContinuation&&d.selected.landingVerified,
              "red hitbox below a real support gap must reject walking and require a complete ballistic crossing");
        check(d.selected.traces[0].back().x>251,"selected lineage reaches the supported far bank");
    });
    run("independent wave corridor needs alternating controls",[]{
        auto c=config(PlannerMode::Wave);c.horizon=120;OnlinePlanner solver;
        auto d=solver.decide(c,false,[](Plan const&p,auto){
            Evaluation e;e.hasRealized=true;e.traces[0].push_back({0,30});float y=30;
            for(int t=0;t<p.length;++t){e.controlActions.push_back({t,PlannerMode::Wave});y+=p.at(t)?1.5f:-1.5f;
                if(y<=0||y>=60){e.died=e.nativeDeath=true;break;}
                ++e.survived;e.realized.buttons[t]=p.at(t);e.realized.length=e.survived;e.traces[0].push_back({float(t+1),y});}
            e.nearClearance=e.clearance=10;return e;
        });
        check(d.hasValidatedInput&&d.completeContinuation,"mode steering should reach the full corridor horizon");
        int edges=0;for(int t=1;t<d.plan.length;++t)edges+=d.plan.at(t)!=d.plan.at(t-1);
        check(edges>=3,"a constant hold/release cannot satisfy wave clearance");
    });
    run("a falling horizon endpoint cannot become a safe cube route",[]{
        auto c=config();Plan p;p.length=180;auto e=trace(p,180);e.requiresLanding=true;e.firstActionVerified=false;
        e.supportLossTick=12;e.groundedPrefixTicks=12;
        check(!proveRoute(c,p,e),"survived tick count alone is not a pit/landing certificate");
        e.requiresLanding=false;e.firstActionVerified=true;e.boundary=true;e.groundedPrefixTicks=0;
        check(!proveRoute(c,p,e),"unsupported boundary cannot certify a grounded cube takeoff");
    });
    run("ground wait excludes a known fatal tail and keeps takeoff reserve",[]{
        auto c=config();c.forceRelease=true;c.candidates=4;OnlinePlanner solver;
        auto d=solver.decide(c,false,[](Plan const&p,auto){auto e=trace(p,140,true);e.requiresLanding=true;
            e.firstActionVerified=false;e.supportLossTick=e.groundedPrefixTicks=100;return e;});
        check(d.hasValidatedInput&&!d.down&&!d.completeContinuation&&d.forecastDied,"observed death remains separate from checked waiting");
        check(d.certificateTicks<=100-reactionTicks(c.tickSeconds),"queued UP leaves ground for the next takeoff search");
        check(d.plan.length==d.selected.survived&&d.selected.traces[0].size()==size_t(d.plan.length+1),"display and button queue contain only the checked prefix");
        VerifiedInputQueue q;check(q.seed(d,{},false,c.tickSeconds),"checked wait can seed a bounded queue");
        check(q.continuation().forecastDied,"queue must preserve the observed fatal uncommitted tail");
        check(!q.matches({Point{.4f,15},{}},{},false,c.tickSeconds),"subpixel drift beyond the configured bound invalidates the queue");
    });
    run("all native control modes share the proof pipeline",[]{
        for(auto m:{PlannerMode::Cube,PlannerMode::Ship,PlannerMode::Ball,PlannerMode::Ufo,PlannerMode::Wave,
                    PlannerMode::Robot,PlannerMode::Spider,PlannerMode::Swing,PlannerMode::Dash}){
            auto c=config(m);OnlinePlanner solver;auto d=solver.decide(c,false,[&](Plan const&p,auto){
                auto e=trace(p,p.length);if(modePolicy(m).mustLand){e.requiresLanding=e.landingVerified=true;}return e;});
            check(d.hasValidatedInput&&d.completeContinuation,"one mode cannot bypass the shared proof contract");
        }
    });
    run("portal and following orb stay on one lineage",[]{
        auto c=config();c.horizon=72;OnlinePlanner solver;
        auto d=solver.decide(c,false,[](Plan const&p,auto){
            if(!p.at(0))return trace(p,16,true);
            auto e=trace(p,90,true);e.transitions={{60,PlannerMode::Ship}};e.firstActionVerified=false;
            if(p.at(60)&&p.at(61)){
                e=trace(p,135,true);e.transitions={{60,PlannerMode::Ship}};e.firstActionVerified=false;
                e.orbActions={120};e.controlActions={{120,PlannerMode::Ship}};
                if(p.at(120)&&!p.at(119)){
                    e=trace(p,std::max(p.length,150));e.transitions={{60,PlannerMode::Ship}};e.portalVerified=true;
                    e.firstActionVerified=true;
                }
            }
            return e;
        });
        check(d.hasValidatedInput&&d.down&&d.plan.at(60)&&d.plan.at(120)&&!d.plan.at(119),
              "late native orb repairs must preserve the route that entered the new mode");
    });
    run("cached route requires an independent replay",[]{
        auto c=config();c.horizon=72;OnlinePlanner solver;int confirmations=0;
        auto d=solver.decide(c,false,[&](Plan const&p,auto,bool memo=true){
            auto e=trace(p,p.length);e.groundedPrefixTicks=p.length;
            if(p.at(0)){e.cacheResumeTicks=memo?16:0;e.clearance=e.nearClearance=120;
                e.requiresLanding=e.landingVerified=true;e.cubeTakeoffs=1;
                if(!memo){++confirmations;e.traces[0][20].y+=2;}}
            return e;
        });
        check(confirmations==1&&d.cacheMismatches==1,"divergent resumed state must fail the independent oracle");
        check(d.hasValidatedInput&&!d.down,"retain an earlier cold-proved action instead of the invalid cached jump");
    });
    run("candidate cap and expired deadline",[]{
        auto c=config();c.candidates=8;OnlinePlanner solver;int calls=0;
        auto d=solver.decide(c,false,[&](Plan const&p,auto){++calls;auto e=trace(p,50,true);
            e.firstActionVerified=false;e.controlActions={{20,PlannerMode::Cube}};return e;});
        check(calls<=8&&d.candidates==calls,"frontier storage does not increase native-evaluation cap");
        c.sharedDeadline=Clock::now();auto expired=solver.decide(c,false,[&](auto const&,auto){++calls;return Evaluation{};});
        check(expired.exhausted&&expired.candidates==0,"absolute deadline is checked before work begins");
    });
    return failures?1:0;
}
