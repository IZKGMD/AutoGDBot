#include "interaction_state.hpp"
#include "verified_input_queue.hpp"
#include "input_motion.hpp"
#include "landing_frame.hpp"
#include "flight_certificate.hpp"
#include <iostream>
#include <cstdlib>
#include <limits>
using namespace agb;
static void check(bool b,char const*why){if(!b){std::cerr<<"FAIL: "<<why<<'\n';std::exit(1);}}
struct Object {
    bool m_isActivated=false,m_isDisabled=false,m_isDisabled2=false,m_isRingPoweredOn=false;
};
struct Enhanced : Object {
    bool m_activated=false,m_activatedByPlayer1=false,m_activatedByPlayer2=false,m_poweredOn=false;
    int m_state=0;
};
struct Ring : Enhanced {bool m_claimTouch=false;};
using Undo=InteractionSnapshot<Object,Enhanced,Ring>;
static int bits(Ring const&r){return r.m_isActivated | r.m_isDisabled<<1 | r.m_isDisabled2<<2 |
    r.m_isRingPoweredOn<<3 | r.m_activated<<4 | r.m_activatedByPlayer1<<5 |
    r.m_activatedByPlayer2<<6 | r.m_poweredOn<<7 | r.m_claimTouch<<8;}
static Ring ring(int n,int state){Ring r;r.m_isActivated=n&1;r.m_isDisabled=n&2;r.m_isDisabled2=n&4;
    r.m_isRingPoweredOn=n&8;r.m_activated=n&16;r.m_activatedByPlayer1=n&32;
    r.m_activatedByPlayer2=n&64;r.m_poweredOn=n&128;r.m_claimTouch=n&256;r.m_state=state;return r;}
static Decision route(int ticks=12){Decision d;d.hasValidatedInput=true;d.certificateTicks=ticks;
    d.plan.length=12;d.plan.buttons[6]=d.plan.buttons[8]=true;
    d.selected.survived=12;d.selected.firstActionVerified=true;
    for(int i=0;i<=12;++i){d.selected.traces[0].push_back({float(i)*1.3f,50});
        d.selected.traces[1].push_back({float(i)*1.3f,100});}
    return d;}
struct Jump {bool held=false,m_jumpBuffered=false,m_stateRingJump=true,m_stateRingJump2=false;
    unsigned char m_stateJumpBuffered=0;
    bool m_touchedRing=true,m_touchedCustomRing=true;};
