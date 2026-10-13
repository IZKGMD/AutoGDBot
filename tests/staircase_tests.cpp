#if defined(__SANITIZE_ADDRESS__)
#include <chrono>
// ASan verifies memory and route semantics with a deterministic clock.
// Deadline performance is tested separately by the Release build; instrumented
// snapshot copies must not be reported as realtime engine measurements.
struct StaircaseFunctionalClock {
    using time_point=std::chrono::steady_clock::time_point;
    using duration=std::chrono::steady_clock::duration;
    static time_point now(){return time_point{};}
};
#define AUTOGDBOT_TEST_CLOCK StaircaseFunctionalClock
#endif
#include "controller.hpp"
#include "online_planner.hpp"
#include "verified_input_queue.hpp"
#include "prefix_cache.hpp"
#include "input_motion.hpp"
#include "flight_certificate.hpp"
#include "landing_schedule.hpp"
#include "planning_hints.hpp"
#include <iostream>
#include <stdexcept>

using namespace agb;
void check(bool ok, char const *why) { if (!ok) throw std::runtime_error(why); }

// Independent fixed-tick fixture of rising narrow platforms over floor spikes.
// It exercises the search/control loop, not the Geometry Dash native engine.
struct Body { float x = 50, y = 15, vy = 0; bool ground = true; };
struct FixtureFrame{Body body;CubePulse pulse;LandingSchedule schedule{-1,true};
    FlightCertificate certificate;Evaluation prefix;};
struct Scene {
    std::vector<Bounds> solids{{170,0,200,30},{295,0,325,60},{420,0,450,90},
                               {545,0,575,120},{670,0,700,150},{795,0,900,180}};
    std::vector<Bounds> hazards{{90,0,110,27},{120,0,140,27},{201,0,294,18},
                                {326,0,419,18},{451,0,544,18},{576,0,669,18},
                                {701,0,794,18}};
    HazardGuard guard;
    Scene() { guard.set(hazards); }
    bool step(Body &b, bool press, float speed = 1.3f) const {
        Body old = b;
        if (press && b.ground) { b.vy = 11.18f; b.ground = false; }
        if (!b.ground) {
            b.vy = std::max(-15.f, std::round((b.vy - .216f)*1000.f)/1000.f);
            b.y += .225f*b.vy;
        }
        b.x += speed;
        b.ground = false;
        float support = 0;
        for (auto s : solids) {
            if (b.x+15 < s.minX || b.x-15 > s.maxX) continue;
            if (b.vy <= 0 && old.y-15 >= s.maxY-.001f && b.y-15 <= s.maxY+.001f)
                support = std::max(support, s.maxY);
        }
        if (b.vy <= 0 && b.y-15 <= support+.001f) {
            b.y = support+15; b.vy = 0; b.ground = true;
        }
        for (auto s : solids) {
            if (b.x+4.5f > s.minX && b.x-4.5f < s.maxX &&
                b.y+4.5f > s.minY && b.y-4.5f < s.maxY) return false;
        }
        return !guard.hit({old.x-5.25f,old.y-5.25f,old.x+5.25f,old.y+5.25f},
                         {b.x-5.25f,b.y-5.25f,b.x+5.25f,b.y+5.25f});
    }
    Evaluation evaluate(Body b, CubePulse pulse, Plan const &plan, Clock::time_point deadline,
                        PrefixCache<FixtureFrame> *cache=nullptr) const {
        Evaluation e; e.hasRealized = true;
        e.traces[0].push_back({b.x,b.y});
        LandingSchedule schedule(plan.landingDelay, b.ground);
        FlightCertificate certificate(b.ground);
        int resume=0;
        if(cache)if(auto frame=cache->find(plan,resume)){
            b=frame->body;pulse=frame->pulse;schedule=frame->schedule;certificate=frame->certificate;e=frame->prefix;
        }
        e.cacheResumeTicks=resume;
        for (int t=resume; t<MaxHorizon; ++t) {
            if (Clock::now() >= deadline) { e.interrupted=true; break; }
            bool grounded = b.ground;
            if (grounded && !pulse.mustRelease()) e.groundedActions.push_back(t);
            bool request = schedule.request(t<plan.length && plan.at(t),grounded,
                                            pulse.mustRelease(),t<plan.length);
            bool down = pulse.command(request,true,grounded);
            schedule.sent(down,grounded); e.realized.buttons[t]=down;
            if (!step(b,down)) { e.died=true; break; }
            certificate.observe(grounded,b.ground,down);
            if (!grounded && b.ground) {
                if (e.firstLandingTick<0) e.firstLandingTick=t;
                ++e.landingCount;
            }
            ++e.survived; e.realized.length=e.survived;
            if(grounded&&b.ground&&!down&&e.groundedPrefixTicks==t)++e.groundedPrefixTicks;
            e.traces[0].push_back({b.x,b.y});
            if(cache&&(t+1)%16==0&&t+1<plan.length)
                cache->save(plan,t+1,{b,pulse,schedule,certificate,e});
            if (e.survived>=plan.length && certificate.verified()) break;
        }
        e.requiresLanding=certificate.required;
        e.landingVerified=certificate.required && certificate.verified();
        e.firstActionVerified=certificate.verified();
        e.decisionPoint=certificate.verified()&&certificate.required;
        return e;
    }
};

