#include "controller.hpp"
#include "portal_continuation.hpp"
#include "verified_input_queue.hpp"
#include <iostream>
#include <limits>
#include <stdexcept>
using namespace agb;
static void check(bool ok, char const *why) { if (!ok) throw std::runtime_error(why); }
static ModeState state(PlannerMode m) {
    ModeState s; s.mode=m; s.flags=m==PlannerMode::Cube?0:1u<<(int(m)-1); return s;
}
// Contract fixture, not a replacement model of GD physics: the evaluator
// reports actual contact and the search must retain the prefix that reached it.
static Evaluation gapPortal(Plan const &p, PlannerMode target, bool fatalAfter=false,
                            int contact=8, int end=72) {
    Evaluation e; e.hasRealized=true; e.realized=p;
    e.traces[0].push_back({0,0}); e.modeTraces[0].push_back(state(PlannerMode::Cube));
    if (!p.at(0)) { e.survived=12; e.died=e.nativeDeath=true;
        e.firstActionVerified=false; e.requiresLanding=true; return e; }
    e.transitions.push_back({contact, target});
    PortalContinuation proof; proof.changed(contact-1);
    bool edge=modePolicy(target).input==InputKind::AirEdge || modePolicy(target).input==InputKind::GroundEdge ||
              modePolicy(target).input==InputKind::GroundPulse;
    bool newAction=edge ? p.at(contact) && !p.at(contact-1) : p.at(contact) && p.at(contact+1);
    if (!newAction || fatalAfter) {
        e.survived=contact+4; e.died=e.nativeDeath=true; e.firstActionVerified=false;
    } else {
        e.survived=end; e.portalVerified=proof.verified(end,1./240);
        e.firstActionVerified=e.decisionPoint=e.portalVerified;
        e.clearance=e.nearClearance=30; e.robustness=1;
        if(modePolicy(target).mustLand) e.requiresLanding=e.landingVerified=true;
    }
    e.postPortalTicks=proof.after(e.survived);e.realized.length=e.survived;
    for(int i=1;i<=e.survived;++i) {
        e.traces[0].push_back({float(i), i<contact?20.f:100.f});
        e.modeTraces[0].push_back(state(i<contact?PlannerMode::Cube:target));
    }
    return e;
}
int main() {
    auto b=portalBand(405,300);check(b.active && b.floor==240 && b.ceiling==540,"band uses measured 30-grid floor");
    b=portalBand(0,240);check(b.floor==90 && b.ceiling==330,"band clamps minimum ground");
    check(!portalBand(std::numeric_limits<float>::quiet_NaN(),300).active,"invalid portal cannot produce a band");
    for(double dt:{1./60,1./120,1./240,1./480}) {
        PortalContinuation p;check(p.verified(0,dt),"no transition requires no extra proof");
        p.changed(10);int required=std::max(4,int(std::ceil(.18/dt)));
        check(!p.verified(10+required,dt) && p.verified(11+required,dt),"full post-contact reaction window required");
        p.changed(10+required);check(!p.verified(11+required,dt),"a second portal resets the continuation proof");
    }
    Config c;c.plannerMode=PlannerMode::Cube;c.cubePulsePlanning=true;c.compareFirstActions=true;
    c.horizon=72;c.candidates=96;c.budgetMs=8;c.tickSeconds=1./240;
    for(auto target:{PlannerMode::Ship,PlannerMode::Ball,PlannerMode::Ufo,PlannerMode::Wave,
                     PlannerMode::Robot,PlannerMode::Spider,PlannerMode::Swing}) {
        Controller solver;
        auto d=solver.decide(c,false,[&](Plan const&p,auto){return gapPortal(p,target);});
        check(d.hasValidatedInput && d.down && d.selected.portalVerified,"jump over gap and verify new-mode continuation");
        check(d.plan.at(8),"new-mode action belongs to same selected lineage");
        check(d.selected.modeTraces[0][8].mode==target,"mode trace records the actual transition");
        check(d.selected.routeId==routeFingerprint(d.plan),"rendered route ID identifies the executed plan");
        solver.reset();auto dead=solver.decide(c,false,[&](Plan const&p,auto){return gapPortal(p,target,true);});
        check(!dead.hasValidatedInput,"portal contact followed by death cannot certify takeoff");
    }
    Controller solver;
    auto late=solver.decide(c,false,[&](Plan const&p,auto){
        return gapPortal(p,PlannerMode::Ship,false,90,180);
    });
    check(late.hasValidatedInput && late.down && late.plan.at(90) && late.selected.portalVerified,
          "portal reached in a landing tail can extend and branch beyond the initial horizon");
    solver.reset();
    auto chain=solver.decide(c,false,[&](Plan const&p,auto){
        auto e=gapPortal(p,PlannerMode::Ship);
        if(!p.at(0))return e;
        if(!(p.at(22)&&!p.at(21))) {
            e.survived=38;e.died=e.nativeDeath=true;e.firstActionVerified=e.portalVerified=false;
            e.orbActions={22};e.controlActions={{22,PlannerMode::Ship}};
            e.realized.length=38;
        }
        return e;
    });
    check(chain.hasValidatedInput && chain.down && chain.plan.at(8) && chain.plan.at(22) && !chain.plan.at(21),
          "ship continuation can release and activate the next native-discovered orb");
    solver.reset();
    auto lateOrb=solver.decide(c,false,[&](Plan const&p,auto){
        auto e=gapPortal(p,PlannerMode::Ship,false,90,180);
        if(!p.at(0)||!p.at(90)||!p.at(91))return e;
        if(!(p.at(126)&&!p.at(125))) {
            e.survived=140;e.died=e.nativeDeath=true;e.firstActionVerified=e.portalVerified=false;
            e.orbActions={126};e.controlActions={{126,PlannerMode::Ship}};
            e.realized.length=140;
        }
        return e;
    });
    check(lateOrb.hasValidatedInput&&lateOrb.down&&lateOrb.plan.at(90)&&lateOrb.plan.at(126)&&!lateOrb.plan.at(125),
          "an orb in the extended post-portal tail must retain its actual contact tick");
    solver.reset();
    auto shortProof=solver.decide(c,false,[&](Plan const&p,auto){
        auto e=gapPortal(p,PlannerMode::Ship);
        if (!p.at(0)) return e;
        e.died=false;e.survived=72;e.boundary=e.decisionPoint=e.firstActionVerified=true;
        e.portalVerified=false;return e;
    });
    check(!shortProof.hasValidatedInput,"legacy boundary/decision flags cannot override missing post-portal proof");
    VerifiedInputQueue q;Decision d;d.hasValidatedInput=true;d.certificateTicks=d.plan.length=8;
    d.selected.survived=8;d.selected.hasRealized=true;d.selected.realized=d.plan;
    d.selected.firstActionVerified=d.selected.portalVerified=true;d.selected.transitions={{3,PlannerMode::Ship}};
    d.plan.buttons[3]=d.plan.buttons[4]=true;
    d.selected.realized=d.plan;
    for(int t=0;t<=8;++t) {
        d.selected.traces[0].push_back({float(t),float(t)});
        d.selected.motions[0].push_back({double(t),false,false,false,false,uint64_t(t/4)});
        d.selected.modeTraces[0].push_back(state(t<3?PlannerMode::Cube:PlannerMode::Ship));
        if(t>=5) {auto &s=d.selected.modeTraces[0].back();s.mini=s.upsideDown=true;s.speed=600;}
    }
    std::array<ModeState,2> modes{state(PlannerMode::Cube),{}};
    check(q.seed(d,modes,false,1./240),"portal route queues with complete per-step modes");
    for(int t=0;t<8;++t) {
        modes[0]=d.selected.modeTraces[0][t];
        std::array<Point,2> points{d.selected.traces[0][t],{}};
        std::array<MotionSample,2> motion{d.selected.motions[0][t],{}};
        check(q.matches(points,modes,false,1./240,&motion),"expected mode change retains exact click timing");
        auto wrong=modes;wrong[0].upsideDown=!wrong[0].upsideDown;
        check(!q.matches(points,wrong,false,1./240,&motion),"unexpected gravity rejects the queued path");
        auto used=motion;used[0].usedInteractions^=123;
        check(!q.matches(points,modes,false,1./240,&used),"changed ring consumption rejects the queued path");
        auto suffix=q.continuation();
        check(suffix.selected.modeTraces[0][0]==modes[0] && suffix.selected.motions[0][0].matches(motion[0]),
              "queued suffix keeps positions, motion and modes on one timeline");
        check(suffix.selected.routeId==routeFingerprint(suffix.plan),"suffix trace uses suffix input identity");
        q.consume(q.down());
    }
    auto incomplete=d;incomplete.selected.modeTraces[0].resize(4);
    check(!q.seed(incomplete,modes,false,1./240),"incomplete mode proof cannot seed future clicks");
    incomplete=d;incomplete.selected.modeTraces[0].clear();
    check(!q.seed(incomplete,modes,false,1./240),"a portal route cannot fall back to a root-only mode proof");
    auto e=d.selected;e.orbActions={1,4};e.dashActions={5};e.controlActions={{1,PlannerMode::Cube},{4,PlannerMode::Ship}};
    e.discontinuities[0]={2,6};advanceEvaluation(e,3);
    check(e.survived==5 && e.traces[0][0].x==3 && e.motions[0][0].velocity==3 &&
          e.modeTraces[0][0].mode==PlannerMode::Ship,"light tick shifts every state array together");
    check(e.orbActions==std::vector<int>{1} && e.dashActions==std::vector<int>{2} &&
          e.discontinuities[0]==std::vector<int>{3} && e.transitions[0].tick==0,
          "orb, mode and teleport event indices follow the same suffix");
    check(e.realized.at(0) && e.realized.at(1) && !e.realized.at(2),"trace shift also shifts the exact input sequence");
    std::cout<<"PASS: gap-to-portal branching, post-contact proof, mode transitions, consumed orbs and aligned route traces\n";
}
