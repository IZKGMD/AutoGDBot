#pragma once
#include "controller.hpp"
#include <span>
namespace agb {
inline int firstLaunch(Plan const&p){
    for(int t=0;t<p.length;++t)if(p.at(t)&&(t==0||!p.at(t-1)))return t;
    return -1;
}
inline bool sameOtherInputs(Plan const&a,Plan const&b){
    const int x=firstLaunch(a),y=firstLaunch(b);
    if(x<0||y<0||a.landingDelay>=0||b.landingDelay>=0)return false;
    if(std::abs(a.length-b.length)>8)return false;
    for(int t=0;t<std::max(a.length,b.length);++t){
        if(t==x||t==y)continue;
        if((t<a.length&&a.at(t))!=(t<b.length&&b.at(t)))return false;
    }
    return true;
}
// A timing margin exists only when each adjacent timing was actually checked.
// Two isolated successful samples must not certify the interval between them.
inline int launchWindowMargin(Plan const&p,std::span<Plan const> checked){
    int at=firstLaunch(p);if(at<0)return 0;
    auto has=[&](int tick){return std::any_of(checked.begin(),checked.end(),[&](auto const&q){
        return firstLaunch(q)==tick&&sameOtherInputs(p,q);
    });};
    int margin=0;
    while(margin<8&&at-margin-1>=0&&has(at-margin-1)&&has(at+margin+1))++margin;
    return margin;
}
}
