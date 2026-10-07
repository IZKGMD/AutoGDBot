#pragma once
#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <limits>
#include <vector>
#include <utility>

namespace agb {
constexpr int MaxPlanningHorizon = 240;
constexpr int MaxHorizon = 512; // bounded tail for completing a flight beyond lookahead
constexpr int MaxCandidates = 96;
using Clock = std::chrono::steady_clock;
struct Point {
    float x{}, y{};
};
struct Plan {
    std::array<uint8_t, MaxHorizon> buttons{};
    int length = 0;
    int landingDelay = -1; // -1: fixed timeline; >=0: native-grounded policy
    bool at(int tick) const {
        return buttons[std::clamp(tick, 0, MaxHorizon - 1)] != 0;
    }
};
struct Evaluation {
    int survived = 0;
    bool died = false;
    bool interrupted = false;
    float clearance = 0;
    float landingMargin = 0;
    float robustness = 0;
    int buttonChanges = -1;
    bool firstActionVerified = true;
    bool boundary = false;
    bool requiresLanding = false, landingVerified = false;
    std::array<std::vector<Point>, 2> traces;
    Plan realized;
    bool hasRealized = false;
    int firstLandingTick = -1, landingCount = 0;
    std::vector<int> groundedActions; // native-confirmed opportunities for a continuation
    std::vector<int> orbActions;      // native-confirmed ring contact ticks

};
struct Config {
    int horizon = 144, candidates = 48;
    double budgetMs = 1.0;
    bool forceRelease = false;
    bool cubePulsePlanning = false;
    bool immediateOrb = false; // real player is touching an activatable orb now
    std::array<int, 32> jumpEvents{};
    int jumpEventCount = 0;
    std::array<int, 32> orbEvents{};
    int orbEventCount = 0;
    std::array<int, 16> landingEvents{};
    int landingEventCount = 0;
};
struct Decision {
    bool down = false, hasValidatedInput = false, exhausted = false;
    bool completeContinuation = false;
    int candidates = 0, predictedTicks = 0, rejectedUnsafe = 0;
    int bestRejectedTicks = 0, landingRejects = 0, deaths = 0, interruptions = 0;
    double elapsedMs = 0;
    Plan plan;
    Evaluation selected;
    std::array<Evaluation, 2> alternatives;
    std::vector<Plan> proposals;
};
// The evaluator must check the deadline between physics steps and restore its
// state even when interrupted. Ranking cannot turn an untested plan into input.
class Controller {
    Plan warm_;
    uint64_t random_ = 0x913851f75b72cd09ULL;
    uint32_t nextRandom() {
        random_ ^= random_ << 13;
        random_ ^= random_ >> 7;
        random_ ^= random_ << 17;
        return static_cast<uint32_t>(random_ >> 16);
    }

