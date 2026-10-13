#pragma once
#include "route_proof.hpp"
#include "launch_window.hpp"
#include <tuple>

namespace agb {
// One event-driven frontier, one proof policy and one absolute deadline.
// Native evaluators own physics. This kernel owns branching and only emits
// the checked prefix of one lineage, never mixing an UP trace with a DOWN.
class OnlinePlanner {
    Controller seeds_;
    Plan warm_;
    struct Node{Plan plan;int priority=0;bool repair=false;};
    std::vector<Node> future_;
  public:
    void reset(){seeds_.reset();warm_={};future_.clear();}
    void advance(int n=1){seeds_.advance(n);n=std::clamp(n,0,warm_.length);
        for(int t=0;t<warm_.length-n;++t)warm_.buttons[t]=warm_.buttons[t+n];
        warm_.length-=n;std::fill(warm_.buttons.begin()+warm_.length,warm_.buttons.end(),false);}
    void commit(bool down){
        std::erase_if(future_,[&](Node &node){
            auto &p=node.plan;
            if(p.length<=1||p.at(0)!=down)return true;
            for(int t=0;t<p.length-1;++t)p.buttons[t]=p.buttons[t+1];
            p.buttons[--p.length]=false;return false;
        });
        advance();
    }
    size_t pendingRoutes()const{return future_.size();}
    void seedVerifiedPrefix(Plan const&p,int n){seeds_.seedVerifiedPrefix(p,n);warm_=p;
        warm_.length=std::clamp(n,0,p.length);std::fill(warm_.buttons.begin()+warm_.length,warm_.buttons.end(),false);}
    template<class Evaluator> Decision decide(Config c,bool held,Evaluator &&evaluate){
        c.horizon=std::clamp(c.horizon,24,MaxPlanningHorizon);c.candidates=std::clamp(c.candidates,4,MaxCandidates);
        auto start=Clock::now();auto deadline=std::min(c.sharedDeadline,start+std::chrono::duration_cast<Clock::duration>(
            std::chrono::duration<double,std::milli>(std::clamp(c.budgetMs,.2,8.))));
        Decision result;result.down=c.forceRelease?false:held;
        if(start>=deadline){result.exhausted=true;return result;}
        const auto confirmationDeadline=deadline;
        if constexpr(requires(Plan p){evaluate(p,deadline,false);}){
            deadline=start+(deadline-start)*2/3;
        }
        Decision coldFallback;
        auto proposals=seeds_.propose(c,held);std::vector<Node> nodes;nodes.reserve(c.candidates*3);
        std::vector<int> pending;pending.reserve(c.candidates*3);
        auto add=[&](Plan p,int priority,bool repair){
            p.length=std::clamp(p.length,c.horizon,MaxHorizon);if(c.forceRelease)p.buttons[0]=false;
            if(nodes.size()>=size_t(c.candidates*3))return;
            for(auto const&old:nodes)if(old.plan.length==p.length&&old.plan.landingDelay==p.landingDelay&&old.plan.buttons==p.buttons)return;
            pending.push_back(int(nodes.size()));nodes.push_back({p,priority,repair});result.repairsCreated+=repair;
        };
        // Preserve mandatory release and compare both legal first actions
        // before any failed-lineage descendants can occupy the frontier.
        Plan coast;coast.length=c.horizon;coast.buttons.fill(false);
        Plan press=coast;press.buttons[0]=true;
        if(!c.cubePulsePlanning){press.buttons.fill(true);}
        add(c.immediateOrb?press:coast,1000000,false);
        if(!c.forceRelease)add(c.immediateOrb?coast:press,999999,false);
        if(warm_.length&&!c.immediateOrb)add(warm_,900000,false);
        for(auto const &node:future_)add(node.plan,20000+node.priority,node.repair);
        future_.clear();
        int seedPriority=5000;
        for(auto p:proposals)add(p,seedPriority--,false);
        auto best=std::tuple{-1,-1,-1,-1,-1,-1};
        struct Safe{Plan plan;Evaluation evaluation;RouteProof proof;decltype(best) rank;};
        std::vector<Safe> safe;
        auto repair=[&](Plan const&p,Evaluation const&e,int at,PlannerMode m,bool orb=false,bool dash=false){
            if(at<0||at>=MaxHorizon||(at==0&&c.forceRelease))return;
            const auto kind=modePolicy(m).input;
            bool edge=orb||kind==InputKind::GroundPulse||kind==InputKind::GroundEdge||kind==InputKind::AirEdge;
            for(int duration:{1,8,24,64}){
                if(edge&&!dash&&duration!=1)break;
                if(Clock::now()>=deadline)break;
                Plan child=p;child.length=std::min(MaxHorizon,std::max({c.horizon,p.length,
                    at+reactionTicks(c.tickSeconds,.18)+1}));child.landingDelay=-1;
                if(e.hasRealized)std::copy_n(e.realized.buttons.begin(),at,child.buttons.begin());
                bool down=edge||dash||kind==InputKind::JumpHold?true:!child.at(at);
                if((edge||kind==InputKind::JumpHold)&&at>0)child.buttons[at-1]=false;
                int end=std::min(child.length,at+std::max(1,int(std::lround((duration/240.)/c.tickSeconds))));
                std::fill(child.buttons.begin()+at,child.buttons.begin()+end,down);
                if(end<child.length&&(edge||dash||kind==InputKind::JumpHold))child.buttons[end]=false;
                add(child,10000+e.survived*8-at,true);
            }
        };
        std::vector<Node> deferred;
        while(!pending.empty()&&result.candidates<c.candidates){
            if(Clock::now()>=deadline){result.exhausted=true;break;}
            auto picked=std::max_element(pending.begin(),pending.end(),[&](int a,int b){return nodes[a].priority<nodes[b].priority;});
            // Keep geometric/mode seeds alive beside native repair branches.
            // A deep family of similar corrections must not consume all
            // evaluations before a different control rhythm is considered.
            if(result.candidates>=2&&(result.candidates%4)==3){
                auto seed=std::max_element(pending.begin(),pending.end(),[&](int a,int b){
                    return (nodes[a].repair?-1:nodes[a].priority)<(nodes[b].repair?-1:nodes[b].priority);});
                if(seed!=pending.end()&&!nodes[*seed].repair)picked=seed;
            }
            int id=*picked;pending.erase(picked);auto const node=nodes[id];auto const&p=node.plan;
            auto localDeadline=deadline;
            if(result.candidates==0&&!c.forceRelease){auto now=Clock::now();localDeadline=now+(deadline-now)/3;}
            auto e=evaluate(p,localDeadline);++result.candidates;result.repairsTested+=node.repair;
            if(e.interrupted&&!e.died&&!e.boundary)deferred.push_back({p,e.survived*8,true});
            ++result.firstActionCandidates[p.at(0)];e.routeId=routeFingerprint(e.hasRealized?e.realized:p);
            auto &alt=result.alternatives[p.at(0)];if(e.survived>alt.survived||alt.traces[0].empty())alt=e;
            auto proof=proveRoute(c,p,e);
            if(proof){
                auto const &actual=e.hasRealized?e.realized:p;
                int changes=0;bool previous=held;for(int t=0;t<proof.ticks;++t){changes+=actual.at(t)!=previous;previous=actual.at(t);}
                int continuity=0;for(int t=0;t<std::min({24,warm_.length,proof.ticks});++t)continuity+=actual.at(t)==warm_.at(t);
                bool full=proof.kind==ProofKind::Horizon||proof.kind==ProofKind::LandedRoute;
                // Progress/room first; launch count is only a tie breaker among
                // already safe routes, so it cannot hide a necessary gap jump.
                auto rank=std::tuple{full?2:1,std::min(proof.ticks,c.horizon),
                    int(std::clamp(std::min(e.clearance,e.nearClearance),0.f,120.f)*4),
                    c.cubePulsePlanning&&!c.immediateOrb?-e.cubeTakeoffs:0,-changes,continuity};
                if(full&&safe.size()<24){
                    safe.push_back({actual,e,proof,rank});
                    if(c.cubePulsePlanning&&!c.immediateOrb){
                        int launch=firstLaunch(actual);
                        for(int delta:{-1,1,-2,2,-4,4}){
                            int at=launch+delta;
                            if(launch<0||at<0||at>=actual.length||actual.at(at)||
                               (at>0&&actual.at(at-1))||(at+1<actual.length&&actual.at(at+1)))continue;
                            Plan child=actual;child.landingDelay=-1;child.buttons[launch]=false;child.buttons[at]=true;
                            add(child,100000-std::abs(delta),true);
                        }
                    }
                }
                if(rank>best){best=rank;result.hasValidatedInput=true;result.down=actual.at(0);
                    result.predictedTicks=e.survived;result.certificateTicks=proof.ticks;
                    result.completeContinuation=full;result.recedingCertificate=!full;
                    result.forecastDied=e.died;result.forecastInterrupted=e.interrupted;result.proofKind=int(proof.kind);
                    result.plan=actual;result.plan.length=proof.ticks;result.selected=e;
                    trimProof(result.selected,proof.ticks);
                    result.selected.died=result.selected.interrupted=result.selected.boundary=false;
                    result.selected.firstActionVerified=true;
                    if(proof.kind==ProofKind::GroundWait){result.selected.requiresLanding=result.selected.landingVerified=false;}
                    result.selected.realized=result.plan;result.selected.routeId=routeFingerprint(result.plan);
                    if(e.cacheResumeTicks==0)coldFallback=result;
                }
            }else{++result.rejectedUnsafe;result.bestRejectedTicks=std::max(result.bestRejectedTicks,e.survived);
                result.deaths+=e.died;result.guardDeaths+=e.guardDeath;result.nativeDeaths+=e.nativeDeath;
                result.landingRejects+=!e.firstActionVerified;result.interruptions+=e.interrupted;}
            if(!proof||proof.kind==ProofKind::GroundWait||proof.kind==ProofKind::ControlPrefix){
                for(auto o:e.transitions){
                    if(o.tick<1||o.tick>=MaxHorizon)continue;
                    Plan child=p;child.length=std::min(MaxHorizon,std::max(p.length,o.tick+reactionTicks(c.tickSeconds,.18)+1));
                    if(e.hasRealized)std::copy_n(e.realized.buttons.begin(),o.tick,child.buttons.begin());
                    child.landingDelay=-1;std::fill(child.buttons.begin()+o.tick,child.buttons.end(),false);
                    add(child,13000+e.survived*8,true);
                    auto input=modePolicy(o.mode).input;
                    if(input==InputKind::Thrust||input==InputKind::DashHold){std::fill(child.buttons.begin()+o.tick,child.buttons.end(),true);add(child,13001+e.survived*8,true);}
                    else repair(child,e,o.tick,o.mode);
                }
                for(auto it=e.orbActions.rbegin();it!=e.orbActions.rend();++it){bool dash=std::find(e.dashActions.begin(),e.dashActions.end(),*it)!=e.dashActions.end();
                    repair(p,e,*it,c.plannerMode,true,dash);if(Clock::now()>=deadline)break;}
                // A loss of support needs branches BEFORE the edge, including
                // a pit that has no solid wall or ordinary spike in front.
                int failure=e.supportLossTick>=0?e.supportLossTick:e.survived;
                std::vector<PlannerMode> modes{c.plannerMode};
                if(c.dual)modes.push_back(c.secondaryMode);
                for(auto o:e.controlActions)if(std::find(modes.begin(),modes.end(),o.mode)==modes.end())modes.push_back(o.mode);
                for(int lead:{24,48,12,64,96,8}){
                    int target=failure-reactionTicks(c.tickSeconds,lead/240.);
                    for(auto m:modes){
                        if(m==PlannerMode::Generic)continue;
                        auto it=std::find_if(e.controlActions.rbegin(),e.controlActions.rend(),[&](auto o){return o.mode==m&&o.tick<=target;});
                        if(it!=e.controlActions.rend())repair(p,e,it->tick,m);
                    }
                }
                if(c.cubePulsePlanning)for(int lead:{24,48,12,64,8,0}){
                    auto it=std::upper_bound(e.groundedActions.begin(),e.groundedActions.end(),failure-lead);
                    if(it!=e.groundedActions.begin())repair(p,e,*--it,PlannerMode::Cube);
                }
            }
            // Only a full, unchanged-input, obstacle-free coast may finish
            // before refinements; required event chains continue branching.
            if(proof.kind==ProofKind::Horizon&&result.down==held&&e.cubeTakeoffs==0&&
               e.transitions.empty()&&e.orbActions.empty()&&c.jumpEventCount==0&&c.orbEventCount==0&&
               (c.forceRelease||(result.firstActionCandidates[0]&&result.firstActionCandidates[1])))break;
        }
        result.exhausted|=!pending.empty()&&result.candidates>=c.candidates;
        if(c.cubePulsePlanning&&!c.immediateOrb&&!safe.empty()){
            std::vector<Plan> checked;for(auto const&s:safe)checked.push_back(s.plan);
            auto chosen=std::max_element(safe.begin(),safe.end(),[&](auto const&a,auto const&b){
                return std::tuple{std::get<0>(a.rank),std::get<1>(a.rank),std::get<2>(a.rank)/16,
                           firstLaunch(a.plan)<0,launchWindowMargin(a.plan,checked),a.rank}<
                       std::tuple{std::get<0>(b.rank),std::get<1>(b.rank),std::get<2>(b.rank)/16,
                           firstLaunch(b.plan)<0,launchWindowMargin(b.plan,checked),b.rank};
            });
            if(chosen!=safe.end()){
                auto const&e=chosen->evaluation;auto proof=chosen->proof;
                result.hasValidatedInput=true;result.down=chosen->plan.at(0);result.plan=chosen->plan;
                result.plan.length=proof.ticks;result.certificateTicks=proof.ticks;result.predictedTicks=e.survived;
                result.completeContinuation=true;result.recedingCertificate=false;result.proofKind=int(proof.kind);
                result.selected=e;trimProof(result.selected,proof.ticks);
                result.selected.died=result.selected.interrupted=result.selected.boundary=false;
                result.selected.realized=result.plan;result.selected.routeId=routeFingerprint(result.plan);
                result.forecastDied=e.died;result.forecastInterrupted=e.interrupted;
                if(e.cacheResumeTicks==0)coldFallback=result;
            }
        }
        if constexpr(requires(Plan p){evaluate(p,confirmationDeadline,false);}){
            if(result.hasValidatedInput&&result.selected.cacheResumeTicks>0){
                auto cold=evaluate(result.plan,confirmationDeadline,false);
                result.coldVerificationTicks=cold.survived;
                auto proof=proveRoute(c,result.plan,cold);
                auto checked=cold;
                if(proof&&proof.ticks>=result.certificateTicks){
                    trimProof(checked,result.certificateTicks);
                    checked.died=checked.interrupted=checked.boundary=false;
                    checked.firstActionVerified=true;
                    if(proof.kind==ProofKind::GroundWait)
                        checked.requiresLanding=checked.landingVerified=false;
                }
                if(proof&&proof.ticks>=result.certificateTicks&&
                   sameTimeline(result.selected,checked,result.certificateTicks,c.dual)){
                    result.selected=std::move(checked);
                    result.selected.realized=result.plan;result.selected.routeId=routeFingerprint(result.plan);
                }else{
                    deferred.push_back({result.plan,100000,true});
                    ++result.cacheMismatches;
                    int mismatches=result.cacheMismatches,checked=result.coldVerificationTicks;
                    retainSearchDiagnostics(coldFallback,result);result=std::move(coldFallback);
                    result.cacheMismatches=mismatches;result.coldVerificationTicks=checked;
                }
            }
        }
        for(int id:pending)deferred.push_back({nodes[id].plan,std::min(nodes[id].priority,80000),nodes[id].repair});
        std::stable_sort(deferred.begin(),deferred.end(),[](auto const&a,auto const&b){return a.priority>b.priority;});
        for(auto const&node:deferred){
            if(future_.size()>=128)break;
            bool duplicate=false;for(auto const&old:future_)duplicate|=old.plan.buttons==node.plan.buttons&&old.plan.landingDelay==node.plan.landingDelay;
            if(!duplicate)future_.push_back(node);
        }
        for(auto const&n:nodes)result.proposals.push_back(n.plan);
        if(result.hasValidatedInput)seedVerifiedPrefix(result.plan,result.certificateTicks);
        result.elapsedMs=std::chrono::duration<double,std::milli>(Clock::now()-start).count();return result;
    }
};
}

