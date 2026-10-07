#include "mode_policy.hpp"
#include "controller.hpp"
#include "flight_certificate.hpp"
#include "landing_frame.hpp"
#include <cstdlib>
#include <iostream>
using namespace agb;
static void check(bool v, char const *why) {
    if (!v) { std::cerr << "FAIL: " << why << '\n'; std::exit(1); }
}
static Config config(PlannerMode m) {
    Config c; c.plannerMode=m; c.horizon=120; c.candidates=96;
    c.budgetMs=8; c.compareFirstActions=true;
    c.cubePulsePlanning=m==PlannerMode::Cube;
    return c;
}
int main() {
    // All native combinations, including residual ship flags on transitions.
    for (int bits=0;bits<128;++bits) {
        int expected=0;
        for(int k=1;k<=7;++k) if(bits & (1<<(k-1))) expected=k;
        check(int(modeFromFlags(bits))==expected,"native mode flag precedence");
    }
    ModeState base;
    for(int field=0;field<9;++field) {
        auto s=base;
        switch(field) {
        case 0:s.mode=PlannerMode::Wave;break; case 1:s.flags=1;break;
        case 2:s.mini=true;break;case 3:s.upsideDown=true;break;
        case 4:s.sideways=true;break;case 5:s.goingLeft=true;break;
        case 6:s.dash=true;break;case 7:s.speed=900;break;
        case 8:s.mode=PlannerMode::Swing;break;
        }
        check(s!=base,"portal/state change invalidates old plan");
    }
    ModeState dash;dash.mode=PlannerMode::Robot;dash.dash=true;
    check(dash.controlMode()==PlannerMode::Dash,"dash overrides vehicle controls");
    check(!controlOpportunity(PlannerMode::Ball,false,false),"ball grounded edge");
    check(!controlOpportunity(PlannerMode::Spider,false,false),"spider grounded edge");
    check(controlOpportunity(PlannerMode::Robot,false,true),"robot airborne orb opportunity");
    check(controlOpportunity(PlannerMode::Ufo,false,false),"UFO airborne edge");
    check(controlOpportunity(PlannerMode::Swing,false,false),"swing airborne edge");

    int routes=0;
    // Independent action-window oracle, not a model of GD's physics. The
    // three non-periodic checkpoints cannot be solved by one delayed click.
    for(auto m : {PlannerMode::Cube,PlannerMode::Ball,PlannerMode::Ufo,
                  PlannerMode::Spider,PlannerMode::Swing}) {
        Controller bot;auto c=config(m);
        auto d=bot.decide(c,false,[&](Plan const &p,auto) {
            Evaluation e;e.clearance=e.nearClearance=40;e.robustness=1;
            for(int t=0;t<c.horizon;++t) {
                const bool grounded=t==0 || t==27 || t==69;
                if(m==PlannerMode::Cube) { if(grounded)e.groundedActions.push_back(t); }
                else if(m==PlannerMode::Ball || m==PlannerMode::Spider) {
                    if(grounded)e.controlActions.push_back({t,m});
                } else e.controlActions.push_back({t,m});
                bool failed=(t==1 && !p.at(0)) ||
                    (t==51 && !(p.at(27)&&!p.at(26))) ||
                    (t==93 && !(p.at(69)&&!p.at(68)));
                if(failed){e.died=true;break;} ++e.survived;
            }
            e.firstActionVerified=!e.died;
            return e;
        });
        check(d.completeContinuation,"three mode-specific action windows");++routes;
    }
    {
        Controller bot;auto c=config(PlannerMode::Robot);
        auto d=bot.decide(c,false,[&](Plan const&p,auto){
            Evaluation e;e.clearance=e.nearClearance=40;e.robustness=1;
            e.controlActions={{0,PlannerMode::Robot},{27,PlannerMode::Robot}};
            bool shortJump=true,longJump=true;
            for(int t=0;t<8;++t)shortJump &= p.at(t);
            shortJump &= !p.at(8);
            for(int t=27;t<51;++t)longJump &= p.at(t);
            longJump &= !p.at(26)&&!p.at(51);
            e.survived=!shortJump?15:!longJump?70:c.horizon;
            e.died=e.survived<c.horizon;e.requiresLanding=true;
            e.firstActionVerified=!e.died;e.landingVerified=!e.died;
            return e;
        });
        check(d.completeContinuation,"robot short then long hold");++routes;
    }
    for(auto m:{PlannerMode::Ship,PlannerMode::Wave,PlannerMode::Dash}) {
        Controller bot;auto c=config(m);
        auto d=bot.decide(c,false,[&](Plan const&p,auto){
            Evaluation e;e.clearance=e.nearClearance=40;e.robustness=1;
            for(int t=0;t<c.horizon;++t) {
                e.controlActions.push_back({t,m});
                bool failed=(t==1&&!p.at(0)) || (t==32&&p.at(8)) ||
                    (t==51&&!p.at(27)) || (t==59&&p.at(35)) ||
                    (t==93&&!p.at(69)) || (t==101&&p.at(77));
                if(failed){e.died=true;break;}++e.survived;
            }
            e.firstActionVerified=!e.died;return e;
        });
        check(d.completeContinuation,"non-periodic hold and release corrections");++routes;
    }
    {
        Controller bot;auto c=config(PlannerMode::Robot);
        auto d=bot.decide(c,false,[](Plan const &,auto){
            Evaluation e;e.survived=100;e.died=true;e.requiresLanding=true;
            e.firstActionVerified=flightActionSafe(true,false,false,false);return e;
        });
        check(!d.hasValidatedInput,"robot cannot certify an unfinished fatal jump");
    }
    {
        Controller bot;auto c=config(PlannerMode::Ship);
        c.dual=true;c.secondaryMode=PlannerMode::Ufo;
        auto proposals=bot.propose(c,false);
        bool thrust=false,pulse=false;
        for(auto const&p:proposals){
            thrust |= p.at(0)&&p.at(1)&&p.at(2);
            pulse |= p.at(0)&&!p.at(1);
        }
        check(thrust&&pulse,"mixed dual includes both vehicle input bases");
        auto d=bot.decide(c,false,[](Plan const&,auto){
            Evaluation e;e.survived=120;e.firstActionVerified=false;
            e.requiresLanding=true;return e;
        });
        check(!d.hasValidatedInput,"secondary unsafe landing rejects shared dual action");
    }
    {
        // Dash releases into cube physics without a vehicle portal. Repairs
        // must follow the observed new control mode rather than the start mode.
        Controller bot;auto c=config(PlannerMode::Dash);
        auto d=bot.decide(c,false,[&](Plan const&p,auto){
            Evaluation e;e.clearance=e.nearClearance=40;e.robustness=1;
            e.controlActions={{0,PlannerMode::Dash},{27,PlannerMode::Cube},{69,PlannerMode::Cube}};
            e.survived=!p.at(0)?1:p.at(8)?32:
                !(p.at(27)&&!p.at(26))?51:!(p.at(69)&&!p.at(68))?93:c.horizon;
            e.died=e.survived<c.horizon;e.firstActionVerified=!e.died;return e;
        });
        check(d.completeContinuation,"dash release followed by cube input opportunities");
    }
    auto b=landingFrame({10,20,30,40},false,true);
    check(b.minX==10 && b.minY==-40 && b.maxY==-20,"inverted supporting surface");
    b=landingFrame({10,20,30,40},true,false);
    check(b.minX==20 && b.maxX==40 && b.minY==-30,"sideways travel coordinates");
    std::cout<<"Mode tests passed: "<<routes<<" action-window routes; flag, transition, dual and landing regressions.\n";
}
