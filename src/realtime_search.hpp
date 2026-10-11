#pragma once
#include "route_proof.hpp"
#include <tuple>

namespace agb {
// Receding-horizon beam search. The oracle extends exact native prefixes;
// neither the search nor its warm start is a precomputed level solution.
class RealtimeSearch {
    Plan warm_;
    struct Node { Plan plan; Evaluation state; };
    static bool alive(Node const &n) {
        return !n.state.died && !n.state.interrupted && !n.state.boundary &&
            n.state.survived == n.plan.length;
    }
    static auto rank(Node const &n) {
        return std::tuple{n.state.survived,
            int(std::clamp(std::min(n.state.clearance, n.state.endpointClearance), 0.f, 120.f) * 8),
            -n.state.buttonChanges, -n.state.cubeTakeoffs};
    }
    static void prune(std::vector<Node> &nodes) {
        std::stable_sort(nodes.begin(), nodes.end(), [](auto const &a, auto const &b) {
            return rank(a) > rank(b);
        });
        // Preserve both possible live commands. A roomy but doomed UP state
        // must not evict all DOWN states before the obstacle enters the window.
        std::array<int, 2> counts{};
        std::vector<Node> kept;
        for (auto &n : nodes) {
            if (counts[n.plan.at(0)]++ < 2) kept.push_back(std::move(n));
        }
        nodes = std::move(kept);
    }
    static PlannerMode endMode(Node const &n, Config const &c, int player = 0) {
        auto const &v = n.state.modeTraces[player];
        return v.empty() ? (player ? c.secondaryMode : c.plannerMode) : v.back().controlMode();
    }
    static bool mayPress(Node const &n, Config const &c) {
        for (int i = 0; i < (c.dual ? 2 : 1); ++i) {
            auto const &v = n.state.motions[i];
            bool grounded = !v.empty() && (v.back().canJump || v.back().grounded);
            bool orb = std::any_of(n.state.orbActions.begin(), n.state.orbActions.end(),
                [&](int t) { return t >= n.plan.length - 2; });
            if (controlOpportunity(endMode(n, c, i), grounded, orb)) return true;
        }
        return false;
    }
    static InputKind endKind(Node const &n, Config const &c) {
        auto primary = modePolicy(endMode(n, c)).input;
        if (!c.dual) return primary;
        auto secondary = modePolicy(endMode(n, c, 1)).input;
        // The shared dual button must retain a flying player's hold even
        // when player one is a cube. Both bodies are checked by the oracle.
        for (auto k : {InputKind::Thrust, InputKind::DashHold, InputKind::JumpHold, InputKind::AirEdge})
            if (primary == k || secondary == k) return k;
        return primary;
    }
    static InputKind kindAt(Evaluation const &e, Config const &c, int tick) {
        auto input = [&](int player) {
            auto mode = player ? c.secondaryMode : c.plannerMode;
            if (e.modeTraces[player].size() > size_t(tick)) mode = e.modeTraces[player][tick].controlMode();
            return modePolicy(mode).input;
        };
        auto primary = input(0); if (!c.dual) return primary;
        auto secondary = input(1);
        for (auto k : {InputKind::Thrust, InputKind::DashHold, InputKind::JumpHold, InputKind::AirEdge})
            if (primary == k || secondary == k) return k;
        return primary;
    }
  public:
    void reset() { warm_ = {}; }
    void advance(int ticks = 1) {
        ticks = std::clamp(ticks, 0, warm_.length);
        for (int t = 0; t < warm_.length - ticks; ++t) warm_.buttons[t] = warm_.buttons[t + ticks];
        warm_.length -= ticks;
        std::fill(warm_.buttons.begin() + warm_.length, warm_.buttons.end(), false);
    }
    void seedVerifiedPrefix(Plan const &p, int ticks) {
        warm_ = p; warm_.length = std::clamp(ticks, 0, p.length);
        std::fill(warm_.buttons.begin() + warm_.length, warm_.buttons.end(), false);
    }
    template<class Oracle> Decision decide(Config c, bool held, Oracle &&oracle) {
        c.horizon = std::clamp(c.horizon, 24, MaxPlanningHorizon);
        c.candidates = std::clamp(c.candidates, 4, MaxCandidates);
        auto start = Clock::now();
        auto end = std::min(c.sharedDeadline, start + std::chrono::duration_cast<Clock::duration>(
            std::chrono::duration<double, std::milli>(std::clamp(c.budgetMs, .2, 8.))));
        auto searchEnd = start + (end - start) * (c.priorityControl ? 4 : 2) / (c.priorityControl ? 5 : 3);
        Decision result; result.down = c.forceRelease ? false : held;
        if (start >= end) { result.exhausted = true; return result; }
        const int slice = std::clamp(int(std::lround(.05 / c.tickSeconds)), 4, 16);
        const int minimum = reactionTicks(c.tickSeconds, .10);
        std::vector<Node> frontier;
        std::vector<Plan> seen;
        Node bestProof, bestRecovery;
        RouteProof bestCertificate;
        Node checkedControl;
        RouteProof checkedControlProof;
        auto checkedControlRank = std::tuple{-1, -1, -100000, -100000, -1};
        auto bestProofRank = std::tuple{-1, -1, -1, -1, -100000, -100000};
        auto inspect = [&](Node const &n) {
            auto const &e = n.state;
            auto proof = proveRoute(c, n.plan, e);
            // A short prefix cannot be promoted to a full horizon simply
            // because all of that short request was evaluated.
            if (proof && n.plan.length < c.horizon && proof.kind == ProofKind::Horizon) {
                proof = n.plan.at(0) || e.groundedPrefixTicks < minimum
                    ? RouteProof{} : RouteProof{ProofKind::GroundWait, e.groundedPrefixTicks};
                if (!modePolicy(c.plannerMode).mustLand && e.survived >= minimum)
                    proof = {ProofKind::ControlPrefix, std::min(e.survived, reactionTicks(c.tickSeconds, .18))};
            }
            if (proof.kind != ProofKind::Horizon && proof.kind != ProofKind::LandedRoute && proof.ticks < minimum) proof = {};
            if (proof) {
                bool full = proof.kind == ProofKind::Horizon || proof.kind == ProofKind::LandedRoute;
                // Compare complete futures before committing a short control
                // prefix. A short safe wait must not beat a path around the
                // upcoming obstacle. Clearance is measured along the flight,
                // not only at its roomy endpoint.
                float margin = e.requiresLanding ? e.landingMargin : e.clearance;
                auto score = std::tuple{full ? 2 : 1, std::min(e.survived, c.horizon),
                    int(std::clamp(margin, 0.f, 120.f) / 4),
                    int(std::clamp(e.endpointClearance, 0.f, 120.f) * 2),
                    -e.buttonChanges, -int(n.plan.at(0) != held)};
                if (score > bestProofRank) {
                    bestProofRank = score; bestProof = n; bestCertificate = proof;
                }
            }
            if (e.survived > 0 && traceCovers(e, e.survived, c.dual) &&
                (bestRecovery.state.survived == 0 || rank(n) > rank(bestRecovery))) bestRecovery = n;
        };
        auto run = [&](Plan p, bool repair = false, bool memo = true) -> Node {
            p.length = std::clamp(p.length, 1, MaxHorizon);
            if (c.forceRelease) p.buttons[0] = false;
            Node n; n.plan = p;
            if (Clock::now() >= searchEnd || result.candidates >= c.candidates) return n;
            for (auto const &s : seen)
                if (s.length == p.length && s.landingDelay == p.landingDelay && s.stopAtLanding == p.stopAtLanding && std::equal(s.buttons.begin(), s.buttons.begin() + p.length,
                                                     p.buttons.begin())) return n;
            seen.push_back(p);
            n.state = oracle.prefix(p, searchEnd, memo);
            if (!memo) result.coldVerificationTicks += n.state.survived;
            ++result.candidates; result.repairsTested += repair;
            ++result.firstActionCandidates[p.at(0)];
            result.deaths += n.state.died; result.guardDeaths += n.state.guardDeath;
            result.nativeDeaths += n.state.nativeDeath; result.interruptions += n.state.interrupted;
            if (!alive(n)) { ++result.rejectedUnsafe; result.bestRejectedTicks = std::max(result.bestRejectedTicks, n.state.survived); }
            if (n.state.hasRealized) n.plan = n.state.realized;
            // A death leaves only a checked prefix, never a complete child.
            if (n.state.died || n.state.interrupted || n.state.boundary) n.plan = p;
            auto &alt = result.alternatives[p.at(0)];
            if (n.state.survived > alt.survived || alt.traces[0].empty()) alt = n.state;
            inspect(n); result.proposals.push_back(p);
            return n;
        };
        Plan coast; coast.length = 1;
        Plan press = coast; press.buttons[0] = true;
        if (!c.cubePulsePlanning) std::fill_n(press.buttons.begin(), press.length, true);
        // Both roots have the same short work allowance; neither gets an
        // entire horizon while the opposing immediate action starves.
        for (auto const &p : {c.immediateOrb ? press : coast, c.immediateOrb ? coast : press}) {
            if (c.forceRelease && p.at(0)) continue;
            auto n = run(p); if (alive(n)) frontier.push_back(std::move(n));
        }
        Node checkedRoot;
        Evaluation rootReplay;
        // Reserve a checked, non-launching command BEFORE expensive future
        // work. A scheduler interruption must not erase a known safe release.
        // Full decisions below still need their own independent replay.
        auto fallback = std::find_if(frontier.begin(), frontier.end(), [&](auto const &n) {
            return n.plan.at(0) == (c.cubePulsePlanning ? false : held);
        });
        if (fallback == frontier.end() && !c.cubePulsePlanning && !frontier.empty()) fallback = frontier.begin();
        if (fallback != frontier.end() && Clock::now() < searchEnd) {
            auto cold = oracle.prefix(fallback->plan, searchEnd, false);
            ++result.coldVerificationTicks;
            if (sameTimeline(fallback->state, cold, 1, c.dual)) {
                checkedRoot = *fallback; rootReplay = std::move(cold);
            }
        }
        if (c.priorityControl && c.cubePulsePlanning) {
            // Fresh native probes start at the live root with cache reads
            // disabled. The first command must also agree with the separate
            // one-step root simulation above. Keep this complete local proof
            // BEFORE a distant rollout can exhaust the remaining work.
            auto priority = [&](Plan p) {
                auto n = run(p, false, false);
                auto proof = proveRoute(c, n.plan, n.state);
                if (proof.kind == ProofKind::Horizon && n.plan.length < c.horizon)
                    proof = {ProofKind::GroundWait, n.state.groundedPrefixTicks};
                auto root = std::find_if(frontier.begin(), frontier.end(), [&](auto const &r) {
                    return r.plan.length == 1 && r.plan.at(0) == n.plan.at(0);
                });
                if (proof && !n.state.died && !n.state.interrupted && !n.state.boundary &&
                    root != frontier.end() && sameTimeline(root->state, n.state, 1, c.dual) &&
                    (proof.kind != ProofKind::GroundWait || proof.ticks >= minimum)) {
                    const bool flight = proof.kind == ProofKind::LandedRoute;
                    int takeoff = 0;
                    for (; takeoff < n.plan.length && !n.plan.at(takeoff); ++takeoff) {}
                    // Later takeoffs naturally end their local flight later.
                    // That is not a safety advantage. Rank the flight margin
                    // before duration, and prefer earlier equally safe input.
                    float margin = flight ? n.state.landingMargin : n.state.clearance;
                    auto score = std::tuple{flight ? 2 : 1,
                        int(std::clamp(margin, 0.f, 120.f) / 4),
                        flight ? -takeoff : 0, -n.state.buttonChanges, proof.ticks};
                    if (!checkedControlProof || score > checkedControlRank) {
                        checkedControl = n; checkedControlProof = proof;
                        checkedControlRank = score;
                    }
                }
                return n;
            };
            const int localLength = std::min(c.horizon, reactionTicks(c.tickSeconds, .9));
            if (warm_.length > 0) {
                Plan p = warm_; p.length = std::min(warm_.length, localLength);
                // Check only the next planned launch and its landing here.
                // Later launches remain in the distant proposal below.
                int first = -1;
                for (int t = 0; t < p.length; ++t) if (p.at(t)) { first = t; break; }
                if (first >= 0) {
                    std::fill(p.buttons.begin() + first + 1, p.buttons.end(), false);
                    p.length = std::min(MaxHorizon, first + localLength);
                    p.landingDelay = -1; p.stopAtLanding = true;
                    priority(p);
                }
            }
            Plan localCoast; localCoast.length = std::min(c.horizon, reactionTicks(c.tickSeconds, .18));
            auto waiting = priority(localCoast);
            if (!c.forceRelease) {
                std::vector<int> takeoffs;
                if (c.immediateOrb) takeoffs.push_back(0);
                if (waiting.state.died || waiting.state.supportLossTick >= 0) {
                    const int failure = waiting.state.supportLossTick >= 0
                        ? waiting.state.supportLossTick : waiting.state.survived;
                    for (double lead : {.16, .12, .08, .04})
                        takeoffs.push_back(std::max(0, failure - reactionTicks(c.tickSeconds, lead)));
                }
                for (int i = 0; i < c.jumpEventCount && takeoffs.size() < 8; ++i)
                    if (c.jumpEvents[i] <= reactionTicks(c.tickSeconds, .3)) takeoffs.push_back(c.jumpEvents[i]);
                if (takeoffs.empty() && waiting.state.died) takeoffs.push_back(0);
                std::vector<int> tried;
                for (int at : takeoffs) {
                    if (at < 0 || std::find(tried.begin(), tried.end(), at) != tried.end()) continue;
                    tried.push_back(at);
                    Plan p; p.length = std::min(MaxHorizon, at + localLength);
                    p.buttons[at] = true; p.stopAtLanding = true;
                    priority(p);
                    if (Clock::now() >= searchEnd || result.candidates >= c.candidates) break;
                }
            }
        }
        std::vector<Node> probes;
        auto probe = [&](Plan p) {
            auto n = run(p);
            if (n.state.survived > 0) probes.push_back(n);
            if (alive(n) && n.plan.length < c.horizon) frontier.push_back(std::move(n));
        };
        if (warm_.length > 0) {
            auto w = warm_;
            // Extend the retained future, rather than padding a short replay
            // certificate and losing all previously discovered turns.
            const auto kind = endKind(Node{}, c);
            bool hold = kind == InputKind::Thrust || kind == InputKind::DashHold || kind == InputKind::JumpHold;
            int old = w.length; w.length = c.horizon;
            if (old < w.length) std::fill(w.buttons.begin() + old, w.buttons.begin() + w.length,
                                         hold && w.at(std::max(0, old - 1)));
            probe(w);
        }
        // Long rollouts reach the actual obstacle before a wide tree of tiny
        // prefixes consumes the budget. Geometry only seeds possible input
        // coordinates; native physics still decides their outcome.
        Plan longCoast; longCoast.length = c.horizon; probe(longCoast);
        Plan longPress = longCoast;
        if (c.cubePulsePlanning) longPress.buttons[0] = true;
        else longPress.buttons.fill(true);
        if (!c.forceRelease || !c.cubePulsePlanning) probe(longPress);
        if (c.cubePulsePlanning) {
            for (int delay : {6, 0, 3}) {
                auto p = longPress; p.landingDelay = delay;
                if (!c.forceRelease) probe(p);
            }
            for (int i = 0; i < std::min(c.jumpEventCount, 8); ++i) {
                auto p = longCoast; int at = c.jumpEvents[i];
                if (at < 0 || at >= p.length || (at == 0 && c.forceRelease)) continue;
                p.buttons[at] = true; p.landingDelay = 6; probe(p);
            }
        }
        // Repair long failed paths at native control opportunities, including
        // coordinates well BEFORE the failure. Keep the already tested past.
        // This phase can discover several future turns within a few rollouts.
        int longRepairs = 0;
        while (!probes.empty() && longRepairs < 28 && Clock::now() < searchEnd) {
            auto pick = std::max_element(probes.begin(), probes.end(), [](auto const &a, auto const &b) { return rank(a) < rank(b); });
            Node parent = std::move(*pick); probes.erase(pick);
            if (alive(parent)) {
                if (parent.state.requiresLanding && !parent.state.landingVerified && parent.plan.length < MaxHorizon) {
                    auto p = parent.plan; int old = p.length;
                    p.length = std::min(MaxHorizon, old + reactionTicks(c.tickSeconds, .45));
                    std::fill(p.buttons.begin() + old, p.buttons.begin() + p.length, false);
                    auto n = run(p); if (n.state.survived > parent.state.survived) probes.push_back(std::move(n));
                }
                continue;
            }
            auto const &e = parent.state;
            if (e.interrupted || e.boundary) continue;
            int failure = e.supportLossTick >= 0 ? e.supportLossTick : e.survived;
            std::vector<int> opportunities;
            for (int lead240 : {48, 64, 32, 80, 24, 96, 16, 8, 4, 1}) {
                int lead = std::max(1, int(std::lround(lead240 / 240. / c.tickSeconds)));
                auto it = std::find_if(e.controlActions.rbegin(), e.controlActions.rend(),
                    [&](auto o) { return o.tick <= failure - lead; });
                if (it != e.controlActions.rend()) opportunities.push_back(it->tick);
            }
            for (auto at : e.orbActions) opportunities.push_back(at);
            for (auto at : opportunities) {
                if (at < 0 || at >= parent.plan.length || (at == 0 && c.forceRelease)) continue;
                auto p = parent.plan; p.landingDelay = -1;
                if (e.hasRealized) std::copy_n(e.realized.buttons.begin(), at, p.buttons.begin());
                auto kind = kindAt(e, c, at);
                bool continuous = kind == InputKind::Thrust || kind == InputKind::DashHold || kind == InputKind::JumpHold;
                if (continuous) std::fill(p.buttons.begin() + at, p.buttons.begin() + p.length, !p.at(at));
                else {
                    if (at > 0) p.buttons[at - 1] = false;
                    std::fill(p.buttons.begin() + at, p.buttons.begin() + p.length, false);
                    p.buttons[at] = true;
                }
                ++result.repairsCreated; ++longRepairs;
                auto n = run(p, true);
                if (n.state.survived > e.survived) probes.push_back(std::move(n));
                if (longRepairs >= 28 || result.candidates >= c.candidates || Clock::now() >= searchEnd) break;
            }
        }
        prune(frontier);
        while (!frontier.empty() && result.candidates < c.candidates && Clock::now() < searchEnd &&
               (!bestCertificate || bestProof.plan.length < c.horizon)) {
            std::vector<Node> next;
            for (auto const &parent : frontier) {
                if (parent.plan.length >= c.horizon) continue;
                int from = parent.plan.length;
                int chunk = slice;
                auto inputKind = endKind(parent, c);
                if (inputKind == InputKind::Thrust || inputKind == InputKind::AirEdge) {
                    if (parent.state.endpointClearance < 8.f) chunk = 2;
                    else if (parent.state.endpointClearance < 20.f) chunk = 4;
                }
                int to = std::min(c.horizon, from + chunk);
                // In an uncontrollable cube flight there is no branching until
                // a landing or ring. Extend it cheaply, while still recording
                // every native action opportunity for repair.
                if (!mayPress(parent, c)) to = std::min(c.horizon, from + slice * 3);
                auto kind = inputKind;
                bool continuous = kind == InputKind::Thrust || kind == InputKind::DashHold || kind == InputKind::JumpHold;
                Plan base = parent.plan; base.length = to;
                std::fill(base.buttons.begin() + from, base.buttons.begin() + to,
                    continuous && parent.plan.at(from - 1));
                std::vector<Plan> children{base};
                if (mayPress(parent, c)) {
                    Plan flip = base;
                    if (continuous) {
                        std::fill(flip.buttons.begin() + from, flip.buttons.begin() + to, !base.at(from));
                    } else {
                        if (from > 0) flip.buttons[from - 1] = false;
                        flip.buttons[from] = true;
                    }
                    children.push_back(flip);
                    // An input edge in the middle of a slice is a distinct
                    // physical state, important for wave/ship/UFO timing.
                    Plan middle = base; int at = from + (to - from) / 2;
                    if (continuous) std::fill(middle.buttons.begin() + at, middle.buttons.begin() + to, !base.at(at));
                    else middle.buttons[at] = true;
                    children.push_back(middle);
                }
                for (auto const &p : children) {
                    auto child = run(p);
                    if (alive(child)) { next.push_back(std::move(child)); continue; }
                    if (child.state.survived <= 0 || child.state.interrupted || child.state.boundary) continue;
                    auto const &e = child.state;
                    std::vector<int> opportunities = e.orbActions;
                    int failure = e.supportLossTick >= 0 ? e.supportLossTick : e.survived;
                    for (int lead : {1, 4, 8, 16, 24, 48}) {
                        auto it = std::find_if(e.controlActions.rbegin(), e.controlActions.rend(),
                            [&](auto o) { return o.tick <= failure - lead; });
                        if (it != e.controlActions.rend()) opportunities.push_back(it->tick);
                    }
                    for (auto it = opportunities.rbegin(); it != opportunities.rend(); ++it) {
                        int at = *it;
                        if (at < 0 || at >= to || (at == 0 && c.forceRelease)) continue;
                        Plan fixed = p;
                        if (e.hasRealized) std::copy_n(e.realized.buttons.begin(), at, fixed.buttons.begin());
                        if (at > 0) fixed.buttons[at - 1] = false;
                        std::fill(fixed.buttons.begin() + at, fixed.buttons.begin() + to, false);
                        auto repairKind = kindAt(e, c, at);
                        if (repairKind == InputKind::Thrust || repairKind == InputKind::DashHold || repairKind == InputKind::JumpHold)
                            std::fill(fixed.buttons.begin() + at, fixed.buttons.begin() + to, !p.at(at));
                        else fixed.buttons[at] = true;
                        ++result.repairsCreated;
                        auto repaired = run(fixed, true);
                        if (alive(repaired)) next.push_back(std::move(repaired));
                        if (Clock::now() >= searchEnd || result.candidates >= c.candidates) break;
                    }
                }
                if (Clock::now() >= searchEnd || result.candidates >= c.candidates) break;
            }
            prune(next); frontier = std::move(next);
        }
        // Compare neighbouring takeoff coordinates. A single lucky, late
        // input is less robust than a flight that survives nearby timings.
        // These are full native rollouts, never a geometric safety promise.
        if (c.cubePulsePlanning && bestCertificate && bestProof.state.requiresLanding) {
            Node original = bestProof;
            int at = -1;
            for (int t = 0; t < original.plan.length; ++t) {
                if (original.plan.at(t) && original.state.motions[0].size() > size_t(t) &&
                    (original.state.motions[0][t].canJump || original.state.motions[0][t].grounded)) { at = t; break; }
            }
            if (at >= 0) {
                std::vector<std::pair<int, Node>> safe{{at, original}};
                for (int delta : {-2, 2, -4, 4, -1, 1, -3, 3}) {
                    int shifted = at + delta;
                    if (shifted < 0 || shifted >= original.plan.length || (shifted == 0 && c.forceRelease)) continue;
                    auto p = original.plan; p.buttons[at] = false;
                    if (shifted > 0) p.buttons[shifted - 1] = false;
                    p.buttons[shifted] = true;
                    auto n = run(p, true);
                    if (alive(n) && n.state.firstActionVerified && n.state.landingVerified &&
                        n.state.survived >= original.state.survived) safe.emplace_back(shifted, std::move(n));
                    if (result.candidates >= c.candidates || Clock::now() >= searchEnd) break;
                }
                // Prefer the middle of the actually checked contiguous
                // window when its clearance is within one pixel of the best.
                int lo = at, hi = at;
                auto found = [&](int tick) { return std::any_of(safe.begin(), safe.end(), [&](auto const &v) { return v.first == tick; }); };
                while (found(lo - 1)) --lo;
                while (found(hi + 1)) ++hi;
                result.jumpWindowTicks = hi - lo + 1;
                auto center = std::min_element(safe.begin(), safe.end(), [&](auto const &a, auto const &b) {
                    return std::abs(a.first - (lo + hi) / 2) < std::abs(b.first - (lo + hi) / 2);
                });
                if (center != safe.end() && center->first >= lo && center->first <= hi &&
                    center->second.state.landingMargin + 1.f >= bestProof.state.landingMargin) {
                    auto proof = proveRoute(c, center->second.plan, center->second.state);
                    if (proof) { bestProof = center->second; bestCertificate = proof; }
                }
            }
        }
        result.exhausted = Clock::now() >= searchEnd || result.candidates >= c.candidates;
        // Always replay the chosen prefix from the live root with memoization
        // off. A cache hit, or a model that merely predicts life, cannot issue
        // a long queue on its own.
        bool certified = false;
        auto recover = [&](Node const &candidate, Evaluation cold) {
            const bool mustLand = modePolicy(c.plannerMode).mustLand ||
                (c.dual && modePolicy(c.secondaryMode).mustLand);
            // Never launch a new unsteerable flight just because its first
            // command survived. A ground takeoff/ring action needs landing.
            if (mustLand && candidate.plan.at(0) && !cold.landingVerified) return false;
            int safe = cold.survived - int(cold.boundary);
            if (safe < 1) return false;
            // Even a deadline-interrupted replay can prove the first completed
            // command. Clear terminal flags only AFTER truncating to that
            // actually checked prefix; never queue its unchecked tail.
            trimProof(cold, 1);
            cold.died = cold.interrupted = cold.boundary = false;
            if (!sameTimeline(candidate.state, cold, 1, c.dual)) return false;
            result.recoveryInput = true; result.recoveryTicks = 1;
            result.down = cold.hasRealized ? cold.realized.at(0) : candidate.plan.at(0);
            result.plan = cold.hasRealized ? cold.realized : candidate.plan;
            result.plan.length = 1; result.predictedTicks = 1;
            result.selected = std::move(cold); result.selected.firstActionVerified = false;
            result.forecastDied = candidate.state.died;
            result.forecastInterrupted = candidate.state.interrupted;
            warm_ = candidate.state.hasRealized ? candidate.state.realized : candidate.plan;
            warm_.length = candidate.state.survived;
            result.forecast = candidate.state; result.forecastPlan = warm_;
            result.forecastTicks = candidate.state.survived;
            trimProof(result.forecast, result.forecastTicks);
            return true;
        };
        if (bestCertificate && Clock::now() < end) {
            Plan p = bestProof.plan; p.length = bestCertificate.ticks;
            // Replay only the portion we are about to queue. Preserve the
            // long forecast separately, so it can seed the next live search.
            if (!modePolicy(c.plannerMode).mustLand &&
                (!c.dual || !modePolicy(c.secondaryMode).mustLand))
                p.length = std::min(p.length, reactionTicks(c.tickSeconds, .28));
            else if (bestProof.state.firstLandingTick >= 0 && bestProof.state.requiresLanding)
                p.length = std::min(p.length, bestProof.state.firstLandingTick + 1 + reactionTicks(c.tickSeconds, .04));
            if (bestCertificate.kind != ProofKind::GroundWait && bestProof.state.firstLandingTick >= 0) {
                for (int t = bestProof.state.firstLandingTick + 4; t < p.length; ++t) {
                    if (p.at(t) && bestProof.state.motions[0].size() > size_t(t) && (bestProof.state.motions[0][t].canJump || bestProof.state.motions[0][t].grounded)) {
                        p.length = t; break;
                    }
                }
            }
            auto cold = oracle.prefix(p, end, false);
            result.coldVerificationTicks = cold.survived;
            if (cold.survived == p.length && !cold.died && !cold.interrupted &&
                bestProof.state.requiresLanding && bestCertificate.kind != ProofKind::GroundWait &&
                !cold.landingVerified && p.length < bestCertificate.ticks && Clock::now() < end) {
                p.length = bestCertificate.ticks;
                cold = oracle.prefix(p, end, false);
                result.coldVerificationTicks += cold.survived;
            }
            if (sameTimeline(bestProof.state, cold, p.length, c.dual) &&
                (!bestProof.state.requiresLanding || bestCertificate.kind == ProofKind::GroundWait || cold.landingVerified)) {
                certified = true; result.hasValidatedInput = true;
                result.down = p.at(0); result.plan = p; result.selected = std::move(cold);
                result.certificateTicks = result.predictedTicks = p.length;
                result.proofKind = int(bestCertificate.kind);
                result.completeContinuation = bestCertificate.kind == ProofKind::Horizon || bestCertificate.kind == ProofKind::LandedRoute;
                result.recedingCertificate = !result.completeContinuation;
                result.selected.firstActionVerified = true;
                result.forecastDied = bestProof.state.died; result.forecastInterrupted = bestProof.state.interrupted;
                warm_ = bestProof.state.hasRealized ? bestProof.state.realized : bestProof.plan;
                warm_.length = bestProof.state.survived;
                result.forecast = bestProof.state; result.forecastPlan = warm_;
                result.forecastTicks = bestProof.state.survived;
                trimProof(result.forecast, result.forecastTicks);
            } else {
                if (!recover(bestProof, std::move(cold))) ++result.cacheMismatches;
            }
        }
        if (!certified && checkedControlProof) {
            // Optional distant work must not erase a launch whose entire
            // flight was already simulated from a fresh root and landed.
            result.hasValidatedInput = certified = true;
            result.recoveryInput = false;
            result.recoveryTicks = 0;
            result.plan = checkedControl.state.hasRealized ? checkedControl.state.realized : checkedControl.plan;
            result.plan.length = checkedControlProof.ticks;
            result.down = result.plan.at(0);
            result.selected = checkedControl.state;
            trimProof(result.selected, result.plan.length);
            result.selected.firstActionVerified = true;
            result.certificateTicks = result.predictedTicks = result.plan.length;
            result.proofKind = int(checkedControlProof.kind);
            result.completeContinuation = checkedControlProof.kind == ProofKind::LandedRoute;
            result.recedingCertificate = !result.completeContinuation;
            auto const &future = bestProof.state.survived >= checkedControlProof.ticks &&
                sameTimeline(checkedControl.state, bestProof.state, checkedControlProof.ticks, c.dual)
                ? bestProof : checkedControl;
            warm_ = future.state.hasRealized ? future.state.realized : future.plan;
            warm_.length = future.state.survived;
            result.forecast = future.state; result.forecastPlan = warm_;
            result.forecastTicks = future.state.survived;
            result.forecastDied = future.state.died;
            result.forecastInterrupted = future.state.interrupted;
            trimProof(result.forecast, result.forecastTicks);
        }
        if (!certified && !result.recoveryInput && rootReplay.survived == 1) {
            if (bestCertificate && bestProof.plan.at(0) == checkedRoot.plan.at(0))
                recover(bestProof, rootReplay);
            else if (bestRecovery.state.survived > 0 && bestRecovery.plan.at(0) == checkedRoot.plan.at(0))
                recover(bestRecovery, rootReplay);
            if (!result.recoveryInput) recover(checkedRoot, std::move(rootReplay));
        }
        if (!certified && !result.recoveryInput && bestRecovery.state.survived > 0 && Clock::now() < end) {
            Plan p = bestRecovery.state.hasRealized ? bestRecovery.state.realized : bestRecovery.plan;
            int safe = bestRecovery.state.survived - int(bestRecovery.state.boundary);
            p.length = std::min(safe, std::max(1, std::min(slice, 8)));
            if (p.length > 0) {
                auto cold = oracle.prefix(p, end, false);
                result.coldVerificationTicks = cold.survived;
                if (!recover(bestRecovery, std::move(cold))) ++result.cacheMismatches;
            }
        }
        if (!certified && !result.recoveryInput) {
            // Show the actual failed/interrupted native proposal as such;
            // an absent input certificate must not hide every diagnostic path.
            result.forecast = bestRecovery.state;
            result.forecastPlan = bestRecovery.state.hasRealized ? bestRecovery.state.realized : bestRecovery.plan;
            result.forecastTicks = bestRecovery.state.survived;
            trimProof(result.forecast, result.forecastTicks);
            reset();
        }
        result.exhausted |= Clock::now() >= end;
        result.elapsedMs = std::chrono::duration<double, std::milli>(Clock::now() - start).count();
        return result;
    }
};
} // namespace agb
