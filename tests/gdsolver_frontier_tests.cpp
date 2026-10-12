#include "gdsolver_frontier.hpp"
#include "realtime_search.hpp"
#include "trace_geometry.hpp"
#include <iostream>
#include <stdexcept>
#include <tuple>
using namespace agb;
void check(bool v,char const *s){if(!v)throw std::runtime_error(s);}
struct Node{Plan plan;Evaluation state;};
Node node(float y,double vy,float margin,int tick=24,bool down=false,uint64_t interactions=0){
    Node n;n.plan.length=tick;n.plan.buttons[0]=down;n.state.survived=tick;
    n.state.clearance=n.state.endpointClearance=margin;n.state.buttonChanges=2;
    n.state.traces[0].push_back({float(tick),y});
    MotionSample m;m.velocity=vy;m.usedInteractions=interactions;
    n.state.motions[0].push_back(m);ModeState mode;mode.mode=PlannerMode::Ship;
    n.state.modeTraces[0].push_back(mode);return n;
}
auto rank(Node const &n){return std::tuple{n.state.survived,n.state.clearance,-n.state.buttonChanges};}
// Independent oracle: a rising corridor whose safe input changes with x.
// This fixture is deliberately unrelated to measured GD physics.
struct WaveOracle {
    float x=0,y=0;bool corrupt=false;
    static float center(float x){float p=std::fmod(x,40.f);return p<20?p*.4f:(40-p)*.4f;}
    bool step(bool down){x+=1;y+=down?1:-1;return std::abs(y-center(x))<10;}
    Evaluation prefix(Plan const &p,Clock::time_point deadline,bool memo){
        auto b=*this;Evaluation e;e.hasRealized=true;e.firstActionVerified=true;
        e.clearance=e.nearClearance=120;e.buttonChanges=0;bool held=false;
        auto sample=[&]{e.traces[0].push_back({b.x,b.y});MotionSample m;m.velocity=held?1:-1;
            m.usedInteractions=(!memo&&corrupt)?9:0;e.motions[0].push_back(m);
            ModeState s;s.mode=PlannerMode::Wave;e.modeTraces[0].push_back(s);};sample();
        for(int i=0;i<p.length;++i){
            if(Clock::now()>=deadline){e.interrupted=true;break;}
            e.controlActions.push_back({i,PlannerMode::Wave});e.buttonChanges+=held!=p.at(i);held=p.at(i);
            if(!b.step(held)){e.died=e.nativeDeath=true;break;}
            e.realized.buttons[i]=held;e.realized.length=++e.survived;sample();
            e.endpointClearance=10-std::abs(b.y-center(b.x));e.clearance=std::min(e.clearance,e.endpointClearance);
        }return e;
    }
};
int main(){
    {
        TraceCadence c;auto base=Clock::time_point{};int renders=0;
        for(int i=0;i<1200;++i){
            int jitter=i%3==0?-300000:i%3==1?300000:0;
            renders+=c.consume(base+std::chrono::nanoseconds(int64_t(i)*1000000000/120+jitter),120);
        }
        check(renders==1200,"120-Hz trace refresh dropped frames under small presentation jitter");
        c.reset();renders=0;
        for(int i=0;i<2400;++i)renders+=c.consume(base+std::chrono::nanoseconds(int64_t(i)*1000000000/240),120);
        check(renders>=1199&&renders<=1201,"trace scheduler ignored selected refresh rate");
        check(c.consume(base+std::chrono::seconds(100),120),"trace scheduler failed to recover after pause");
        std::cout<<"PASS: 120 Hz phase under jitter, 240-to-120 rate limit and pause recovery\n";
    }
    {
        gdsolver::CellMap map;map.ensure(1);gdsolver::CellKey zero;map.at(zero).hi=123;
        for(uint64_t i=1;i<=20000;++i){auto k=zero;k.fields[0]=i;map.at(k).lo=int(i);}
        check(map.at(zero).hi==123,"zero cell was confused with an empty slot");
        for(uint64_t i=1;i<=20000;++i){auto k=zero;k.fields[0]=i;check(map.at(k).lo==int(i),"growth lost a reachability cell");}
        map.clear();check(map.size()==0&&map.at(zero).hi==-1,"map clear retained old lineage");
        std::cout<<"PASS: zero key, 20000 cells, growth and clear\n";
    }
    {
        gdsolver::NativeFrontier f;
        std::vector<Node> ns{node(10,.02,100),node(10,.35,90),node(10,.18,80)};
        f.prune(ns,rank);
        check(ns.size()==2,"cell did not preserve both vy extremes");
        check(std::any_of(ns.begin(),ns.end(),[](auto const &n){return n.state.motions[0][0].velocity>.3;}),"climb lineage lost");
        check(std::any_of(ns.begin(),ns.end(),[](auto const &n){return n.state.motions[0][0].velocity<.1;}),"slow/dive recovery lineage lost");
        std::cout<<"PASS: hi/lo velocity representatives\n";
    }
    {
        std::vector<Node> ns;
        // Four roomy histories share almost the same height. Only the lower
        // fifth state can pass a subsequent ceiling; old 2-per-root drops it.
        for(int i=0;i<4;++i)ns.push_back(node(50+i*.01f,.1,100-i));
        ns.push_back(node(4,-.5,40));ns.push_back(node(22,.1,80,24,true));
        gdsolver::NativeFrontier f;f.prune(ns,rank);
        auto canPassCeiling=[](Node const &n){float y=n.state.traces[0][0].y;double v=n.state.motions[0][0].velocity;
            for(int t=0;t<8;++t){v-=.1;y+=float(v);if(y>=12)return false;}return true;};
        check(std::any_of(ns.begin(),ns.end(),canPassCeiling),"roomy dead end evicted the only ceiling route");
        check(std::any_of(ns.begin(),ns.end(),[](auto const &n){return n.plan.at(0);}),"DOWN root starved");
        std::cout<<"PASS: independently integrated ceiling route survives roomy dead-end histories\n";
    }
    {
        gdsolver::NativeFrontier f;std::vector<Node> ns;
        ns.push_back(node(10,.1,100,24,false,1));ns.push_back(node(10,.1,100,24,false,2));
        ns.push_back(node(10,.1,100,25,false,1));
        auto dual=ns[0];dual.state.traces[1]={{24,30}};MotionSample m;m.velocity=.1;
        dual.state.motions[1]={m};dual.state.modeTraces[1]=dual.state.modeTraces[0];ns.push_back(dual);
        auto flipped=ns[0];flipped.state.modeTraces[0][0].upsideDown=true;ns.push_back(flipped);
        auto forced=ns[0];forced.state.motions[0][0].force={.01f,0};ns.push_back(forced);
        f.prune(ns,rank);check(ns.size()==6,"tick/dual/gravity/consumed-ring/force states merged");
        std::cout<<"PASS: independent state identity for tick, dual, gravity, interactions and forces\n";
    }
    {
        gdsolver::ContinuationBank bank;Plan p;p.length=40;p.buttons[0]=true;p.buttons[7]=true;
        bank.remember(p,32);auto up=p;up.buttons[0]=false;bank.remember(up,30);
        check(bank.size()==2,"proposal bank lost a root");bank.advance(1,true);
        auto out=bank.take();check(out.size()==1&&out[0].length==39&&out[0].at(6)&&!out[0].at(0),"bank retained incompatible input or wrong coordinate");
        bank.reset();check(bank.take().empty(),"level reset leaked proposals");
        std::cout<<"PASS: proposals advance with actually applied command, without certificates\n";
    }
    {
        RealtimeSearch s;WaveOracle o;Config c;c.horizon=56;c.budgetMs=8;c.candidates=96;
        c.tickSeconds=1./120;c.plannerMode=PlannerMode::Wave;bool held=false;int resumed=0,cells=0;
        for(int frame=0;frame<180;++frame){
            auto d=s.decide(c,held,o);check(d.hasValidatedInput||d.recoveryInput,"online frontier issued no checked command");
            check(o.step(d.down),"independent online replay died");resumed+=d.resumedPlans;cells+=d.reachableCells;
            held=d.down;s.advanceWithInput(held);
        }
        check(resumed>0&&cells>0,"realtime integration did not use persistent state frontier");
        o.corrupt=true;auto bad=s.decide(c,held,o);
        check(!bad.hasValidatedInput&&!bad.recoveryInput,"retained proposal bypassed independent replay after world divergence");
        std::cout<<"PASS: 180 live 120-TPS steps, resumed="<<resumed<<", cells="<<cells<<", corrupted replay rejected\n";
    }
    std::cout<<"All GDSolver native-frontier regressions passed\n";
}
