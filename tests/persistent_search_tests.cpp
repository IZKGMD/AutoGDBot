#include "online_planner.hpp"
#include "prefix_cache.hpp"
#include "travel_phase.hpp"
#include <iostream>
#include <stdexcept>
using namespace agb;
void check(bool b,const char*s){if(!b)throw std::runtime_error(s);}
int main(){
    // Exhaust every hole pattern: sampled successes never bridge an untested
    // or failed tick in a launch window.
    for(int mask=0;mask<4096;++mask){
        std::vector<Plan> checked;
        for(int t=0;t<12;++t)if(mask&(1<<t)){Plan p;p.length=40;p.buttons[t+4]=true;p.buttons[30]=true;checked.push_back(p);}
        for(auto const&p:checked){int t=firstLaunch(p)-4,want=0;
            while(t-want-1>=0&&t+want+1<12&&(mask&(1<<(t-want-1)))&&(mask&(1<<(t+want+1))))++want;
            check(launchWindowMargin(p,checked)==want,"timing interval must consist of adjacent checked launches");
        }
    }
    struct Snapshot{int future=0;float y=0;};
    for(int length=24;length<=400;length+=8){
        PrefixCache<Snapshot> proposals,oracle;
        Plan p;p.length=length;p.buttons[length-8]=true;
        proposals.save(p,length/2,{length/2,0});oracle.save(p,length/3,{length/3,0});
        for(int frame=0;frame<10;++frame){
            auto validate=[](Snapshot&s){--s.future;return s.y==0;};
            proposals.reanchor(validate);oracle.reanchor(validate);
            for(int t=0;t<p.length-1;++t)p.buttons[t]=p.buttons[t+1];p.buttons[--p.length]=false;
            int a=0,b=0;auto x=proposals.find(p,a),y=oracle.find(p,b);
            if(length/3-frame-1>1)check(x&&y&&a==length/2-frame-1&&b==length/3-frame-1&&x->future==a&&y->future==b,
                "proposal and independent oracle advance separate snapshots against live time");
        }
        proposals.reanchor([](Snapshot&){return false;});int at=0;
        check(!proposals.find(p,at),"live drift drops all affected proposal snapshots");
        oracle.reset();check(oracle.size()==0,"world epoch change clears verification");
    }
    for(int kind=0;kind<3;++kind){
        TravelPhase p;
        for(int n=0;n<4;++n){
            p.begin(10,1.3f);float entry=kind==0?11.3f:10.f;
            p.observe(entry);p.beginUpdate(entry);p.endUpdate(entry+(kind==1?1.3f:0.f));p.finish();
            if(n<3)check(!p.measured,"single phase observation is insufficient");
        }
        check(p.measured&&p.beforeNative==(kind==0),"distinguish pre-collision travel from native/update tail");
    }
    // A search interrupted before completion retains candidates. Executing a
    // different first input discards incompatible speculative lineages.
    OnlinePlanner solver;Config c;c.horizon=80;c.candidates=4;c.budgetMs=8;c.plannerMode=PlannerMode::Ship;
    solver.decide(c,false,[](Plan const&p,auto){Evaluation e;e.survived=12;e.interrupted=true;e.hasRealized=true;e.realized=p;
        for(int t=0;t<=12;++t)e.traces[0].push_back({float(t),0});return e;});
    check(solver.pendingRoutes()>0,"deadline keeps unfinished frontier");
    solver.commit(false);check(solver.pendingRoutes()>0,"executed coast retains compatible future search");
    solver.reset();check(solver.pendingRoutes()==0,"reset removes all prior speculative state");
    std::cout<<"PASS: 4096 timing-hole patterns, 48 split-replay lengths, live invalidation, native phase and persistent frontier\n";
}