  public:
    void reset() {
        warm_ = {};
        random_ = 0x913851f75b72cd09ULL;
    }
    void advance(int ticks = 1) {
        if (warm_.length <= 0)
            return;
        ticks = std::clamp(ticks, 0, warm_.length);
        for (int i = 0; i < warm_.length - ticks; ++i)
            warm_.buttons[i] = warm_.buttons[i + ticks];
        warm_.length -= ticks;
        std::fill(warm_.buttons.begin() + warm_.length, warm_.buttons.end(), false);
    }
    std::vector<Plan> propose(Config cfg, bool held) {
        cfg.horizon = std::clamp(cfg.horizon, 24, MaxPlanningHorizon);
        cfg.candidates = std::clamp(cfg.candidates, 4, MaxCandidates);
        std::vector<Plan> plans;
        plans.reserve(cfg.candidates);
        auto add = [&](Plan p) {
            if (int(plans.size()) >= cfg.candidates)
                return;
            p.length = cfg.horizon;
            if (cfg.forceRelease)
                p.buttons[0] = false;
            for (auto const &old : plans)
                if (p.landingDelay == old.landingDelay &&
                    std::equal(p.buttons.begin(), p.buttons.begin() + p.length,
                               old.buttons.begin()))
                    return;
            plans.push_back(p);
        };

        // Revalidate the previous collision-checked route first on normal
        // ticks. During an actual orb contact, PRESS/SKIP must be evaluated
        // before any cached route because the input window is only a few ticks.
        if (warm_.length > 0 && !cfg.immediateOrb) {
            Plan w = warm_;
            std::fill(w.buttons.begin() + warm_.length, w.buttons.end(), false);
            add(w);
        }

        Plan warm = warm_;
        int known = std::min(warm_.length, cfg.horizon);
        std::fill(warm.buttons.begin() + known, warm.buttons.end(),
                  cfg.cubePulsePlanning ? false : held);
        warm.length = cfg.horizon;

        auto pulseAt = [&](int t) {
            if (t < 0 || t >= cfg.horizon)
                return;
            Plan p;
            p.buttons[t] = true;
            add(p);
        };

        if (cfg.cubePulsePlanning) {
            // Cube decisions are pulses, not held states. Do not spend one of
            // the first candidates on an all-DOWN timeline that CubePulse will
            // immediately reinterpret.
            Plan coast;
            coast.buttons.fill(false);
            // During a real native orb contact the two meaningful actions are
            // PRESS NOW and SKIP. Put PRESS first so the tiny contact window
            // can never be starved by unrelated geometry candidates.
            if (cfg.immediateOrb) {
                pulseAt(0);
                add(coast);
                if (warm_.length > 0) {
                    Plan w = warm_;
                    std::fill(w.buttons.begin() + warm_.length, w.buttons.end(), false);
                    add(w);
                }
            } else {
                add(coast);
                pulseAt(0);
            }

            const int jumpCount = std::clamp(cfg.jumpEventCount, 0, int(cfg.jumpEvents.size()));
            const int orbCount = std::clamp(cfg.orbEventCount, 0, int(cfg.orbEvents.size()));

            auto addOrbPairs = [&](int targetPairs) {
                int paired = 0;
                for (int i = 0; i < jumpCount && paired < targetPairs; ++i) {
                    for (int j = 0; j < orbCount && paired < targetPairs; ++j) {
                        const int takeoff = cfg.jumpEvents[i];
                        const int orb = cfg.orbEvents[j];
                        if (takeoff < 0 || orb < 0 || takeoff >= orb - 3 || orb >= cfg.horizon)
                            continue;
                        Plan p;
                        p.buttons[takeoff] = true;
                        p.buttons[orb] = true;
                        const auto before = plans.size();
                        add(p);
                        paired += plans.size() != before;
                    }
                }
            };

            // Coverage before refinement: reserve room for a real multi-action
            // route and for several distinct geometry events even with a tiny
            // candidate budget.
            addOrbPairs(4);
            const int earlyJumpCount = std::min(jumpCount, 8);
            for (int i = 0; i < earlyJumpCount; ++i)
                pulseAt(cfg.jumpEvents[i]);

            // Native-touchdown policies remain the primary staircase mechanism,
            // but they no longer monopolize the list before event coverage.
            for (int delay : {0, 1, 3, 6}) {
                Plan p;
                p.buttons[0] = true;
                p.landingDelay = delay;
                add(p);
            }

            for (int i = earlyJumpCount; i < jumpCount; ++i)
                pulseAt(cfg.jumpEvents[i]);

            // A zero-delay touchdown policy for several geometry takeoffs gives
            // the search a cheap repeated-jump route before denser refinements.
            for (int i = 0; i < jumpCount && i < 10; ++i) {
                Plan p;
                p.buttons[cfg.jumpEvents[i]] = true;
                p.landingDelay = 0;
                add(p);
            }
            for (int delay : {1, 3, 6}) {
                for (int i = 0; i < jumpCount && i < 6; ++i) {
                    Plan p;
                    p.buttons[cfg.jumpEvents[i]] = true;
                    p.landingDelay = delay;
                    add(p);
                }
            }

            // Orb contact itself may be the next controllable action while the
            // cube is already airborne.
            for (int i = 0; i < orbCount; ++i)
                pulseAt(cfg.orbEvents[i]);
            addOrbPairs(16);

            const int landingCount =
                std::clamp(cfg.landingEventCount, 0, int(cfg.landingEvents.size()));
            auto addStepRoute = [&](int takeoff, int radius) {
                if (takeoff < 0 || takeoff >= cfg.horizon)
                    return;
                Plan p;
                p.buttons[takeoff] = true;
                int chained = 0;
                for (int i = 0; i < landingCount && chained < 8; ++i) {
                    const int center = cfg.landingEvents[i];
                    if (center <= takeoff + 8 || center >= cfg.horizon)
                        continue;
                    for (int k = std::max(takeoff + 2, center - radius);
                         k <= std::min(cfg.horizon - 1, center + radius); ++k)
                        p.buttons[k] = true;
                    ++chained;
                }
                if (chained)
                    add(p);
            };
            if (landingCount) {
                addStepRoute(0, 6);
                for (int i = 0; i < jumpCount && i < 8; ++i)
                    addStepRoute(cfg.jumpEvents[i], 6);
            }

            // Refine only after all important action classes have had a chance
            // to enter the bounded list.
            for (int t = 1; t <= 12; ++t)
                pulseAt(t);
            for (int d : {-1, 1, -2, 2, -3, 3})
                for (int i = 0; i < jumpCount; ++i)
                    pulseAt(cfg.jumpEvents[i] + d);
            for (int i = 0; i < orbCount; ++i)
                for (int d : {-1, 1, -2, 2})
                    pulseAt(cfg.orbEvents[i] + d);

            for (int t : {24, 48, 72, 96, 12, 36, 60, 84, 120, 144, 168, 192, 216,
                          6, 18, 1, 2, 3, 4, 8})
                pulseAt(t);

            // No pseudo-random cube tail. Spare slots are a deterministic
            // timing lattice, so identical game states produce identical
            // proposals on the first attempt.
            for (int phase : {0, 3}) {
                for (int t = phase; t < cfg.horizon && int(plans.size()) < cfg.candidates;
                     t += 6)
                    pulseAt(t);
            }
        } else {
            for (bool down : {held, !held}) {
                Plan p;
                p.buttons.fill(down);
                add(p);
            }
            add(warm);
            Plan flipped = warm;
            flipped.buttons[0] = !warm.at(0);
            add(flipped);
            for (int t : {1, 2, 3, 4, 6, 8, 12, 18, 24, 36, 48, 72, 96}) {
                if (t >= cfg.horizon)
                    continue;
                for (bool first : {held, !held}) {
                    Plan p;
                    for (int k = 0; k < cfg.horizon; ++k)
                        p.buttons[k] = k < t ? first : !first;
                    add(p);
                }
            }
            for (int attempts = 0;
                 int(plans.size()) < cfg.candidates && attempts < cfg.candidates * 16;
                 ++attempts) {
                Plan p = warm;
                int from = nextRandom() % cfg.horizon;
                int to = std::min(cfg.horizon, from + 1 + int(nextRandom() % 36));
                for (int k = from; k < to; ++k)
                    p.buttons[k] ^= 1;
                if (attempts & 1)
                    p.buttons[0] = !held;
                add(p);
            }
        }
        return plans;
    }
    template <class Evaluator>
    Decision decide(Config cfg, bool held, Evaluator &&evaluate,
                    std::vector<float> const &priority = {}) {
        cfg.horizon = std::clamp(cfg.horizon, 24, MaxPlanningHorizon);
        cfg.candidates = std::clamp(cfg.candidates, 4, MaxCandidates);
        cfg.jumpEventCount = std::clamp(cfg.jumpEventCount, 0, int(cfg.jumpEvents.size()));
        cfg.orbEventCount = std::clamp(cfg.orbEventCount, 0, int(cfg.orbEvents.size()));
        cfg.landingEventCount =
            std::clamp(cfg.landingEventCount, 0, int(cfg.landingEvents.size()));
        const auto start = Clock::now();
        const auto deadline =
            start +
            std::chrono::duration_cast<Clock::duration>(
                std::chrono::duration<double, std::milli>(std::clamp(cfg.budgetMs, 0.2, 8.0)));
        Config proposalConfig = cfg;
        const int expansionReserve = cfg.cubePulsePlanning ? std::min(32, cfg.candidates / 3) : 0;
        proposalConfig.candidates -= expansionReserve;
        auto plans = propose(proposalConfig, held);
        Decision result;
        result.down = cfg.forceRelease ? false : held;
        float bestScore = -std::numeric_limits<float>::infinity();
        std::array<bool, 2> immediateOrbEvaluated{};
        auto consider = [&](Plan const &plan, Evaluation e) {
            const bool complete = !e.died && !e.interrupted &&
                (e.boundary || e.survived >= cfg.horizon);
            // A short probe does not certify a jump or its landing. Never rank
            // a known death, even if it survives longer than another prefix.
            // Continuous modes may use a checked prefix; cube decisions need
            // a complete trajectory with all landings checked by the evaluator.
            if (e.survived <= 0 || !e.firstActionVerified || e.died ||
                (cfg.cubePulsePlanning && !complete)) {
                ++result.rejectedUnsafe;
                result.bestRejectedTicks = std::max(result.bestRejectedTicks, e.survived);
                result.landingRejects += !e.firstActionVerified;
                result.deaths += e.died;
                result.interruptions += e.interrupted;
                return false;
            }
            int changes = e.buttonChanges;
            if (changes < 0) {
                changes = 0;
                bool old = held;
                for (int k = 0; k < e.survived; ++k) {
                    changes += old != plan.at(k);
                    old = plan.at(k);
                }
            }
            const int continuityTicks = std::min({e.survived, warm_.length, 64});
            int continuity = 0;
            for (int k = 0; k < continuityTicks; ++k)
                continuity += plan.at(k) == warm_.at(k);
            const bool exactWarm = continuityTicks > 0 && continuity == continuityTicks;
            const float safeClearance = std::clamp(e.clearance, 0.f, 120.f);
            const float safeLanding = std::clamp(e.landingMargin, 0.f, 80.f);
            const float robust = std::clamp(e.robustness, 0.f, 1.f);
            // Clearance is meaningful only when the runtime rollout populated the
            // robustness metric from the whole trajectory. This prevents an endpoint
            // or synthetic clearance value from causing an unnecessary jump on open ground.
            const float marginWeight = robust > 0.f ? 1.f : 0.f;
            float score = std::min(e.survived, cfg.horizon) * 10000.f -
                          std::max(0, e.survived - cfg.horizon) * 10.f +
                          safeClearance * 85.f * marginWeight +
                          safeLanding * 55.f * marginWeight + robust * 18000.f -
                          changes * 18.f + continuity * 5.f;
            if (exactWarm)
                score += 1200.f;
            if (complete)
                score += 3000000.f;
            if (complete && e.landingVerified)
                score += 60000.f;
            if (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)))
                score += 100.f;
            if (cfg.immediateOrb) {
                const bool realizedPress = e.hasRealized ? e.realized.at(0) : plan.at(0);
                if (realizedPress)
                    score += 4000.f; // tie-break toward using a safe contacted orb
            }
            if (score > bestScore) {
                bestScore = score;
                result.down = e.hasRealized ? e.realized.at(0) : plan.at(0);
                result.predictedTicks = e.survived;
                result.hasValidatedInput = true;
                result.completeContinuation = complete;
                result.plan = e.hasRealized ? e.realized : plan;
                result.selected = std::move(e);
            }
            // Once a route has been validated, do not stop on a different
            // merely-safe timing. Re-test the warm route and keep it unless a
            // materially better continuation wins. This removes frame-to-frame
            // takeoff jitter near double/triple spikes.
            bool warmCompatible = warm_.length <= 0;
            if (!warmCompatible) {
                const int n = std::min({24, warm_.length, plan.length});
                warmCompatible = n > 0;
                for (int k = 0; k < n && warmCompatible; ++k)
                    warmCompatible = plan.at(k) == warm_.at(k);
            }
            // Never stop at the first merely-surviving geometry route. Without a
            // previously verified warm route, compare the protected timing neighborhood
            // so a wide-margin takeoff can beat a spike-grazing one. With a warm route,
            // early-stop is allowed only when that route itself has meaningful margin.
            const bool currentRobust = robust >= .55f || safeClearance >= 18.f;
            const bool geometrySensitive = cfg.jumpEventCount > 0 || cfg.orbEventCount > 0 ||
                                           cfg.landingEventCount > 0;
            // Open ground keeps the cheap three-evaluation fast path. Around hazards,
            // orbs and landing chains we compare the protected timing neighborhood unless
            // an already-verified robust warm route can be reused safely.
            const bool comparedImmediateOrb =
                !cfg.immediateOrb || (immediateOrbEvaluated[0] && immediateOrbEvaluated[1]);
            const bool mayEarlyStop =
                comparedImmediateOrb &&
                (!geometrySensitive ||
                 (warm_.length > 0 && currentRobust && warmCompatible));
            return complete && mayEarlyStop && result.plan.at(0) == plan.at(0) &&
                   (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)));
        };
        // Probes are diagnostics only. They must never become executable
        // plans or consume the budget before a full takeoff/landing check.
        std::vector<int> order(plans.size());
        for (size_t i = 0; i < order.size(); ++i)
            order[i] = int(i);
        size_t protectedCount = std::min(
            plans.size(), size_t(8 + std::max(0, cfg.jumpEventCount) +
                                 std::max(0, cfg.orbEventCount) +
                                 std::max(0, cfg.landingEventCount) + 20));
        if (priority.size() == plans.size() && plans.size() > protectedCount)
            std::stable_sort(order.begin() + protectedCount, order.end(),
                             [&](int a, int b) { return priority[a] > priority[b]; });
        int furthestFailure = -1;
        int furthestOrbFailure = -1;
        for (size_t cursor = 0; cursor < order.size(); ++cursor) {
            const int i = order[cursor];
            if (Clock::now() >= deadline) {
                result.exhausted = true;
                break;
            }
            auto e = evaluate(plans[i], deadline);
            ++result.candidates;
            immediateOrbEvaluated[plans[i].at(0) ? 1 : 0] = true;
            auto &alternative = result.alternatives[plans[i].at(0)];
            if (e.survived > alternative.survived || alternative.traces[0].empty())
                alternative = e;

            // Event-driven expansion: if native clone physics actually touches
            // an orb on a rejected route, branch exactly at that contact tick.
            // This replaces fragile "orb X coordinate == press time" guessing.
            if (cfg.cubePulsePlanning && e.died &&
                !e.orbActions.empty() && e.survived >= furthestOrbFailure &&
                int(plans.size()) < cfg.candidates) {
                furthestOrbFailure = e.survived;
                std::vector<int> children;
                int emitted = 0;
                for (auto it = e.orbActions.rbegin();
                     it != e.orbActions.rend() && emitted < 6 &&
                     int(plans.size()) < cfg.candidates; ++it) {
                    const int at = *it;
                    if (at < 1 || at >= cfg.horizon || plans[i].at(at))
                        continue;
                    Plan child = e.hasRealized ? e.realized : plans[i];
                    child.length = cfg.horizon;
                    child.landingDelay = -1;
                    std::fill(child.buttons.begin() + at, child.buttons.end(), false);
                    child.buttons[at] = true;
                    bool duplicate = false;
                    for (auto const &old : plans)
                        duplicate |= old.buttons == child.buttons &&
                                     old.landingDelay == child.landingDelay;
                    if (duplicate)
                        continue;
                    children.push_back(int(plans.size()));
                    plans.push_back(child);
                    ++emitted;
                }
                order.insert(order.begin() + cursor + 1, children.begin(), children.end());
            }

            // The same mechanism remains for native-confirmed touchdowns.
            if (cfg.cubePulsePlanning && e.died && e.survived > furthestFailure &&
                !e.groundedActions.empty() && int(plans.size()) < cfg.candidates) {
                furthestFailure = e.survived;
                std::vector<int> children;
                for (int lead : {24, 32, 16, 40, 8, 48, 0, 56}) {
                    const int index = std::max(0, int(e.groundedActions.size()) - 1 - lead);
                    const int at = e.groundedActions[index];
                    if (at < 1 || at >= cfg.horizon || plans[i].at(at))
                        continue;
                    Plan child = e.hasRealized ? e.realized : plans[i];
                    child.length = cfg.horizon;
                    child.landingDelay = -1;
                    std::fill(child.buttons.begin() + at, child.buttons.end(), false);
                    child.buttons[at] = true;
                    bool duplicate = false;
                    for (auto const &old : plans)
                        duplicate |= old.buttons == child.buttons &&
                                     old.landingDelay == child.landingDelay;
                    if (duplicate)
                        continue;
                    children.push_back(int(plans.size()));
                    plans.push_back(child);
                    if (int(plans.size()) >= cfg.candidates)
                        break;
                }
                order.insert(order.begin() + cursor + 1, children.begin(), children.end());
            }
            if (consider(plans[i], std::move(e)))
                break;
        }
        if (result.hasValidatedInput && result.completeContinuation) {
            warm_ = result.plan;
            warm_.length = std::min(warm_.length, result.selected.survived);
        }
        if (!result.hasValidatedInput) {
            warm_ = {};
            if (cfg.cubePulsePlanning)
                result.down = false;
        }
        result.proposals = std::move(plans);
        result.elapsedMs = std::chrono::duration<double, std::milli>(Clock::now() - start).count();
        return result;
    }
};
} // namespace agb

