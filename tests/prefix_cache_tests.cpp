#include "prefix_cache.hpp"
#include "interaction_state.hpp"
#include <iostream>
#include <stdexcept>
using namespace agb;
static void check(bool b,char const*s){if(!b)throw std::runtime_error(s);}
struct State{double y=0,velocity=0;uint64_t rings=0,pads=0;ModeState mode;int pulse=0,landing=0;};
int main(){
    PrefixCache<State,3> cache;Plan a;a.length=100;a.buttons[0]=true;State s;s.y=42;s.rings=17;
    s.mode.mode=PlannerMode::Ship;s.pulse=1;s.landing=3;cache.save(a,32,s);
    Plan b=a;b.buttons[40]=true;int at=0;auto *found=cache.find(b,at);
    check(found&&at==32&&found->rings==17&&found->mode.mode==PlannerMode::Ship&&found->pulse==1,
          "same requested prefix retains exact motion, interaction, mode and pulse state");
    b.buttons[15]=true;check(!cache.find(b,at),"a different past request cannot borrow another lineage");
    b=a;b.landingDelay=0;check(!cache.find(b,at),"different touchdown policy is a different native input history");
    b=a;b.length=16;check(!cache.find(b,at),"no resume beyond the active requested interval");
    cache.save(a,48,s);cache.save(a,64,s);cache.save(a,80,s);check(cache.size()==3,"snapshot pool remains bounded");
    b=a;found=cache.find(b,at);check(found&&at==80,"longest identical prefix avoids repeated native steps");
    check(cache.skippedTicks>=112&&cache.hits>=2,"cache savings are counted rather than claimed from wall time");
    cache.reset();check(!cache.find(a,at)&&cache.hits==0,"a new live world clears all cached state");
    // Independent numerical integration: resume a branch must equal replay
    // from the root without rounding the cached position/velocity.
    auto step=[](State &x,bool down){x.velocity+=(down?.12:-.16);x.y+=x.velocity*.25;};
    a={};a.length=80;a.buttons.fill(false);s={};
    for(int t=0;t<32;++t)step(s,a.at(t));cache.save(a,32,s);
    b=a;for(int t=40;t<55;++t)b.buttons[t]=true;found=cache.find(b,at);State resumed=*found,full{};
    for(int t=at;t<80;++t)step(resumed,b.at(t));for(int t=0;t<80;++t)step(full,b.at(t));
    check(resumed.y==full.y&&resumed.velocity==full.velocity,"shared-prefix integration remains bit exact");
    std::cout<<"PASS: bounded prefix cache, exact lineage keys, policy isolation and cold/resumed numerical agreement\n";
}
