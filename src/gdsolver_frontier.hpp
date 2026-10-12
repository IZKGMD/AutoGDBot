#pragma once
#include "controller.hpp"
#include <bit>
#include <numeric>

// Adapted from gdsolver/gdsolver, dp/src/dp/search_dp.hpp (Slots, CellMap,
// hi/lo representatives), commit 2cddf0e25fca53105e9851d20d44f9e5e82d8314.
// Copyright (c) 2026 The gdsolver Authors. MIT; see third_party/gdsolver-LICENSE.
// Native GD rollouts replace the upstream Windows approximate dynamics.
namespace agb::gdsolver {
struct CellKey {
    std::array<uint64_t, 48> fields{};
    bool operator==(CellKey const &) const = default;
    uint64_t hash() const {
        uint64_t h = 1469598103934665603ull;
        for (auto v : fields) { h ^= v; h *= 1099511628211ull; }
        return h;
    }
};
struct Slots { int hi = -1, lo = -1; };
// Upstream's allocation-free open-addressing map, with full-key equality,
// explicit occupancy (zero is valid), and growth before the table can fill.
class CellMap {
    std::vector<CellKey> keys_;
    std::vector<Slots> vals_;
    std::vector<uint8_t> occupied_;
    std::vector<size_t> touched_;
    size_t mask_ = 0;
    void grow(size_t n) {
        auto keys = std::move(keys_); auto vals = std::move(vals_);
        auto used = std::move(touched_);
        keys_.resize(n); vals_.resize(n); occupied_.assign(n, 0);
        mask_ = n - 1; touched_.clear();
        for (auto i : used) at(keys[i]) = vals[i];
    }
  public:
    void ensure(size_t want) {
        size_t n = 64;
        while (n < want * 4) n <<= 1;
        if (n > keys_.size()) grow(n);
    }
    void clear() {
        for (auto i : touched_) occupied_[i] = 0;
        touched_.clear();
    }
    size_t size() const { return touched_.size(); }
    Slots &at(CellKey const &key) {
        if ((touched_.size() + 1) * 4 >= keys_.size()) grow(keys_.empty() ? 64 : keys_.size() * 2);
        size_t i = size_t(key.hash() * 0x9E3779B97F4A7C15ull) & mask_;
        while (occupied_[i]) {
            if (keys_[i] == key) return vals_[i];
            i = (i + 1) & mask_;
        }
        occupied_[i] = 1; keys_[i] = key; vals_[i] = {};
        touched_.push_back(i); return vals_[i];
    }
};
inline uint64_t quantize(double value, double multiplier) {
    if (!std::isfinite(value) || std::abs(value * multiplier) >= 9e18)
        return std::bit_cast<uint64_t>(value);
    return uint64_t(int64_t(std::floor(value * multiplier)));
}
inline CellKey keyOf(Plan const &p, Evaluation const &e) {
    CellKey k; k.fields[0] = uint64_t(p.length); k.fields[1] = p.at(0);
    k.fields[2] = uint64_t(p.landingDelay + 1); k.fields[3] = p.stopAtLanding;
    k.fields[4] = e.requiresLanding | (uint64_t(e.landingVerified) << 1);
    k.fields[5] = e.portalVerified;
    // Without a complete endpoint sample, do not merge unknown physical states.
    for (int player = 0; player < 2; ++player) {
        auto const &tr = e.traces[player]; auto const &ms = e.motions[player];
        auto const &modes = e.modeTraces[player]; const size_t o = 8 + player * 20;
        if (tr.empty()) { k.fields[o] = ~uint64_t(0); continue; }
        if (ms.empty() || modes.empty()) { k.fields[6] = routeFingerprint(p); continue; }
        auto const &m = ms.back(); auto const &mode = modes.back();
        bool flight = modePolicy(mode.controlMode()).input == InputKind::Thrust;
        // Upstream's 0.25px x phase and 0.5px/0.1vy ground cells. Native
        // flight cells are sharpened to 0.5px/0.1vy near tight geometry.
        bool tight = e.endpointClearance < 12.f;
        k.fields[o] = quantize(tr.back().x, 4);
        k.fields[o+1] = quantize(tr.back().y, flight && !tight ? .5 : 2);
        k.fields[o+2] = quantize(m.velocity, flight && !tight ? 2.5 : 10);
        k.fields[o+3] = uint64_t(mode.mode) | (uint64_t(mode.flags) << 8) |
            (uint64_t(mode.mini) << 16) | (uint64_t(mode.upsideDown) << 17) |
            (uint64_t(mode.sideways) << 18) | (uint64_t(mode.goingLeft) << 19) | (uint64_t(mode.dash) << 20);
        k.fields[o+4] = uint64_t(mode.speed);
        k.fields[o+5] = m.canJump | (uint64_t(m.grounded) << 1) | (uint64_t(m.buffered) << 2) |
            (uint64_t(m.ringUnused) << 3) | (uint64_t(m.nativeDetails) << 4);
        k.fields[o+6] = m.usedInteractions;
        k.fields[o+7] = quantize(m.xVelocity, 1000);
        k.fields[o+8] = std::bit_cast<uint64_t>(m.gravity);
        k.fields[o+9] = std::bit_cast<uint64_t>(m.gravityMod);
        k.fields[o+10] = quantize(m.rotation, 10);
        k.fields[o+11] = std::bit_cast<uint32_t>(m.force.x);
        k.fields[o+12] = std::bit_cast<uint32_t>(m.force.y);
        k.fields[o+13] = m.contactFlags;
        k.fields[o+14] = p.at(std::max(0, p.length - 1));
    }
    return k;
}
class NativeFrontier {
    CellMap cells_;
  public:
    int cells = 0, merged = 0;
    template<class Node, class Rank>
    void prune(std::vector<Node> &nodes, Rank rank, size_t cap = 12) {
        cells_.clear(); cells_.ensure(nodes.size());
        auto velocity = [&](int i) {
            auto const &m = nodes[size_t(i)].state.motions[0];
            return m.empty() ? 0. : m.back().velocity;
        };
        // Keep both velocity extremes in a cell, as GDSolver does. Search
        // quantization limits memory only; it never certifies a command.
        for (size_t i = 0; i < nodes.size(); ++i) {
            auto &s = cells_.at(keyOf(nodes[i].plan, nodes[i].state));
            if (s.hi < 0) { s.hi = s.lo = int(i); continue; }
            ++merged;
            if (velocity(int(i)) > velocity(s.hi) + 1e-9) s.hi = int(i);
            else if (velocity(int(i)) < velocity(s.lo) - 1e-9) s.lo = int(i);
            else if (rank(nodes[i]) > rank(nodes[size_t(s.hi)])) s.hi = int(i);
        }
        cells += int(cells_.size());
        std::vector<size_t> order(nodes.size()); std::iota(order.begin(), order.end(), 0);
        std::stable_sort(order.begin(), order.end(), [&](size_t a, size_t b) { return rank(nodes[a]) > rank(nodes[b]); });
        std::vector<size_t> representatives;
        for (auto i : order) {
            auto const &s = cells_.at(keyOf(nodes[i].plan, nodes[i].state));
            if (s.hi == int(i) || s.lo == int(i)) representatives.push_back(i);
        }
        std::vector<size_t> chosen; std::array<int,2> roots{};
        // Reserve two per immediate action, then round-robin spatial cells:
        // one roomy region cannot evict all climb/dive/alternate-height states.
        for (auto i : representatives) if (roots[nodes[i].plan.at(0)] < 2 && chosen.size() < cap) {
            ++roots[nodes[i].plan.at(0)]; chosen.push_back(i);
        }
        std::vector<CellKey> selectedCells;
        for (auto i : chosen) selectedCells.push_back(keyOf(nodes[i].plan, nodes[i].state));
        for (auto i : representatives) {
            auto key = keyOf(nodes[i].plan, nodes[i].state);
            if (chosen.size() >= cap) break;
            if (std::find(selectedCells.begin(),selectedCells.end(),key)==selectedCells.end()) {
                chosen.push_back(i); selectedCells.push_back(key);
            }
        }
        for (auto i : representatives) {
            if (chosen.size() >= cap) break;
            if (std::find(chosen.begin(),chosen.end(),i)==chosen.end()) chosen.push_back(i);
        }
        std::vector<Node> kept; kept.reserve(chosen.size());
        for (auto i : chosen) kept.push_back(std::move(nodes[i]));
        nodes = std::move(kept);
    }
};

// Only input proposals survive between live commands. No old clone, scene,
// collision result or certificate is reused across roots. Every proposal is
// replayed against the live world before it can enter the verified queue.
class ContinuationBank {
    struct Entry { Plan plan; int reached = 0; };
    std::vector<Entry> plans_;
    size_t cursor_ = 0;
    static bool equal(Plan const &a, Plan const &b) {
        return a.length == b.length && a.landingDelay == b.landingDelay &&
            a.stopAtLanding == b.stopAtLanding &&
            std::equal(a.buttons.begin(),a.buttons.begin()+a.length,b.buttons.begin());
    }
  public:
    void reset() { plans_.clear(); cursor_ = 0; }
    size_t size() const { return plans_.size(); }
    std::vector<Plan> take(size_t count=4) {
        std::vector<Plan> out;
        if (plans_.empty()) return out;
        count=std::min(count,plans_.size());
        for(size_t i=0;i<count;++i) out.push_back(plans_[(cursor_+i)%plans_.size()].plan);
        cursor_=(cursor_+count)%plans_.size(); return out;
    }
    void remember(Plan p, int reached) {
        if(reached < 4 || p.length < 4 || p.landingDelay >= 0 || p.stopAtLanding) return;
        for(auto &e:plans_) if(equal(e.plan,p)) { e.reached=std::max(e.reached,reached); return; }
        plans_.push_back({p,reached});
        if(plans_.size()<=16) return;
        // Keep equal capacity for UP/DOWN proposals until the actual live
        // command is known. Then discard incompatible lineages in advance.
        std::stable_sort(plans_.begin(),plans_.end(),[](auto const &a,auto const &b){return a.reached>b.reached;});
        std::array<int,2> counts{};
        std::erase_if(plans_,[&](auto const &e){return ++counts[e.plan.at(0)]>8;});
    }
    void advance(int ticks, bool applied) {
        ticks=std::max(0,ticks);
        if(!ticks) return;
        std::erase_if(plans_,[&](auto const &e){return e.plan.length<=ticks || e.plan.at(0)!=applied;});
        for(auto &e:plans_) {
            int n=e.plan.length-ticks;
            for(int i=0;i<n;++i)e.plan.buttons[i]=e.plan.buttons[i+ticks];
            e.plan.length=n; e.reached=std::max(0,e.reached-ticks);
            std::fill(e.plan.buttons.begin()+n,e.plan.buttons.end(),false);
        }
        if(!plans_.empty())cursor_%=plans_.size(); else cursor_=0;
    }
};
} // namespace agb::gdsolver
