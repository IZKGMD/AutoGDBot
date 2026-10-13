#include "controller.hpp"
#include "verified_input_queue.hpp"
#include <iostream>
#include <stdexcept>
using namespace agb;
static void check(bool v, char const *s) { if (!v) throw std::runtime_error(s); }
static Evaluation flight(Plan const &p, bool fatal) {
    Evaluation e;
    e.survived = p.at(0) ? 249 : 126;
    e.requiresLanding = true; e.firstActionVerified = false;
    e.died = e.nativeDeath = fatal; e.interrupted = !fatal;
    e.hasRealized = true; e.realized = p; e.realized.length = e.survived;
    for (int t=0;t<=e.survived;++t) e.traces[0].push_back({float(t),50});
    return e;
}
int main() {
    Config c; c.horizon=180; c.candidates=16; c.budgetMs=8;
    c.cubePulsePlanning=true; c.plannerMode=PlannerMode::Cube;
    c.compareFirstActions=true; c.allowFlightRecovery=true;
    for (bool fatal : {false,true}) {
        Controller bot;
        auto d=bot.decide(c,false,[&](Plan const &p,auto){return flight(p,fatal);});
        check(!d.down && !d.hasValidatedInput,"unproved or known-fatal takeoff cannot win on survival time");
    }
    for (bool cubeFirst : {false,true}) {
        Config dual=c;dual.dual=true;dual.cubePulsePlanning=false;
        dual.plannerMode=cubeFirst?PlannerMode::Cube:PlannerMode::Ship;
        dual.secondaryMode=cubeFirst?PlannerMode::Ship:PlannerMode::Cube;
        Controller mixed;
        auto d=mixed.decide(dual,false,[&](Plan const&p,auto){
            auto e=flight(p,true);e.traces[1]=e.traces[0];return e;
        });
        check(!d.down && !d.hasValidatedInput,"either cube in mixed dual requires a safe landing");
    }
    Controller bot;
    auto wait=bot.decide(c,false,[&](Plan const &p,auto){
        if(p.at(0)) return flight(p,true);
        Evaluation e; e.survived=e.groundedPrefixTicks=60; e.interrupted=true;
        e.hasRealized=true; e.realized=p; e.realized.length=60;
        return e;
    });
    check(wait.hasValidatedInput && !wait.down && !wait.recoveryInput && wait.certificateTicks<=60,
          "collision-free grounded prefix remains a certified wait after deadline");
    for(int invalid=0;invalid<4;++invalid) {
        bot.reset();
        auto d=bot.decide(c,false,[&](Plan const &p,auto){
            if(p.at(0)) return flight(p,true);
            Evaluation e; e.survived=e.groundedPrefixTicks=60; e.interrupted=true;
            if(invalid==0) e.groundedPrefixTicks=0;
            if(invalid==1) e.requiresLanding=true;
            if(invalid==2) e.died=true;
            if(invalid==3) e.groundedPrefixTicks=61;
            return e;
        });
        check(!d.hasValidatedInput,"missing ground, airborne, fatal and inconsistent prefixes are rejected");
    }
    c.jumpEvents[0]=10; c.jumpEventCount=1; bot.reset();
    auto delayed=bot.decide(c,false,[&](Plan const &p,auto){
        if(p.at(0)) return flight(p,true);
        Evaluation e; e.survived=e.groundedPrefixTicks=40; e.interrupted=true;
        if(p.at(10)) {e.survived=130;e.groundedPrefixTicks=10;e.interrupted=false;
            e.requiresLanding=e.landingVerified=e.decisionPoint=true;}
        return e;
    });
    check(delayed.hasValidatedInput && !delayed.down && delayed.plan.at(10) &&
          delayed.selected.landingVerified,"safe later takeoff outranks short wait and fatal early jump");
    c.jumpEventCount=0; c.budgetMs=8; bot.reset(); int calls=0;
    Clock::time_point firstDeadline{}; bool firstButton=false, reserved=false;
    auto fair=bot.decide(c,false,[&](Plan const &p,auto deadline){
        ++calls;
        if(calls==1) {
            firstDeadline=deadline;firstButton=p.at(0);
            Evaluation e;e.interrupted=true;return e;
        }
        if(calls==2) reserved=p.at(0)!=firstButton && deadline>firstDeadline;
        Evaluation e;e.survived=c.horizon;return e;
    });
    check(reserved && fair.firstActionCandidates[0]>0 && fair.firstActionCandidates[1]>0 && fair.hasValidatedInput,
          "slow first forecast cannot starve the opposite button");
    c.sharedDeadline=Clock::now();calls=0;bot.reset();
    auto expired=bot.decide(c,false,[&](Plan const&,auto){++calls;return Evaluation{};});
    check(expired.exhausted && !expired.hasValidatedInput && calls==0,"expired shared deadline does not create a fresh tier budget");
    VerifiedInputQueue q; Decision d; d.hasValidatedInput=true; d.certificateTicks=d.plan.length=4;
    d.plan.buttons[0]=true;d.plan.buttons[2]=true;
    for(int i=0;i<=4;++i)d.selected.traces[0].push_back({float(i),0});
    std::array<ModeState,2> modes{};modes[0].mode=PlannerMode::Ufo;
    check(q.seed(d,modes,false,1./240),"airborne edge queue seeds");
    check(q.transitionDue(false),"airborne DOWN is due");q.consume(true);
    check(q.transitionDue(true) && !q.down(),"verified release is due");q.consume(false);
    check(q.transitionDue(false),"next rising edge remains due");
    for(auto mode:{PlannerMode::Ship,PlannerMode::Ball,PlannerMode::Ufo,PlannerMode::Wave,
                   PlannerMode::Robot,PlannerMode::Spider,PlannerMode::Swing}) {
        c={};c.plannerMode=mode;c.immediateOrb=true;c.horizon=72;c.candidates=24;
        bot.reset();auto plans=bot.propose(c,true);bool rearm=false;
        for(auto const&p:plans)rearm|=!p.at(0)&&p.at(1);
        check(rearm,"each held-button orb mode has a release and repress proposal");
        if(mode==PlannerMode::Ship || mode==PlannerMode::Wave || mode==PlannerMode::Robot) {
            bool hold=false;for(auto const&p:plans)hold|=p.at(0)&&p.at(1);
            check(hold,"optional ring does not remove continuous hold");
        }
    }
    std::cout<<"PASS: fatal/unknown launch rejection, grounded waits, delayed safe takeoffs, fair deadlines and queued airborne edges\n";
}

