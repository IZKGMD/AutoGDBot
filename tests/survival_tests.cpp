#include "controller.hpp"
#include "hazard_guard.hpp"
#include "planning_hints.hpp"
#include "verified_input_queue.hpp"
#include <iostream>
#include <stdexcept>
#include <random>
using namespace agb;
static void check(bool b, char const *why) { if (!b) throw std::runtime_error(why); }
static Evaluation unfinished(Plan const &p, int survived) {
    Evaluation e;
    e.requiresLanding = true; e.firstActionVerified = false;
    e.died = e.nativeDeath = true; e.hasRealized = true;
    e.survived = survived; e.realized = p; e.realized.length = survived;
    for (int t = 0; t <= survived; ++t) e.traces[0].push_back({t * 1.3f, float(p.at(0) ? 50 : 15)});
    return e;
}
int main() {
    Plan later; later.length = 384; later.buttons[0] = later.buttons[90] = later.buttons[270] = true;
    check(lastRequestedTick(later) == 270, "identify the final planned orb, including beyond old 240-tick horizon");
    check(!flightRouteFinished(true, 20, lastRequestedTick(later)), "first touchdown cannot hide two future orbs");
    check(!flightRouteFinished(true, 150, lastRequestedTick(later)), "second touchdown cannot hide the final orb");
    check(flightRouteFinished(true, 320, lastRequestedTick(later)), "completed chain may end at its final safe landing");
    later.landingDelay = 0;
    check(lastRequestedTick(later) == 383 && !flightRouteFinished(true, 320, lastRequestedTick(later)),
          "touchdown policy must finish its active interval");

    Config cfg; cfg.horizon = 384; cfg.budgetMs = 8; cfg.candidates = 40;
    cfg.plannerMode = PlannerMode::Cube; cfg.cubePulsePlanning = true;
    cfg.orbWindows[0] = {86,94}; cfg.orbWindows[1] = {266,274}; cfg.orbWindowCount = 2;
    Controller chainPlanner;
    auto chain = chainPlanner.decide(cfg, false, [&](Plan const &p, auto deadline) {
        Evaluation e; e.hasRealized = true; e.requiresLanding = true;
        e.realized = p; const int last = lastRequestedTick(p);
        e.traces[0].push_back({0,15});
        for (int t = 0; t < p.length; ++t) {
            if (Clock::now() >= deadline) { e.interrupted = true; break; }
            if ((t == 0 || t == 90 || t == 270) && !p.at(t)) { e.died = true; break; }
            ++e.survived; e.traces[0].push_back({float(t+1),15});
            bool landing = t == 20 || t == 150 || t == 320;
            if (flightRouteFinished(landing, t, last)) {
                e.decisionPoint = e.landingVerified = true; break;
            }
        }
        e.realized.length = e.survived;
        e.firstActionVerified = e.landingVerified;
        return e;
    });
    check(chain.hasValidatedInput && chain.down && chain.plan.at(90) && chain.plan.at(270) &&
          chain.predictedTicks >= 321, "candidate search verifies a complete three-action chain rather than its first landing");
    Config windows = cfg; windows.orbWindowCount = windows.orbEventCount = 0;
    std::vector<Bounds> rings{{100,40,120,60},{280,50,300,70}};
    seedOrbContactEvents(windows,{0,0,30,30},1,rings);
    check(windows.orbWindowCount == 2 && windows.orbWindows[1].begin > windows.orbWindows[0].end,
          "contact windows retain ring identity instead of exhausting the flat event array");
    Config reverse = cfg; reverse.orbWindowCount = reverse.orbEventCount = 0;
    seedOrbContactEvents(reverse,{380,0,410,30},-1,rings);
    check(reverse.orbWindowCount == 2, "reverse travel retains both contact windows");

    cfg = {}; cfg.horizon = 180; cfg.candidates = 8; cfg.budgetMs = 8;
    cfg.cubePulsePlanning = true; cfg.plannerMode = PlannerMode::Cube;
    cfg.allowFlightRecovery = true;
    auto evaluate = [&](Plan const &p, auto) { return unfinished(p, p.at(0) ? 249 : 126); };
    Controller planner; auto recovered = planner.decide(cfg, false, evaluate);
    check(recovered.recoveryInput && !recovered.down && !recovered.hasValidatedInput &&
          recovered.certificateTicks == 0 && !recovered.selected.firstActionVerified,
          "fatal cube launch is rejected even when it lives longer than release");
    check(recovered.recoveryTicks <= recovered.selected.survived &&
          recovered.plan.length == recovered.recoveryTicks, "recovery exposes only a bounded checked prefix");
    VerifiedInputQueue queue; std::array<ModeState,2> modes{};
    check(!queue.seed(recovered,modes,false,1./240), "unproved recovery cannot seed the executable verified queue");
    cfg.allowFlightRecovery = false; planner.reset();
    auto disabled = planner.decide(cfg,false,evaluate);
    check(!disabled.recoveryInput && !disabled.hasValidatedInput && !disabled.down, "recovery setting can restore strict rejection");
    cfg.allowFlightRecovery = true; planner.reset();
    cfg.plannerMode = PlannerMode::Robot; cfg.cubePulsePlanning = false;
    auto robotRecovery = planner.decide(cfg,false,evaluate);
    check(robotRecovery.recoveryInput && robotRecovery.down,
          "robot flight search also retains a checked survival prefix");
    cfg.plannerMode = PlannerMode::Cube; cfg.cubePulsePlanning = true;
    cfg.horizon = 24; planner.reset();
    auto shortHorizon = planner.decide(cfg,false,[](Plan const &p,auto) {return unfinished(p,24);});
    check(!shortHorizon.recoveryInput, "short configured horizon cannot weaken the recovery time minimum");
    cfg.horizon = 180; planner.reset();
    auto tooShort = planner.decide(cfg,false,[](Plan const &p,auto) {return unfinished(p,12);});
    check(!tooShort.recoveryInput, "imminent fatal trajectories cannot authorize recovery");
    planner.reset(); int calls = 0;
    auto unopposed = planner.decide(cfg,false,[&](Plan const &p,auto deadline) {
        if (++calls == 1) { while (Clock::now() < deadline) {} return unfinished(p,126); }
        return unfinished(p,249);
    });
    check(!unopposed.recoveryInput, "one tested polarity cannot authorize recovery");
    cfg.dual = true; planner.reset();
    auto noP2 = planner.decide(cfg,false,evaluate);
    check(!noP2.recoveryInput, "mixed/shared dual needs both checked traces");
    cfg.dual = false; planner.reset();
    auto verified = planner.decide(cfg,false,[&](Plan const &p,auto) {
        if (p.at(0)) return unfinished(p,249);
        Evaluation e; e.survived = cfg.horizon; return e;
    });
    check(verified.hasValidatedInput && !verified.recoveryInput && !verified.down,
          "a fully checked route always outranks recovery");

    Decision d; d.hasValidatedInput = true; d.certificateTicks = d.plan.length = 4;
    d.selected.survived = 4;
    for (int t=0;t<=4;++t) {
        d.selected.traces[0].push_back({t*1.3f,50});
        d.selected.motions[0].push_back({2.0 - t*.25,false,false,false,true});
    }
    check(queue.seed(d,modes,false,1./240), "motion-backed verified queue seed");
    std::array<Point,2> pos{Point{0,50},{}};
    std::array<MotionSample,2> motion{d.selected.motions[0][0],{}};
    check(queue.matches(pos,modes,false,1./240,&motion), "queue checks native velocity and control state");
    check(!queue.matches(pos,modes,false,1./240), "motion proof cannot be bypassed by a position-only check");
    motion[0].velocity += .2;
    check(!queue.matches(pos,modes,false,1./240,&motion), "pad/force changes at the same position invalidate queued clicks");
    motion[0] = d.selected.motions[0][0]; motion[0].ringUnused = false;
    check(!queue.matches(pos,modes,false,1./240,&motion), "ring input eligibility changes invalidate old click timing");
    queue.consume(false); auto suffix = queue.continuation();
    check(suffix.selected.motions[0].size() == 4 && suffix.selected.motions[0][0].velocity == 1.75,
          "motion proof advances with the button/position suffix");

    std::mt19937 rng(17); std::uniform_real_distribution<float> x(-300,300), y(-80,80);
    std::vector<Bounds> hazards;
    for (int i=0;i<256;++i) {float a=x(rng),b=y(rng);hazards.push_back({a,b,a+12,b+15});}
    HazardGuard guard; guard.set(hazards);
    for (int n=0;n<400;++n) {
        float a=x(rng),b=y(rng); Bounds p{a,b,a+10,b+10}; float expected=120;
        for (auto h:hazards) {
            if(h.maxX < p.minX-100 || h.minX > p.maxX+100) continue;
            float dx=std::max({h.minX-p.maxX,p.minX-h.maxX,0.f});
            float dy=std::max({h.minY-p.maxY,p.minY-h.maxY,0.f});
            expected=std::min(expected,std::sqrt(dx*dx+dy*dy));
        }
        check(std::abs(guard.clearance(p)-expected)<.001, "cached geometry ranking equals exhaustive distance calculation");
    }
    std::cout << "PASS: full orb chains, bounded labelled recovery, dual/input proof, native-motion queue and 400 cached geometry comparisons\n";
}
