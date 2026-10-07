#include "controller.hpp"
#include "flight_certificate.hpp"
#include "input_motion.hpp"
#include "landing_schedule.hpp"
#include <iostream>
#include <stdexcept>
using namespace agb;
static void check(bool ok,char const *why){if(!ok)throw std::runtime_error(why);}
static Config config(){Config c;c.plannerMode=PlannerMode::Cube;c.cubePulsePlanning=true;
    c.compareFirstActions=true;c.horizon=120;c.candidates=24;c.budgetMs=8;return c;}
// Independent empty-floor movement fixture. A jump has a real airborne phase
// and touchdown, but there is no obstacle or reason to launch it.
static Evaluation emptyFloor(Plan const&p){
    Evaluation e;e.hasRealized=true;e.traces[0].push_back({0,15});
    float x=0,y=15,vy=0;bool ground=true,held=false;CubePulse pulse;
    LandingSchedule schedule(p.landingDelay,true);FlightCertificate flight(true);
    e.buttonChanges=0;e.nearClearance=e.clearance=120;e.robustness=1;
    for(int t=0;t<MaxHorizon;++t){
        bool before=ground;
        bool request=schedule.request(t<p.length&&p.at(t),ground,pulse.mustRelease(),t<p.length);
        bool down=pulse.command(request,true,ground);schedule.sent(down,ground);
        if(down&&ground){vy=11.18f;ground=false;++e.cubeTakeoffs;}
        if(!ground){vy=std::max(-15.f,vy-.216f);y+=.225f*vy;
            if(y<=15&&vy<=0){y=15;vy=0;ground=true;}}
        x+=1.3f;e.realized.buttons[t]=down;e.buttonChanges+=down!=held;held=down;
        if(before&&ground&&!down&&e.groundedPrefixTicks==t)++e.groundedPrefixTicks;
        flight.observe(before,ground,down);
        if(!before&&ground)++e.landingCount;
        ++e.survived;e.realized.length=e.survived;e.traces[0].push_back({x,y});
        if(e.survived>=p.length&&flight.verified())break;
    }
    e.requiresLanding=flight.required;e.firstActionVerified=flight.verified();
    e.landingVerified=flight.required&&flight.verified();e.decisionPoint=e.landingVerified;
    return e;
}
static Evaluation failure(Plan const&p,int death){
    Evaluation e;e.survived=death;e.died=e.nativeDeath=true;e.firstActionVerified=false;
    e.hasRealized=true;e.realized=p;e.realized.length=death;return e;
}
static bool pulseIn(Plan const&p,int lo,int hi){
    for(int t=lo;t<=hi;++t)if(p.at(t)&&!p.at(t-1))return true;return false;
}
int main(){
    int failures=0;
    auto run=[&](char const *name,auto test){try{test();std::cout<<"PASS: "<<name<<'\n';}
        catch(std::exception const&e){++failures;std::cerr<<"FAIL: "<<name<<": "<<e.what()<<'\n';}};
    run("empty floor does not earn a jump",[]{
        auto c=config();c.horizon=180;Controller solver;
        auto d=solver.decide(c,false,[](Plan const&p,auto){return emptyFloor(p);});
        check(d.hasValidatedInput&&!d.down,"equally safe coast must outrank an unnecessary launch/landing");
        check(d.selected.groundedPrefixTicks>=c.horizon,"selected route stays on the unobstructed floor");
    });
    run("coast repairs cannot consume all compound-jump slots",[]{
        auto c=config();c.horizon=180;Controller solver;
        auto d=solver.decide(c,false,[](Plan const&p,auto){
            if(p.at(0)&&pulseIn(p,100,140)){
                Evaluation e;e.survived=180;e.hasRealized=true;e.realized=p;e.realized.length=180;
                e.requiresLanding=e.landingVerified=e.decisionPoint=true;e.clearance=e.nearClearance=30;
                return e;
            }
            auto e=failure(p,p.at(0)?160:150);e.requiresLanding=p.at(0);
            for(int t=p.at(0)?100:0;t<(p.at(0)?141:150);++t){
                e.groundedActions.push_back(t);e.controlActions.push_back({t,PlannerMode::Cube});}
            return e;
        });
        check(d.hasValidatedInput&&d.down&&pulseIn(d.plan,100,140),
              "a native touchdown on the first block must still branch into the next jump");
        check(d.firstActionCandidates[0]&&d.firstActionCandidates[1],"both initial actions still receive evaluation");
    });
    run("airborne release prioritizes a discovered touchdown",[]{
        auto c=config();c.forceRelease=true;Controller solver;int calls=0;
        auto d=solver.decide(c,false,[&](Plan const&p,auto){
            if(++calls>2){Evaluation e;e.interrupted=true;return e;}
            if(pulseIn(p,20,28)){
                Evaluation e;e.survived=120;e.hasRealized=true;e.realized=p;e.realized.length=120;
                e.requiresLanding=e.landingVerified=e.decisionPoint=true;return e;
            }
            auto e=failure(p,40);e.requiresLanding=true;
            for(int t=20;t<=26;++t){e.groundedActions.push_back(t);e.controlActions.push_back({t,PlannerMode::Cube});}
            return e;
        });
        check(d.hasValidatedInput&&!d.down&&pulseIn(d.plan,20,28),
              "mandatory UP now must not defer all future grounded repairs behind baseline plans");
    });
    run("repair pool cannot exceed the evaluation budget",[]{
        auto c=config();c.candidates=16;c.forceRelease=true;Controller solver;int calls=0;
        auto d=solver.decide(c,false,[&](Plan const&p,auto){
            ++calls;auto e=failure(p,100);
            for(int t=10;t<100;++t){e.groundedActions.push_back(t);e.controlActions.push_back({t,PlannerMode::Cube});}
            return e;
        });
        check(calls<=c.candidates&&d.candidates==calls,"candidate setting limits actual native evaluations");
    });
    run("late portal control keeps its extended timeline",[]{
        auto c=config();c.horizon=72;c.candidates=96;Controller solver;
        auto d=solver.decide(c,false,[](Plan const&p,auto){
            if(!p.at(0))return failure(p,30);
            auto e=failure(p,106);e.transitions={{70,PlannerMode::Ship}};
            if(p.at(70)&&p.at(80)&&p.length>=114){
                e.controlActions={{90,PlannerMode::Ship}};
                bool released=true;
                for(int t=90;t<98;++t)released &= !p.at(t);
                if(released){
                    e.died=e.nativeDeath=false;e.survived=p.length;
                    e.realized.length=p.length;e.firstActionVerified=e.portalVerified=true;
                    e.decisionPoint=true;e.clearance=e.nearClearance=30;
                }
            }
            return e;
        });
        check(d.hasValidatedInput&&d.down&&d.selected.portalVerified,
              "ship correction after the configured horizon must remain a valid portal continuation");
        check(d.plan.length>=114&&d.plan.at(70)&&d.plan.at(80),
              "repair preserves the prefix that actually reached the portal");
        for(int t=90;t<98;++t)check(!d.plan.at(t),"native-discovered correction is on the extended timeline");
    });
    return failures?1:0;
}
