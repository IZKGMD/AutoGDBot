#pragma once
#include "controller.hpp"
namespace agb {
// Observe where external travel happens relative to the first native routine.
// Four agreeing normal-frame observations are required before changing order.
class TravelPhase {
    float root_=0,first_=0,updateBefore_=0,updateDelta_=0,expected_=0;
    bool active_=false,seen_=false,updated_=false,candidate_=false;
    int agreements_=0;
  public:
    bool beforeNative=false,measured=false;
    void reset(){*this={};}
    void begin(float root,float expected){root_=root;expected_=expected;active_=true;seen_=updated_=false;}
    void observe(float x){if(active_&&!seen_){first_=x;seen_=true;}}
    void beginUpdate(float x){observe(x);updateBefore_=x;}
    void endUpdate(float x){if(active_){updateDelta_=x-updateBefore_;updated_=true;}}
    bool finish(){
        active_=false;
        if(!seen_||!updated_||!std::isfinite(expected_)||std::abs(expected_)<.1f)return false;
        const float tolerance=std::max(.05f,std::abs(expected_)*.08f);
        const bool nativeMoves=std::abs(updateDelta_)>tolerance;
        const bool early=std::abs(first_-root_-expected_)<=tolerance&&!nativeMoves;
        const bool late=std::abs(first_-root_)<=tolerance;
        if(!early&&!late){agreements_=0;return false;}
        if(agreements_&&candidate_==early)++agreements_;else{candidate_=early;agreements_=1;}
        if(agreements_<4)return false;
        const bool changed=beforeNative!=early;
        beforeNative=early;measured=true;return changed;
    }
};
}
