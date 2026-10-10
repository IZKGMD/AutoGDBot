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
            int(std::clamp(n.state.endpointClearance, 0.f, 120.f) * 8),
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
        auto searchEnd = start + (end - start) * 3 / 4;
        Decision result; result.down = c.forceRelease ? false : held;
        if (start >= end) { result.exhausted = true; return result; }
        const int slice = std::clamp(int(std::lround(.05 / c.tickSeconds)), 4, 16);
        const int minimum = reactionTicks(c.tickSeconds, .10);
        std::vector<Node> frontier;
        std::vector<Plan> seen;
        Node bestProof, bestRecovery;
        RouteProof bestCertificate;
        auto bestProofRank = std::tuple{-1, -1, -1, -100000};
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
                auto score = std::tuple{full ? 2 : 1, proof.ticks,
                    int(std::clamp(e.endpointClearance, 0.f, 120.f) * 4), -e.buttonChanges};
                if (score > bestProofRank) {
                    bestProofRank = score; bestProof = n; bestCertificate = proof;
                }
            }
            if (e.survived > 0 && traceCovers(e, e.survived, c.dual) &&
                (bestRecovery.state.survived == 0 || rank(n) > rank(bestRecovery))) bestRecovery = n;
        };
        auto run = [&](Plan p, bool repair = false) -> Node {
            p.length = std::clamp(p.length, 1, MaxHorizon);
            if (c.forceRelease) p.buttons[0] = false;
            Node n; n.plan = p;
            if (Clock::now() >= searchEnd || result.candidates >= c.candidates) return n;
            for (auto const &s : seen)
                if (s.length == p.length && std::equal(s.buttons.begin(), s.buttons.begin() + p.length,
                                                     p.buttons.begin())) return n;
            seen.push_back(p);
            n.state = oracle.prefix(p, searchEnd, true);
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
        if (warm_.length > 0) {
            auto w = warm_; w.length = std::min(MaxHorizon, std::max(c.horizon, warm_.length));
            auto n = run(w); if (alive(n)) frontier.push_back(std::move(n));
        }
        prune(frontier);
        while (!frontier.empty() && result.candidates < c.candidates && Clock::now() < searchEnd) {
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
                        auto repairMode = c.plannerMode;
                        if (!e.modeTraces[0].empty() && e.modeTraces[0].size() > size_t(at))
                            repairMode = e.modeTraces[0][at].controlMode();
                        auto repairKind = modePolicy(repairMode).input;
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
        result.exhausted = Clock::now() >= searchEnd || result.candidates >= c.candidates;
        // Always replay the chosen prefix from the live root with memoization
        // off. A cache hit, or a model that merely predicts life, cannot issue
        // a long queue on its own.
        bool certified = false;
        auto recover = [&](Node const &candidate, Evaluation cold) {
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
            return true;
        };
        if (bestCertificate && Clock::now() < end) {
            Plan p = bestProof.plan; p.length = bestCertificate.ticks;
            auto cold = oracle.prefix(p, end, false);
            result.coldVerificationTicks = cold.survived;
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
                seedVerifiedPrefix(p, p.length);
            } else {
                if (!recover(bestProof, std::move(cold))) ++result.cacheMismatches;
            }
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
        if (!certified && !result.recoveryInput) reset();
        result.exhausted |= Clock::now() >= end;
        result.elapsedMs = std::chrono::duration<double, std::milli>(Clock::now() - start).count();
        return result;
    }
};
} // namespace agb