int main(){
    // The production undo implementation must preserve every already-used
    // real state as well as unused pads/rings for both players.
    for(int n=0;n<512;++n)for(int state=0;state<4;++state){
        auto r=ring(n,state);Undo undo(&r,&r,&r);
        r=ring(n^511,99);undo.restore();
        check(bits(r)==n && r.m_state==state,"all activation/contact fields restored");
    }
    Enhanced pad;pad.m_poweredOn=true;pad.m_state=2;
    for(int candidate=0;candidate<96;++candidate){
        Undo undo(&pad,&pad,nullptr);
        pad.m_activated=pad.m_activatedByPlayer1=true;pad.m_poweredOn=false;pad.m_state=0;
        undo.restore();
        check(!pad.m_activatedByPlayer1 && pad.m_poweredOn && pad.m_state==2,"forecast leaves live pad usable");
    }
    // Real pad activation then becomes the baseline; rollback must not undo it.
    pad.m_activatedByPlayer1=true;
    {Undo undo(&pad,&pad,nullptr);pad.m_activatedByPlayer2=true;undo.restore();}
    check(pad.m_activatedByPlayer1 && !pad.m_activatedByPlayer2,"dual pad baseline stays consumed for only P1");
    Object solid;solid.m_isActivated=true;{Undo undo(&solid,nullptr,nullptr);solid.m_isDisabled=true;undo.restore();}
    check(solid.m_isActivated&&!solid.m_isDisabled,"plain solid rollback without enhanced cast");
    Ring shared;
    struct Interrupted {Undo u;~Interrupted(){u.restore();}};
    auto forecast=[&]{Interrupted scope{Undo(&shared,&shared,&shared)};
        shared.m_activatedByPlayer1=shared.m_claimTouch=true;return;};
    forecast();check(!shared.m_activatedByPlayer1&&!shared.m_claimTouch,"interrupted forecast restores ring ownership");

    VerifiedInputQueue queue;auto d=route();std::array<ModeState,2> states{};
    constexpr double dt=1.0/240;
    check(queue.seed(d,states,false,dt),"seed a verified route");
    Jump p;int hits=0;
    // Search is deliberately unavailable throughout two close orb windows.
    // The checked plan must still send exactly DOWN/UP/DOWN at 6/7/8.
    for(int t=0;t<12;++t){
        std::array<Point,2> real{Point{t*1.3f,50},{t*1.3f,100}};
        check(queue.matches(real,states,false,dt),"queue stays aligned with actual substep");
        bool down=queue.down();auto suffix=queue.continuation();
        check(suffix.plan.at(0)==down && suffix.selected.traces[0][0].x==real[0].x,"suffix preserves click time and trace alignment");
        bool changed=applyJumpState(down,[&]{return p.held;},[&]{p.held=p.m_jumpBuffered=true;},[&]{p.held=p.m_jumpBuffered=false;});
        if(changed&&down)refreshPressedJumpSnapshot(p);
        if(t==6||t==8){check(p.m_stateJumpBuffered&&p.m_stateRingJump2,"native collision sees same-substep orb press");++hits;}
        if(t==7)check(!p.held,"release separates close orb presses");
        // The game's regular snapshot preparation for the next substep.
        p.m_stateJumpBuffered=p.m_jumpBuffered;p.m_stateRingJump2=p.m_stateRingJump;
        queue.consume(p.held);
    }
    check(hits==2 && queue.remaining()==0,"rapid orb windows survive search starvation without exceeding proof");
    check(!queue.matches({Point{15.6f,50},{}},states,false,dt),"expired certificate cannot supply an unchecked click");
    check(p.m_touchedRing&&p.m_touchedCustomRing,"snapshot refresh preserves native contact flags");
    p.m_jumpBuffered=false;p.m_stateJumpBuffered=1;
    refreshPressedJumpSnapshot(p);check(p.m_stateJumpBuffered==1,"release retains native one-step delay");

    check(queue.seed(d,states,true,dt),"shared dual route seed");
    std::array<Point,2> positions{Point{0,50},{0,100}};
    check(queue.matches(positions,states,true,dt),"both players match");
    auto bad=positions;bad[1].x=1;check(!queue.matches(bad,states,true,dt),"P2 divergence invalidates timed input");
    check(!queue.matches(positions,states,false,dt),"dual exit invalidates queue");
    auto changed=states;changed[1].mini=true;check(!queue.matches(positions,changed,true,dt),"secondary mini portal invalidates queue");
    changed=states;changed[0].upsideDown=true;check(!queue.matches(positions,changed,true,dt),"gravity change invalidates queue");
    check(!queue.matches(positions,states,true,dt*2),"TPS change invalidates old click timestamps");
    auto nan=std::numeric_limits<double>::quiet_NaN();check(!queue.seed(d,states,false,nan),"nonfinite time cannot certify input");
    for(int failure=0;failure<5;++failure){auto unsafe=d;
        switch(failure){case 0:unsafe.hasValidatedInput=false;break;case 1:unsafe.selected.died=true;break;
        case 2:unsafe.selected.interrupted=true;break;case 3:unsafe.selected.firstActionVerified=false;break;
        case 4:unsafe.certificateTicks=0;break;}
        check(!queue.seed(unsafe,states,false,dt),"unsafe/partial forecasts cannot seed queue");
    }
    auto incomplete=d;incomplete.selected.traces[1].clear();check(!queue.seed(incomplete,states,true,dt),"missing second-player proof rejected");
    queue.seed(d,states,false,dt);queue.consume(true);check(queue.remaining()==0,"actual input mismatch clears queue");
    auto prefix=route(2);queue.seed(prefix,states,false,dt);queue.consume(false);queue.consume(false);
    check(queue.remaining()==0&&!queue.down(),"long plan cannot outlive its short certificate");

    std::vector<Bounds> floor{{0,0,150,30}};
    check(supportRunway({85,30,115,60},1.3f,floor)==65,"support lasts until the trailing contact edge leaves");
    check(supportRunway({140,30,170,60},1.3f,floor)==10,"overhanging cube retains finite contact runway");
    check(supportRunway({10,30,40,60},-1.3f,floor)==40,"reverse trailing edge runway");
    check(!landingDecisionSafe(int(35/1.3f),30,false,false),"short ledge cannot certify delayed takeoff");
    floor.push_back({150,0,300,30});check(supportRunway({140,30,170,60},1.3f,floor)==160,"joined supports retain continuous runway");
    auto invFoot=landingFrame({85,-60,115,-30},false,true);
    std::vector<Bounds> ceiling{landingFrame({0,-30,150,0},false,true)};
    check(supportRunway(invFoot,1.3f,ceiling)==65,"ceiling runway after gravity flip");
    std::cout<<"Interaction tests passed: 2048 rollback states, live pad/dual ownership, rapid orb input, cache invalidation and ledge runway.\n";
}

