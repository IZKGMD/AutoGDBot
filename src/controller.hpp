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

enum class PlannerMode : uint8_t {
    Cube = 0,
    Ship = 1,
    Ball = 2,
    Ufo = 3,
    Wave = 4,
    Robot = 5,
    Spider = 6,
    Swing = 7,
    Generic = 255
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
    float nearClearance = 0; // minimum hazard margin in the immediate control window
    float landingMargin = 0;
    float robustness = 0;
    int buttonChanges = -1;
    bool firstActionVerified = true;
    bool boundary = false;
    bool decisionPoint = false; // native state where the live controller can safely replan
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
    double tickSeconds = 1.0 / 240.0;
    bool forceRelease = false;
    bool cubePulsePlanning = false;
    bool immediateOrb = false; // touching or within the certified pre-contact orb window
    PlannerMode plannerMode = PlannerMode::Generic;
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
    bool recedingCertificate = false;
    int candidates = 0, predictedTicks = 0, certificateTicks = 0, rejectedUnsafe = 0;
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

  public:
    void reset() {
        warm_ = {};
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
        const double tickSeconds =
            std::clamp(cfg.tickSeconds, 1.0 / 1000.0, 1.0 / 30.0);
        auto T = [&](int base240Ticks) {
            return std::clamp(
                int(std::lround((base240Ticks / 240.0) / tickSeconds)),
                1, cfg.horizon - 1);
        };
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
        const bool discretePulseMode =
            cfg.cubePulsePlanning || cfg.plannerMode == PlannerMode::Ball ||
            cfg.plannerMode == PlannerMode::Ufo || cfg.plannerMode == PlannerMode::Spider;
        std::fill(warm.buttons.begin() + known, warm.buttons.end(),
                  discretePulseMode ? false : held);
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
            for (int base = 1; base <= 12; ++base)
                pulseAt(T(base));
            for (int d : {-1, 1, -2, 2, -3, 3})
                for (int i = 0; i < jumpCount; ++i)
                    pulseAt(cfg.jumpEvents[i] + d);
            for (int i = 0; i < orbCount; ++i)
                for (int d : {-1, 1, -2, 2})
                    pulseAt(cfg.orbEvents[i] + d);

            for (int base : {24, 48, 72, 96, 12, 36, 60, 84, 120, 144, 168, 192, 216,
                             6, 18, 1, 2, 3, 4, 8})
                pulseAt(T(base));

            // No pseudo-random cube tail. Spare slots are a deterministic
            // timing lattice, so identical game states produce identical
            // proposals on the first attempt.
            for (int basePhase : {0, 3}) {
                const int phase = basePhase == 0 ? 0 : T(basePhase);
                const int step = std::max(1, T(6));
                for (int t = phase; t < cfg.horizon && int(plans.size()) < cfg.candidates;
                     t += step)
                    pulseAt(t);
            }
        } else {
            const int orbCount = std::clamp(cfg.orbEventCount, 0, int(cfg.orbEvents.size()));

            auto constant = [&](bool down) {
                Plan p;
                p.buttons.fill(down);
                add(p);
            };
            auto switchAt = [&](int t, bool first) {
                if (t < 0 || t >= cfg.horizon)
                    return;
                Plan p;
                for (int k = 0; k < cfg.horizon; ++k)
                    p.buttons[k] = k < t ? first : !first;
                add(p);
            };
            auto window = [&](int startTick, int duration) {
                if (startTick >= cfg.horizon || duration <= 0)
                    return;
                Plan p;
                for (int k = std::max(0, startTick);
                     k < std::min(cfg.horizon, startTick + duration); ++k)
                    p.buttons[k] = true;
                add(p);
            };
            auto edgePulse = [&](int t, bool baseline) {
                if (t < 0 || t >= cfg.horizon)
                    return;
                Plan p;
                p.buttons.fill(baseline);
                if (t > 0)
                    p.buttons[t - 1] = false;
                p.buttons[t] = true;
                if (t + 1 < cfg.horizon)
                    p.buttons[t + 1] = baseline;
                add(p);
            };
            auto pulseTrain = [&](int period, int phase = 0) {
                if (period < 2)
                    return;
                Plan p;
                for (int t = std::max(0, phase); t < cfg.horizon; t += period)
                    p.buttons[t] = true;
                add(p);
            };
            auto pwm = [&](int period, int duty, int phase) {
                if (period < 2 || duty <= 0 || duty >= period)
                    return;
                Plan p;
                for (int t = 0; t < cfg.horizon; ++t) {
                    int q = (t + phase) % period;
                    p.buttons[t] = q < duty;
                }
                add(p);
            };

            // Real ring contacts are timing-critical in every game mode, not
            // only cube. Seed an explicit rising edge at the native contact
            // window before filling the rest of the mode-specific candidates.
            for (int i = 0; i < orbCount && i < 12; ++i) {
                edgePulse(cfg.orbEvents[i], false);
                edgePulse(cfg.orbEvents[i], held);
            }

            switch (cfg.plannerMode) {
            case PlannerMode::Ball:
            case PlannerMode::Ufo:
            case PlannerMode::Spider: {
                // These modes are edge-triggered. One-tick pulses and compact
                // pulse trains explore gravity flips / UFO hops / spider snaps
                // without ever relying on a long held state.
                constant(false);
                edgePulse(0, false);
                for (int base : {1, 2, 3, 4, 6, 8, 12, 18, 24, 32, 40, 48, 64, 80, 96, 120, 144})
                    edgePulse(T(base), false);
                for (int basePeriod : {6, 8, 10, 12, 16, 20, 24, 32, 40, 48}) {
                    const int period = std::max(2, T(basePeriod));
                    pulseTrain(period, 0);
                    pulseTrain(period, period / 2);
                }
                break;
            }

            case PlannerMode::Robot: {
                // Robot jump height is hold-duration controlled. Search a
                // deterministic lattice of short/medium/long holds plus delayed
                // windows for landing and obstacle timing.
                constant(false);
                constant(true);
                for (int baseDuration : {1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64})
                    window(0, T(baseDuration));
                for (int baseStart : {2, 4, 6, 8, 12, 18, 24, 32, 48})
                    for (int baseDuration : {2, 4, 8, 16, 24})
                        window(T(baseStart), T(baseDuration));
                break;
            }

            case PlannerMode::Ship:
            case PlannerMode::Wave:
            case PlannerMode::Swing: {
                // Continuous modes need controlled switch times, short
                // corrections and periodic duty cycles. This deterministic
                // basis replaces the old pseudo-random mutation tail.
                constant(held);
                constant(!held);
                add(warm);
                Plan flipped = warm;
                flipped.buttons[0] = !warm.at(0);
                add(flipped);

                for (int base : {1, 2, 3, 4, 6, 8, 12, 18, 24, 32, 40, 48, 64, 80, 96, 120, 144, 180})
                    for (bool first : {held, !held})
                        switchAt(T(base), first);

                for (int baseFirst : {4, 8, 12, 18, 24, 32}) {
                    for (int baseWidth : {4, 8, 12, 18, 24, 32}) {
                        const int firstSwitch = T(baseFirst);
                        const int width = T(baseWidth);
                        Plan p;
                        p.buttons.fill(held);
                        for (int k = firstSwitch;
                             k < std::min(cfg.horizon, firstSwitch + width); ++k)
                            p.buttons[k] = !held;
                        add(p);
                    }
                }

                for (int basePeriod : {4, 6, 8, 12, 16, 24, 32}) {
                    const int period = std::max(2, T(basePeriod));
                    pwm(period, std::max(1, period / 3), 0);
                    pwm(period, std::max(1, period / 2), period / 4);
                    pwm(period, std::max(1, (period * 2) / 3), period / 2);
                }
                break;
            }

            default: {
                // Generic fallback is deterministic as well. Identical states
                // now always produce the same candidate set in every mode.
                constant(held);
                constant(!held);
                add(warm);
                for (int base : {1, 2, 3, 4, 6, 8, 12, 18, 24, 36, 48, 72, 96, 120, 144})
                    for (bool first : {held, !held})
                        switchAt(T(base), first);
                for (int basePeriod : {8, 12, 16, 24, 32}) {
                    const int period = std::max(2, T(basePeriod));
                    pwm(period, period / 2, 0);
                }
                break;
            }
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
        const double safeTickSeconds =
            std::clamp(cfg.tickSeconds, 1.0 / 1000.0, 1.0 / 30.0);
        auto secondsToTicks = [&](double seconds) {
            return std::clamp(int(std::ceil(seconds / safeTickSeconds)), 4, cfg.horizon);
        };
        double certificateSeconds = .12;
        switch (cfg.plannerMode) {
        case PlannerMode::Ship:
        case PlannerMode::Wave:
        case PlannerMode::Swing:
            certificateSeconds = .10;
            break;
        case PlannerMode::Ufo:
        case PlannerMode::Robot:
            certificateSeconds = .12;
            break;
        case PlannerMode::Ball:
        case PlannerMode::Spider:
            certificateSeconds = .14;
            break;
        default:
            certificateSeconds = .12;
            break;
        }
        const int minCertificateTicks = secondsToTicks(certificateSeconds);

        auto consider = [&](Plan const &plan, Evaluation e) {
            const bool complete = !e.died && !e.interrupted &&
                (e.boundary || e.survived >= cfg.horizon);
            const bool eventCertified =
                !e.died && e.firstActionVerified && e.decisionPoint;
            // Continuous/re-responsive modes do not need to prove the whole
            // 0.8-1.0 s future every frame. A collision-free prefix long
            // enough for the next live replanning cycle is a valid MPC
            // certificate. Cube still requires a native decision point
            // (landing/orb/portal) because its airborne path is not freely
            // steerable.
            const bool prefixCertified =
                !cfg.cubePulsePlanning && e.firstActionVerified &&
                e.survived >= minCertificateTicks;
            const bool certified = complete || eventCertified || prefixCertified;

            if (e.survived <= 0 || !e.firstActionVerified || !certified) {
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
            const float nearClearance = std::clamp(e.nearClearance, 0.f, 120.f);
            const float safeLanding = std::clamp(e.landingMargin, 0.f, 80.f);
            const float robust = std::clamp(e.robustness, 0.f, 1.f);
            const float marginWeight = robust > 0.f ? 1.f : 0.f;

            // For a known-later-death receding prefix, extra time-to-death
            // must not dominate the immediate hazard margin. The live MPC will
            // replan long before that later death; what matters most is keeping
            // the next ~150 ms as wide and recoverable as possible.
            const int scoredSurvival =
                e.died && !complete
                    ? std::min(e.survived, minCertificateTicks + secondsToTicks(.05))
                    : std::min(e.survived, cfg.horizon);
            const float nearWeight = e.died && !complete ? 5200.f : 900.f;
            float score = scoredSurvival * (e.died && !complete ? 2200.f : 10000.f) -
                          std::max(0, e.survived - cfg.horizon) * 10.f +
                          nearClearance * nearWeight +
                          safeClearance * 110.f * marginWeight +
                          safeLanding * 55.f * marginWeight + robust * 18000.f -
                          changes * 18.f + continuity * 5.f;
            if (exactWarm)
                score += 1200.f;
            if (complete)
                score += 3000000.f;
            else if (eventCertified)
                score += 1900000.f;
            else if (!e.died)
                score += 650000.f;
            else
                score -= 1200000.f; // known later death: usable only as last-resort prefix
            if ((complete || eventCertified) && e.landingVerified)
                score += 60000.f;
            if (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)))
                score += 100.f;
            if (cfg.immediateOrb) {
                const bool realizedPress = e.hasRealized ? e.realized.at(0) : plan.at(0);
                if (realizedPress)
                    score += 4000.f;
            }

            if (score > bestScore) {
                bestScore = score;
                result.down = e.hasRealized ? e.realized.at(0) : plan.at(0);
                result.predictedTicks = e.survived;
                result.certificateTicks =
                    complete ? cfg.horizon
                             : (eventCertified ? e.survived : minCertificateTicks);
                result.hasValidatedInput = true;
                result.completeContinuation = complete;
                result.recedingCertificate = !complete;
                result.plan = e.hasRealized ? e.realized : plan;
                result.selected = std::move(e);
            }

            bool warmCompatible = warm_.length <= 0;
            if (!warmCompatible) {
                const int n = std::min({24, warm_.length, plan.length});
                warmCompatible = n > 0;
                for (int k = 0; k < n && warmCompatible; ++k)
                    warmCompatible = plan.at(k) == warm_.at(k);
            }
            const bool currentRobust =
                robust >= .55f || nearClearance >= 16.f || safeClearance >= 18.f;
            const bool geometrySensitive = cfg.jumpEventCount > 0 || cfg.orbEventCount > 0 ||
                                           cfg.landingEventCount > 0;
            const bool comparedImmediateOrb =
                !cfg.immediateOrb || (immediateOrbEvaluated[0] && immediateOrbEvaluated[1]);
            const bool mayEarlyStop =
                comparedImmediateOrb &&
                (!geometrySensitive ||
                 (warm_.length > 0 && currentRobust && warmCompatible));
            // Receding certificates are intentionally compared against more
            // candidates. Only a full continuation may short-circuit search.
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

