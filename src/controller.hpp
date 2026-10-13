#pragma once
#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <limits>
#include <vector>
#include <utility>
#include "mode_policy.hpp"

namespace agb {
constexpr int MaxPlanningHorizon = 768;
constexpr int MaxHorizon = 1024; // landing tail beyond the rolling lookahead
constexpr int MaxCandidates = 96;
#ifdef AUTOGDBOT_TEST_CLOCK
using Clock = AUTOGDBOT_TEST_CLOCK;
#else
using Clock = std::chrono::steady_clock;
#endif
struct Point {
    float x{}, y{};
};
struct MotionSample {
    double velocity = 0;
    bool canJump = false, grounded = false, buffered = false, ringUnused = false;
    uint64_t usedInteractions = 0;
    bool matches(MotionSample const &other) const {
        return std::isfinite(velocity) && std::isfinite(other.velocity) &&
            std::abs(velocity - other.velocity) <= .02 && canJump == other.canJump &&
            grounded == other.grounded && buffered == other.buffered && ringUnused == other.ringUnused &&
            usedInteractions == other.usedInteractions;
    }
};
struct OrbWindow { int begin = 0, end = 0; };

struct ControlOpportunity { int tick = 0; PlannerMode mode = PlannerMode::Generic; };
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
    int groundedPrefixTicks = 0; // contiguous native-confirmed UP + ground for both players
    int cubeTakeoffs = 0; // input-caused native ground-to-air transitions
    int supportLossTick = -1; // first native grounded-to-air transition without a press
    int cacheResumeTicks=0;
    bool died = false;
    bool guardDeath = false;
    bool nativeDeath = false;
    bool interrupted = false;
    float clearance = 0;
    float nearClearance = 0; // minimum hazard margin in the immediate control window
    float landingMargin = 0;
    int landingRunwayTicks = 0;
    float robustness = 0;
    int buttonChanges = -1;
    bool firstActionVerified = true;
    bool boundary = false;
    bool decisionPoint = false; // native state where the live controller can safely replan
    bool requiresLanding = false, landingVerified = false;
    bool portalVerified = false;
    int postPortalTicks = 0;
    uint64_t routeId = 0;
    std::array<std::vector<Point>, 2> traces;
    std::array<std::vector<MotionSample>, 2> motions;
    std::array<std::vector<ModeState>, 2> modeTraces;
    std::array<std::vector<int>, 2> discontinuities;
    Plan realized;
    bool hasRealized = false;
    int firstLandingTick = -1, landingCount = 0;
    std::vector<int> groundedActions; // native-confirmed opportunities for a continuation
    std::vector<int> orbActions;      // native-confirmed ring contact ticks
    std::vector<int> dashActions;     // contacts requiring a hold after the rising edge
    std::vector<ControlOpportunity> controlActions;
    std::vector<ControlOpportunity> transitions; // first input step in native new mode

};
inline uint64_t routeFingerprint(Plan const &p) {
    uint64_t h = 1469598103934665603ull;
    for (int i=0;i<std::clamp(p.length,0,MaxHorizon);++i) {h^=p.buttons[i];h*=1099511628211ull;}
    h ^= uint64_t(p.length);h*=1099511628211ull;
    h ^= uint64_t(p.landingDelay + 1);return h;
}
inline void advanceEvaluation(Evaluation &e, int ticks=1) {
    ticks=std::clamp(ticks,0,e.survived);
    auto shift=[&](auto &v){v.erase(v.begin(),v.begin()+std::min(size_t(ticks),v.size()));};
    for(auto &v:e.traces)shift(v);
    for(auto &v:e.motions)shift(v);
    for(auto &v:e.modeTraces)shift(v);
    for(auto &v:e.discontinuities){for(auto &t:v)t-=ticks;std::erase_if(v,[](int t){return t<=0;});}
    for(auto *v:{&e.orbActions,&e.dashActions,&e.groundedActions}){
        for(auto &t:*v)t-=ticks;std::erase_if(*v,[](int t){return t<0;});
    }
    for(auto *v:{&e.controlActions,&e.transitions}){
        for(auto &o:*v)o.tick-=ticks;std::erase_if(*v,[](auto o){return o.tick<0;});
    }
    e.supportLossTick=e.supportLossTick>=ticks?e.supportLossTick-ticks:-1;
    e.survived-=ticks;e.groundedPrefixTicks=std::max(0,e.groundedPrefixTicks-ticks);
    e.firstLandingTick=e.firstLandingTick>=ticks?e.firstLandingTick-ticks:-1;
    const int length=std::max(0,e.realized.length-ticks);
    for(int i=0;i<length;++i)e.realized.buttons[i]=e.realized.buttons[i+ticks];
    e.realized.length=length;
    std::fill(e.realized.buttons.begin()+length,e.realized.buttons.end(),false);
    e.routeId=routeFingerprint(e.realized);
}
struct Config {
    int horizon = 144, candidates = 48;
    double budgetMs = 1.0;
    Clock::time_point sharedDeadline = Clock::time_point::max();
    double tickSeconds = 1.0 / 240.0;
    bool forceRelease = false;
    bool cubePulsePlanning = false;
    bool immediateOrb = false; // touching or within the certified pre-contact orb window
    PlannerMode plannerMode = PlannerMode::Generic;
    PlannerMode secondaryMode = PlannerMode::Generic;
    bool dual = false;
    bool compareFirstActions = false;
    bool allowFlightRecovery = false;
    std::array<int, 32> jumpEvents{};
    int jumpEventCount = 0;
    std::array<int, 32> orbEvents{};
    int orbEventCount = 0;
    std::array<OrbWindow, 16> orbWindows{};
    int orbWindowCount = 0;
    std::array<int, 16> landingEvents{};
    int landingEventCount = 0;
};
struct Decision {
    bool forecastDied=false, forecastInterrupted=false;
    int proofKind=0;
    int coldVerificationTicks=0,cacheMismatches=0;
    bool down = false, hasValidatedInput = false, exhausted = false;
    bool completeContinuation = false;
    bool recedingCertificate = false;
    bool recoveryInput = false; // collision-checked prefix; landing is unproved
    int recoveryTicks = 0;
    int candidates = 0, predictedTicks = 0, certificateTicks = 0, rejectedUnsafe = 0;
    int bestRejectedTicks = 0, landingRejects = 0, deaths = 0;
    int repairsCreated = 0, repairsTested = 0;
    int guardDeaths = 0, nativeDeaths = 0, interruptions = 0;
    double elapsedMs = 0;
    std::array<int, 2> firstActionCandidates{};
    Plan plan;
    Evaluation selected;
    std::array<Evaluation, 2> alternatives;
    std::vector<Plan> proposals;
};
// A landing cannot hide a later requested orb/jump. Touchdown policies must
// run through their full active interval before ending at a safe landing.
inline int lastRequestedTick(Plan const &p) {
    if (p.landingDelay >= 0) return p.length - 1;
    for (int t = p.length - 1; t >= 0; --t) if (p.at(t)) return t;
    return -1;
}
inline bool flightRouteFinished(bool safeLanding, int completedTick, int lastRequest) {
    return safeLanding && completedTick >= lastRequest;
}
// Search tiers may improve a verified route, but failure in a later tier
// must not discard the earlier safe action in favor of an old coast queue.
inline bool preferVerifiedFallback(Decision const &current, Decision const &fallback) {
    return fallback.hasValidatedInput && fallback.selected.firstActionVerified &&
           !fallback.selected.died &&
           (!current.hasValidatedInput || current.selected.died);
}
inline void retainSearchDiagnostics(Decision &queued, Decision const &search) {
    queued.candidates = search.candidates;
    queued.repairsCreated = search.repairsCreated;
    queued.repairsTested = search.repairsTested;
    queued.coldVerificationTicks = search.coldVerificationTicks;
    queued.cacheMismatches = search.cacheMismatches;
    queued.firstActionCandidates = search.firstActionCandidates;
    queued.elapsedMs = search.elapsedMs;
    queued.rejectedUnsafe = search.rejectedUnsafe;
    queued.bestRejectedTicks = search.bestRejectedTicks;
    queued.landingRejects = search.landingRejects;
    queued.deaths = search.deaths;
    queued.guardDeaths = search.guardDeaths;
    queued.nativeDeaths = search.nativeDeaths;
    queued.interruptions = search.interruptions;
    queued.exhausted = search.exhausted;
    queued.alternatives = search.alternatives;
}
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
    void seedVerifiedPrefix(Plan const &plan, int verifiedTicks) {
        warm_ = plan;
        warm_.length = std::clamp(verifiedTicks, 0, std::min(plan.length, MaxHorizon));
        std::fill(warm_.buttons.begin() + warm_.length, warm_.buttons.end(), false);
    }
    std::vector<Plan> propose(Config cfg, bool held) {
        cfg.horizon = std::clamp(cfg.horizon, 24, MaxPlanningHorizon);
        cfg.candidates = std::clamp(cfg.candidates, 4, MaxCandidates);
        cfg.orbWindowCount = std::clamp(cfg.orbWindowCount, 0, int(cfg.orbWindows.size()));
        if (cfg.dual && cfg.secondaryMode != PlannerMode::Generic &&
            cfg.secondaryMode != cfg.plannerMode) {
            Config primary = cfg;
            primary.dual = false;
            primary.cubePulsePlanning = primary.plannerMode == PlannerMode::Cube;
            Config secondary = primary;
            secondary.plannerMode = cfg.secondaryMode;
            secondary.cubePulsePlanning = secondary.plannerMode == PlannerMode::Cube;
            auto a = propose(primary, held), b = propose(secondary, held);
            std::vector<Plan> merged;
            for (size_t i = 0; i < std::max(a.size(), b.size()); ++i) {
                for (auto const *list : {&a, &b}) {
                    if (i >= list->size() || int(merged.size()) >= cfg.candidates) continue;
                    auto const &p = (*list)[i];
                    bool duplicate = false;
                    for (auto const &old : merged)
                        duplicate |= old.buttons == p.buttons && old.landingDelay == p.landingDelay;
                    if (!duplicate) merged.push_back(p);
                }
            }
            return merged;
        }
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
            cfg.plannerMode == PlannerMode::Ufo || cfg.plannerMode == PlannerMode::Spider ||
            cfg.plannerMode == PlannerMode::Swing;
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

            // Cover a whole observed/mapped orb chain before refining single
            // edges. These are proposals only; native physics may skip/reject
            // vertically unreachable or optional rings.
            if (cfg.orbWindowCount > 0) {
                std::vector<OrbWindow> windows(cfg.orbWindows.begin(),
                    cfg.orbWindows.begin() + cfg.orbWindowCount);
                std::sort(windows.begin(), windows.end(), [](auto a, auto b) { return a.begin < b.begin; });
                for (int timing : {1, 0, 2}) {
                    for (int takeoff : {-1, 0, jumpCount ? cfg.jumpEvents[0] : -1}) {
                        Plan p;
                        if (takeoff >= 0 && takeoff < cfg.horizon) p.buttons[takeoff] = true;
                        int previous = takeoff;
                        for (auto w : windows) {
                            int at = timing == 0 ? w.begin : timing == 2 ? w.end : (w.begin + w.end) / 2;
                            at = std::max(at, previous + 2);
                            if (at < 0 || at >= cfg.horizon || at > w.end) continue;
                            p.buttons[at] = true; previous = at;
                        }
                        add(p);
                    }
                }
            }

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
            // Reserve immediate control before distant ring events. A large
            // orb array used to fill the entire cheap ship/robot action list.
            constant(false);
            if (!discretePulseMode) constant(true);
            else edgePulse(0, false);
            if (cfg.immediateOrb && held && !cfg.forceRelease) {
                edgePulse(1, discretePulseMode ? false : held);
                if (!discretePulseMode) edgePulse(1, false);
            }
            for (int i = 0; i < orbCount && i < 3; ++i) {
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

            case PlannerMode::Swing: {
                // Swing changes gravity on a rising edge. Holding is not a
                // thrust axis. Explore paired flips with an intervening UP.
                constant(false);
                edgePulse(0, false);
                for (int base : {1, 2, 4, 8, 12, 18, 24, 32, 48, 64, 96})
                    edgePulse(T(base), false);
                for (int first : {0, 4, 12, 24, 48})
                    for (int gap : {8, 16, 24, 40, 64}) {
                        Plan p;
                        const int a = first == 0 ? 0 : T(first);
                        const int b = a + T(gap);
                        p.buttons[a] = true;
                        if (b < cfg.horizon) p.buttons[b] = true;
                        add(p);
                    }
                for (int period : {12, 24, 40, 64, 96})
                    pulseTrain(T(period));
                break;
            }

            case PlannerMode::Dash:
                // Retain dash until a verified release window. A cube dash
                // must not be reduced to an ordinary one-tick jump pulse.
                constant(true);
                constant(false);
                for (int base : {1, 2, 4, 8, 12, 18, 24, 32, 48, 64, 96, 144, 192})
                    switchAt(T(base), true);
                break;

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
            {
                // Continuous modes need controlled switch times, short
                // corrections and periodic duty cycles. This deterministic
                // basis replaces the old pseudo-random mutation tail.
                constant(held);
                constant(!held);
                add(warm);
                Plan flipped = warm;
                flipped.buttons[0] = !warm.at(0);
                add(flipped);

                // Put recurrent steering in the first few slots. Previously
                // single-switch plans consumed all 10-40 fast/rescue slots
                // before PWM or a second correction could be proposed.
                if (cfg.plannerMode == PlannerMode::Ship) {
                    for (int basePeriod : {16, 32, 48}) {
                        const int period = T(basePeriod);
                        pwm(period, std::max(1, period / 2), 0);
                        pwm(period, std::max(1, period / 3), period / 2);
                    }
                } else {
                    for (int basePeriod : {24, 48, 72}) {
                        const int period = T(basePeriod);
                        pwm(period, std::max(1, period / 2), 0);
                    }
                }

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
        cfg.orbWindowCount = std::clamp(cfg.orbWindowCount, 0, int(cfg.orbWindows.size()));
        cfg.landingEventCount =
            std::clamp(cfg.landingEventCount, 0, int(cfg.landingEvents.size()));
        const auto start = Clock::now();
        const auto localDeadline =
            start +
            std::chrono::duration_cast<Clock::duration>(
                std::chrono::duration<double, std::milli>(std::clamp(cfg.budgetMs, 0.2, 8.0)));
        const auto deadline = std::min(localDeadline, cfg.sharedDeadline);
        if (start >= deadline) {
            Decision expired;
            expired.down = cfg.forceRelease || cfg.cubePulsePlanning ? false : held;
            expired.exhausted = true;
            return expired;
        }
        Config proposalConfig = cfg;
        const int expansionReserve = std::min(32, cfg.candidates / 3);
        proposalConfig.candidates -= expansionReserve;
        auto plans = propose(proposalConfig, held);
        const size_t initialPlanCount = plans.size();
        // Unevaluated proposals must not consume the opportunities to repair
        // a later native-discovered touchdown. Bound storage separately from
        // the actual native evaluation count and the common time deadline.
        const int repairPoolCapacity = cfg.candidates * 2;
        Decision result;
        result.down = cfg.forceRelease ? false : held;
        float bestScore = -std::numeric_limits<float>::infinity();
        Decision recovery;
        float recoveryScore = -std::numeric_limits<float>::infinity();
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
        const int minRecoveryTicks = std::max(4, int(std::ceil(.18 / safeTickSeconds)));

        auto consider = [&](Plan const &plan, Evaluation e) {
            // Releasing is also an action. If every complete flight is
            // rejected, retain the longest actually simulated native prefix
            // instead of silently replacing it with an untested release.
            // This deliberately does NOT claim a landing certificate.
            auto finiteTrace = [&](int player) {
                return !e.traces[player].empty() &&
                    std::all_of(e.traces[player].begin(), e.traces[player].end(), [](Point p) {
                        return std::isfinite(p.x) && std::isfinite(p.y);
                    });
            };
            if (cfg.allowFlightRecovery &&
                (modePolicy(cfg.plannerMode).mustLand ||
                 (cfg.dual && modePolicy(cfg.secondaryMode).mustLand)) &&
                e.requiresLanding && !e.firstActionVerified && e.hasRealized &&
                // Cube input commits a ballistic route. Neither a distant
                // known death nor an interrupted landing check permits a new
                // launch. Recovery can retain UP while an existing flight ends.
                !((cfg.plannerMode == PlannerMode::Cube ||
                   (cfg.dual && cfg.secondaryMode == PlannerMode::Cube)) && e.realized.at(0)) &&
                e.survived >= minRecoveryTicks && e.realized.length >= e.survived && finiteTrace(0) &&
                int(e.traces[0].size()) > e.survived &&
                (!cfg.dual || (int(e.traces[1].size()) > e.survived && finiteTrace(1)))) {
                const float score = float(e.survived) * 1000.f +
                    std::clamp(e.nearClearance, 0.f, 120.f) * 20.f;
                if (score > recoveryScore) {
                    recoveryScore = score;
                    recovery.recoveryInput = true;
                    recovery.down = e.realized.at(0);
                    recovery.recoveryTicks = std::min(minRecoveryTicks, e.survived);
                    recovery.predictedTicks = e.survived;
                    recovery.plan = e.realized;
                    recovery.plan.length = recovery.recoveryTicks;
                    recovery.selected = e;
                }
            }
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
            const bool groundedWaitCertified = cfg.cubePulsePlanning && !e.died &&
                e.firstActionVerified && !e.requiresLanding &&
                !(e.hasRealized ? e.realized.at(0) : plan.at(0)) &&
                e.groundedPrefixTicks >= minCertificateTicks &&
                e.groundedPrefixTicks <= e.survived;
            const bool portalSafe = e.transitions.empty() || e.portalVerified;
            const bool certified = portalSafe &&
                (complete || eventCertified || prefixCertified || groundedWaitCertified);

            if (e.survived <= 0 || !e.firstActionVerified || !certified) {
                ++result.rejectedUnsafe;
                result.bestRejectedTicks = std::max(result.bestRejectedTicks, e.survived);
                result.landingRejects += !e.firstActionVerified;
                result.deaths += e.died;
                result.guardDeaths += e.guardDeath;
                result.nativeDeaths += e.nativeDeath;
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
            // Landing is a safety obligation, not progress by itself. Rewarding
            // it made equally safe empty-floor plans schedule needless jumps.
            if (cfg.cubePulsePlanning && !cfg.immediateOrb) {
                int takeoffs = e.cubeTakeoffs;
                if (e.modeTraces[0].empty() && takeoffs == 0) {
                    // Standalone evaluators may supply only the realized input.
                    auto const &input = e.hasRealized ? e.realized : plan;
                    bool previous = held;
                    for (int t=0;t<std::min(e.survived,input.length);++t) {
                        takeoffs += !previous && input.at(t);
                        previous = input.at(t);
                    }
                }
                score -= 160000.f * takeoffs;
            }
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
                    complete ? std::min(cfg.horizon, e.survived)
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
                (!cfg.compareFirstActions ||
                 (immediateOrbEvaluated[0] && immediateOrbEvaluated[1])) &&
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
        if ((cfg.compareFirstActions || cfg.immediateOrb) && !cfg.forceRelease && !order.empty()) {
            auto opposite = std::find_if(order.begin() + 1, order.end(), [&](int i) {
                return plans[i].at(0) != plans[order.front()].at(0);
            });
            if (opposite != order.end()) std::rotate(order.begin() + 1, opposite, opposite + 1);
        }
        int furthestFailure = -1;
        std::array<int, 2> furthestControlFailure{-1, -1};
        // Keep the initial action basis ahead of adaptive repairs. Repeated
        // repairs of one failed cached coast used to starve the opposite
        // first action on Android (only 2-4 forecasts fit in one substep).
        const size_t basisEnd = std::min(cfg.forceRelease ? size_t(1) : size_t(2), order.size());
        auto enqueueRepairs = [&](size_t cursor, std::vector<int> const &children, bool transition = false) {
            const bool compared = cfg.forceRelease ||
                (immediateOrbEvaluated[0] && immediateOrbEvaluated[1]);
            if (transition && (compared || cfg.forceRelease)) {
                order.insert(order.begin() + cursor + 1, children.begin(), children.end());
                return;
            }
            if (!compared || cursor + 1 < basisEnd)
                order.insert(order.end(), children.begin(), children.end());
            else
                order.insert(order.begin() + cursor + 1, children.begin(), children.end());
        };
        for (size_t cursor = 0; cursor < order.size(); ++cursor) {
            const int i = order[cursor];
            if (Clock::now() >= deadline || result.candidates >= cfg.candidates) {
                result.exhausted = true;
                break;
            }
            auto candidateDeadline = deadline;
            if ((cfg.compareFirstActions || cfg.immediateOrb) && !cfg.forceRelease &&
                !immediateOrbEvaluated[!plans[i].at(0)]) {
                const auto now = Clock::now();
                candidateDeadline = now + (deadline - now) / 2;
            }
            auto e = evaluate(plans[i], candidateDeadline);
            e.routeId = routeFingerprint(e.hasRealized ? e.realized : plans[i]);
            ++result.candidates;
            result.repairsTested += size_t(i) >= initialPlanCount;
            ++result.firstActionCandidates[plans[i].at(0) ? 1 : 0];
            immediateOrbEvaluated[plans[i].at(0) ? 1 : 0] = true;
            auto &alternative = result.alternatives[plans[i].at(0)];
            if (e.survived > alternative.survived || alternative.traces[0].empty())
                alternative = e;

            // Like gdsolver's state frontier, branch from the actual transition
            // reached by this lineage, retaining its exact native input prefix.
            // Never apply the old cube pulse policy to a new ship/wave section.
            if (!e.transitions.empty() && (e.died || e.interrupted || !e.firstActionVerified || !e.portalVerified) &&
                int(plans.size()) < repairPoolCapacity) {
                std::vector<int> children;
                for(auto o:e.transitions) {
                    const int at=o.tick;
                    if(at<1 || at>=MaxHorizon) continue;
                    const auto kind=modePolicy(o.mode).input;
                    const bool edge=kind==InputKind::GroundPulse || kind==InputKind::GroundEdge || kind==InputKind::AirEdge;
                    for(int variant=0;variant<(edge?4:2);++variant) {
                        if(int(plans.size())>=repairPoolCapacity || children.size()>=8)break;
                        Plan child=plans[i];
                        child.length=std::min(MaxHorizon, std::max({cfg.horizon, plans[i].length,
                            at + std::max(4, int(std::ceil(.18/safeTickSeconds))) + 1}));
                        child.landingDelay=-1;
                        if(e.hasRealized)std::copy_n(e.realized.buttons.begin(),at,child.buttons.begin());
                        std::fill(child.buttons.begin()+at,child.buttons.end(),!edge && variant==1);
                        if(edge && variant>0) {
                            const int press=at+(variant==1?0:variant==2?4:12);
                            if(press>=child.length)continue;
                            if(press>0)child.buttons[press-1]=false;
                            child.buttons[press]=true;
                        }
                        bool duplicate=false;for(auto const &old:plans)
                            duplicate|=old.buttons==child.buttons && old.landingDelay==child.landingDelay;
                        if(duplicate)continue;
                        children.push_back(int(plans.size()));plans.push_back(child);
                    }
                }
                enqueueRepairs(cursor,children,true);
            }

            // Event-driven expansion: if native clone physics actually touches
            // an orb on a rejected route, branch exactly at that contact tick.
            // This replaces fragile "orb X coordinate == press time" guessing.
            if (
                (e.died || e.interrupted || !e.firstActionVerified) &&
                !e.orbActions.empty() &&
                int(plans.size()) < repairPoolCapacity) {
                std::vector<int> children;
                int emitted = 0;
                for (auto it = e.orbActions.rbegin();
                     it != e.orbActions.rend() && emitted < 6 &&
                     int(plans.size()) < repairPoolCapacity; ++it) {
                    const int at = *it;
                    if (at < 0 || at >= MaxHorizon)
                        continue;
                    const bool dash = std::find(e.dashActions.begin(), e.dashActions.end(), at) !=
                                      e.dashActions.end();
                    if (!dash && plans[i].at(at) && (at == 0 || !plans[i].at(at - 1)))
                        continue;
                    for (int hold : {1, 8, 24, 64, 144, 240}) {
                        if (!dash && hold != 1) break;
                        if (int(plans.size()) >= repairPoolCapacity) break;
                        Plan child = plans[i];
                        // Keep the observed prefix and the original future
                        // steering. Clearing the whole tail destroyed ship
                        // thrust and the next action in an orb chain.
                        if (e.hasRealized)
                            std::copy_n(e.realized.buttons.begin(), at, child.buttons.begin());
                        child.length = std::min(MaxHorizon, std::max({cfg.horizon,
                            plans[i].length, at + 1}));
                        child.landingDelay = -1;
                        if (at > 0) child.buttons[at - 1] = false;
                        const int end = std::min(child.length, at + hold);
                        std::fill(child.buttons.begin() + at, child.buttons.begin() + end, true);
                        if (end < child.length) child.buttons[end] = false;
                        bool duplicate = false;
                        for (auto const &old : plans)
                            duplicate |= old.buttons == child.buttons &&
                                         old.landingDelay == child.landingDelay;
                        if (duplicate) continue;
                        children.push_back(int(plans.size()));
                        plans.push_back(child);
                        ++emitted;
                    }
                }
                enqueueRepairs(cursor, children);
            }

            // Repair failed native trajectories at mode-valid control times.
            // This can add multiple non-periodic corrections, preserving the
            // already observed prefix instead of replacing the whole route.
            const int first = plans[i].at(0) ? 1 : 0;
            if (e.died && e.survived > furthestControlFailure[first] &&
                !e.controlActions.empty() && int(plans.size()) < repairPoolCapacity) {
                furthestControlFailure[first] = e.survived;
                std::vector<int> children;
                std::vector<PlannerMode> observedModes{cfg.plannerMode};
                if (cfg.dual) observedModes.push_back(cfg.secondaryMode);
                for (auto o : e.controlActions)
                    if (std::find(observedModes.begin(), observedModes.end(), o.mode) == observedModes.end())
                        observedModes.push_back(o.mode);
                for (int baseLead : {24, 48, 12, 64, 96, 8}) {
                    const int lead = std::max(1, int(std::lround((baseLead / 240.0) / safeTickSeconds)));
                    const int target = std::max(0, e.survived - lead);
                    for (PlannerMode m : observedModes) {
                        if (m == PlannerMode::Generic) continue;
                        auto it = std::find_if(e.controlActions.rbegin(), e.controlActions.rend(),
                            [&](ControlOpportunity o) { return o.mode == m && o.tick <= target; });
                        if (it == e.controlActions.rend()) continue;
                        const int at = it->tick;
                        if (at < 0 || at >= MaxHorizon) continue;
                        const auto input = modePolicy(m).input;
                        for (int duration : {1, 8, 24, 64}) {
                            const bool edge = input == InputKind::GroundPulse ||
                                input == InputKind::GroundEdge || input == InputKind::AirEdge;
                            if (edge && duration != 1) break;
                            if (int(plans.size()) >= repairPoolCapacity || children.size() >= 8) break;
                            Plan child = plans[i];
                            if (e.hasRealized)
                                std::copy_n(e.realized.buttons.begin(), at, child.buttons.begin());
                            child.length = std::min(MaxHorizon, std::max({cfg.horizon,
                                plans[i].length, at + 1}));
                            child.landingDelay = -1;
                            const bool down = edge || input == InputKind::JumpHold ? true : !child.at(at);
                            if ((edge || input == InputKind::JumpHold) && at > 0) child.buttons[at - 1] = false;
                            const int hold = std::max(1, int(std::lround((duration / 240.0) / safeTickSeconds)));
                            const int end = std::min(child.length, at + hold);
                            std::fill(child.buttons.begin() + at, child.buttons.begin() + end, down);
                            if (end < child.length && (edge || input == InputKind::JumpHold)) child.buttons[end] = false;
                            bool duplicate = false;
                            for (auto const &old : plans)
                                duplicate |= old.buttons == child.buttons && old.landingDelay == child.landingDelay;
                            if (duplicate) continue;
                            children.push_back(int(plans.size()));
                            plans.push_back(child);
                        }
                    }
                }
                enqueueRepairs(cursor, children);
            }

            // The same mechanism remains for native-confirmed touchdowns.
            if (cfg.cubePulsePlanning && e.died && e.survived > furthestFailure &&
                !e.groundedActions.empty() && int(plans.size()) < repairPoolCapacity) {
                furthestFailure = e.survived;
                std::vector<int> children;
                for (int lead : {24, 32, 16, 40, 8, 48, 0, 56}) {
                    const int index = std::max(0, int(e.groundedActions.size()) - 1 - lead);
                    const int at = e.groundedActions[index];
                    if (at < 1 || at >= MaxHorizon || plans[i].at(at))
                        continue;
                    Plan child = e.hasRealized ? e.realized : plans[i];
                    child.length = std::min(MaxHorizon, std::max({cfg.horizon,
                        plans[i].length, at + 1}));
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
                    if (int(plans.size()) >= repairPoolCapacity)
                        break;
                }
                enqueueRepairs(cursor, children);
            }
            if (consider(plans[i], std::move(e)))
                break;
        }
        if (result.hasValidatedInput && result.completeContinuation) {
            warm_ = result.plan;
            warm_.length = std::min(warm_.length, result.selected.survived);
        }
        if (!result.hasValidatedInput) {
            const bool compared = immediateOrbEvaluated[0] && immediateOrbEvaluated[1];
            if (recovery.recoveryInput && compared && !cfg.forceRelease) {
                retainSearchDiagnostics(recovery, result);
                result = std::move(recovery);
                warm_ = result.plan;
                std::fill(warm_.buttons.begin() + warm_.length, warm_.buttons.end(), false);
            } else {
                warm_ = {};
                if (cfg.cubePulsePlanning) result.down = false;
            }
        }
        result.repairsCreated = int(plans.size() - initialPlanCount);
        result.proposals = std::move(plans);
        result.elapsedMs = std::chrono::duration<double, std::milli>(Clock::now() - start).count();
        return result;
    }
};
} // namespace agb