int main() {
    LandingSchedule immediate(0,true);
    check(!immediate.request(false,true,false,true), "policy must wait for the scheduled first jump");
    immediate.sent(true,true);
    check(!immediate.request(false,false,false,true), "airborne cube cannot re-jump");
    check(immediate.request(false,true,false,true), "next jump is relative to actual touchdown");
    check(!immediate.request(false,true,false,false), "tail must release instead of starting another jump");
    Scene scene; Body body; CubePulse pulse;
    Plan fixed; fixed.length=216; fixed.buttons[0]=true;
    auto broken=scene.evaluate(body,pulse,fixed,Clock::time_point::max());
    check(broken.died,"one fixed pulse must fail beyond the first stair");
    Plan policy=fixed; policy.landingDelay=6;
    auto route=scene.evaluate(body,pulse,policy,Clock::time_point::max());
    check(!route.died && route.firstActionVerified && route.landingCount>=3,
          "native-touchdown policy must complete several stairs");
    check(route.survived>policy.length,"forecast must finish the airborne tail beyond its horizon");
    for (float start : {40.f,45.f,50.f,55.f,60.f}) {
        Controller controller; Body live; live.x=start; CubePulse livePulse;
        int presses=0;
        for (int tick=0; live.x<850 && tick<700; ++tick) {
            Config cfg; cfg.horizon=216; cfg.candidates=96; cfg.budgetMs=3.5;
#if defined(__SANITIZE_ADDRESS__)
            // Instrumentation checks memory/logic, not the Release deadline.
            // Keep the 3.5ms regression in the uninstrumented run.
            cfg.budgetMs=8;
#endif
            cfg.cubePulsePlanning=true; cfg.forceRelease=livePulse.mustRelease() || !live.ground;
            if (live.ground) seedCubeEvents(cfg,live.x,live.y-15,1.3f,scene.solids,scene.hazards);
            auto decision=controller.decide(cfg,false,[&](Plan const &p, auto end) {
                return scene.evaluate(live,livePulse,p,end);
            });
            // During uncontrollable flight release is deterministic; grounded
            // states require a fully checked route.
            check(!live.ground || decision.hasValidatedInput,"every grounded stair state needs a viable route");
            bool down=livePulse.command(decision.down,true,live.ground); presses+=down;
            check(scene.step(live,down),"closed-loop controller died in the staircase fixture");
            controller.advance();
        }
        check(live.x>=850 && presses>=6,"closed-loop controller must reach the final stair");
    }
    for(float start:{40.f,45.f,50.f,55.f,60.f}){
        OnlinePlanner solver;VerifiedInputQueue queue;Body live;live.x=start;CubePulse pulse;int launches=0;
        PrefixCache<FixtureFrame> proposals,oracle;
        std::array<ModeState,2> modes{};modes[0].mode=PlannerMode::Cube;
        for(int tick=0;live.x<850&&tick<700;++tick){
            if(tick){auto anchor=[&](FixtureFrame &frame){
                auto &e=frame.prefix;
                if(e.traces[0].size()<2||std::hypot(e.traces[0][1].x-live.x,e.traces[0][1].y-live.y)>.001f)return false;
                advanceEvaluation(e);return true;
            };proposals.reanchor(anchor);oracle.reanchor(anchor);}
            Config c;c.plannerMode=PlannerMode::Cube;c.cubePulsePlanning=true;
            c.horizon=480;c.candidates=96;c.budgetMs=8;c.forceRelease=pulse.mustRelease()||!live.ground;
            if(live.ground)seedCubeEvents(c,live.x,live.y-15,1.3f,scene.solids,scene.hazards);
            bool ready=queue.matches({Point{live.x,live.y},{}},modes,false,c.tickSeconds);
            if(!ready)queue.reset();
            Decision d;
            if(ready&&(queue.transitionDue(false)||(queue.remaining()>reactionTicks(c.tickSeconds,.75)&&(tick&1))))d=queue.continuation();
            else{
                d=solver.decide(c,false,[&](Plan const&p,auto end,bool memo=true){
                    return scene.evaluate(live,pulse,p,end,memo?&proposals:&oracle);
                });
                if(ready&&(!d.hasValidatedInput||d.certificateTicks<queue.remaining()))d=queue.continuation();
                else if(d.hasValidatedInput)queue.seed(d,modes,false,c.tickSeconds);
            }
            check(!live.ground||d.hasValidatedInput,"persistent planner needs a checked staircase route");
            bool down=pulse.command(d.down,true,live.ground);launches+=down;
            check(scene.step(live,down),"persistent planner died in stair fixture");queue.consume(down);solver.commit(down);
        }
        check(live.x>=850&&launches>=6,"persistent planner completes the stair fixture");
    }
    std::cout << "PASS: repeated touchdown pulses, completed tails, five legacy and five persistent staircase runs\n";
}

