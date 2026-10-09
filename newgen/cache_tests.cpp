#include "cache_identity.hpp"
#include <cassert>
#include <iostream>
#include <limits>
int main(){
    assert(autogd::sha256("")=="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    assert(autogd::sha256("abc")=="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assert(autogd::sha256(std::string(1000000,'a'))=="cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0");
    const auto first=autogd::levelKey("kA22,0;1,1,2,100;",0,2,"android64:engine-v1");
    assert(first.size()==64&&first==autogd::levelKey("kA22,0;1,1,2,100;",0,2,"android64:engine-v1"));
    assert(first!=autogd::levelKey("kA22,0;1,1,2,101;",0,2,"android64:engine-v1"));
    assert(first!=autogd::levelKey("kA22,0;1,1,2,100;",0,2,"windows:engine-v1"));
    assert(first!=autogd::levelKey("kA22,0;1,1,2,100;",0,2,"android64:engine-v2"));
    assert(first!=autogd::levelKey("kA22,0;1,1,2,100;",1,2,"android64:engine-v1"));
    assert(autogd::levelKey("",0,2,"android64:engine-v1").empty());
    assert(autogd::frameBudget(6)==6&&autogd::frameBudget(-1)==2&&autogd::frameBudget(100)==12);
    assert(autogd::frameBudget(std::numeric_limits<double>::quiet_NaN())==6);
    std::cout<<"cache identity and mobile frame budget: PASS\n";
}
