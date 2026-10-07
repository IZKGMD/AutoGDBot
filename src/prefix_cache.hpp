#pragma once
#include "controller.hpp"
#include <memory>

namespace agb {
// Cache is scoped to ONE live command: no state from yesterday's world can
// certify today's action. Keys compare actual requested input, not a hash or
// quantized motion state; different ring/landing histories cannot coalesce.
template<class State, size_t Capacity=32>
class PrefixCache {
    struct Entry { Plan request; int tick; State state; };
    std::vector<std::unique_ptr<Entry>> entries_;
    size_t next_=0;
  public:
    int hits=0, skippedTicks=0;
    void reset(){entries_.clear();next_=0;hits=skippedTicks=0;}
    void save(Plan const&p,int tick,State state){
        if(tick<=0||tick>p.length||tick>MaxHorizon)return;
        auto e=std::make_unique<Entry>(p,tick,std::move(state));
        if(entries_.size()<Capacity)entries_.push_back(std::move(e));
        else {entries_[next_]=std::move(e);next_=(next_+1)%Capacity;}
    }
    State const *find(Plan const&p,int &tick){
        Entry const *best=nullptr;
        for(auto const&entry:entries_){
            auto const&e=*entry;
            if(e.tick>p.length||e.request.landingDelay!=p.landingDelay||
               (best&&e.tick<=best->tick))continue;
            if(std::equal(p.buttons.begin(),p.buttons.begin()+e.tick,e.request.buttons.begin()))best=&e;
        }
        tick=best?best->tick:0;
        if(best){++hits;skippedTicks+=tick;return &best->state;}
        return nullptr;
    }
    size_t size()const{return entries_.size();}
};
}
