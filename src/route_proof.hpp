#pragma once
#include "controller.hpp"

namespace agb {
enum class ProofKind { None, GroundWait, ControlPrefix, LandedRoute, Horizon };
struct RouteProof { ProofKind kind=ProofKind::None; int ticks=0; explicit operator bool()const{return ticks>0;} };
inline int reactionTicks(double dt,double seconds=.12){
    return std::clamp(int(std::ceil(seconds/std::clamp(dt,.001,1./30))),4,MaxHorizon);
}
inline bool traceCovers(Evaluation const&e,int n,bool dual){
    if(n<=0||n>e.survived)return false;
    for(int i=0;i<(dual?2:1);++i){
        if(e.traces[i].size()<=size_t(n))return false;
        for(int t=0;t<=n;++t)if(!std::isfinite(e.traces[i][t].x)||!std::isfinite(e.traces[i][t].y))return false;
    }
    return true;
}
// A landing is not a horizon endpoint: the part after it must also be run.
inline bool routeWindowFinished(int completed,int length,bool settled,bool portalSafe){
    return completed>=length&&settled&&portalSafe;
}
inline RouteProof proveRoute(Config const&c,Plan const&p,Evaluation const&e){
    const bool down=e.hasRealized?e.realized.at(0):p.at(0);
    const bool landingMode=modePolicy(c.plannerMode).mustLand ||
        (c.dual&&modePolicy(c.secondaryMode).mustLand);
    const int minimum=reactionTicks(c.tickSeconds,.10);
    const bool portals=e.transitions.empty()||e.portalVerified;
    if(!e.died&&!e.interrupted&&!e.boundary&&e.firstActionVerified&&portals&&
       e.survived>=p.length&&(!e.requiresLanding||e.landingVerified)&&traceCovers(e,e.survived,c.dual))
        return {e.requiresLanding?ProofKind::LandedRoute:ProofKind::Horizon,
                std::min(e.survived,MaxHorizon)};
    // Waiting is proved by the native contiguous grounded segment, even when
    // its uncommitted tail later falls. Reserve enough ground for takeoff;
    // never authorize the whole fatal/unfinished tail as a queued coast.
    if(landingMode&&!down){
        int n=std::min(e.groundedPrefixTicks,e.survived);
        if(e.supportLossTick>=0)n=std::min(n,std::max(0,e.supportLossTick-reactionTicks(c.tickSeconds)));
        if(e.died)n=std::min(n,std::max(0,e.survived-reactionTicks(c.tickSeconds)));
        if(e.boundary)n=std::min(n,std::max(0,e.survived-1));
        if(n>=minimum&&traceCovers(e,n,c.dual))return {ProofKind::GroundWait,n};
    }
    // Responsive modes can commit a short checked control segment. A newly
    // launched cube/robot cannot: its unsteerable flight must be proved first.
    if(!landingMode&&e.firstActionVerified&&portals){
        int n=std::min(e.survived,reactionTicks(c.tickSeconds,.18));
        if(e.boundary)n=std::min(n,std::max(0,e.survived-1));
        if(n>=minimum&&traceCovers(e,n,c.dual))return {ProofKind::ControlPrefix,n};
    }
    return {};
}
inline void trimProof(Evaluation &e,int n){
    e.survived=std::min(e.survived,n);n=e.survived;
    auto trim=[&](auto &sets){for(auto &v:sets)if(v.size()>size_t(n+1))v.resize(n+1);};
    trim(e.traces);trim(e.motions);trim(e.modeTraces);
    for(auto &v:e.discontinuities)std::erase_if(v,[&](int t){return t>n;});
    for(auto *v:{&e.groundedActions,&e.orbActions,&e.dashActions})std::erase_if(*v,[&](int t){return t>=n;});
    for(auto *v:{&e.transitions,&e.controlActions})std::erase_if(*v,[&](auto o){return o.tick>=n;});
    e.groundedPrefixTicks=std::min(e.groundedPrefixTicks,n);e.realized.length=n;
    std::fill(e.realized.buttons.begin()+n,e.realized.buttons.end(),false);
    e.routeId=routeFingerprint(e.realized);
}
inline bool sameTimeline(Evaluation const&a,Evaluation const&b,int n,bool dual){
    if(b.died||b.interrupted||!traceCovers(a,n,dual)||!traceCovers(b,n,dual))return false;
    for(int i=0;i<(dual?2:1);++i)for(int t=0;t<=n;++t){
        auto p=a.traces[i][t],q=b.traces[i][t];if(std::hypot(p.x-q.x,p.y-q.y)>.05f)return false;
        if(!a.modeTraces[i].empty()&&(b.modeTraces[i].size()<=size_t(t)||a.modeTraces[i][t]!=b.modeTraces[i][t]))return false;
        if(!a.motions[i].empty()&&(b.motions[i].size()<=size_t(t)||!a.motions[i][t].matches(b.motions[i][t])))return false;
    }
    if(a.hasRealized&&b.hasRealized)for(int t=0;t<n;++t)if(a.realized.at(t)!=b.realized.at(t))return false;
    return true;
}
}
