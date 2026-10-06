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
constexpr int MaxHorizon = 240;
constexpr int MaxCandidates = 96;
using Clock = std::chrono::steady_clock;
struct Point {
    float x{}, y{};
};
struct Plan {
    std::array<uint8_t, MaxHorizon> buttons{};
    int length = 0;
    bool at(int tick) const {
        return buttons[std::clamp(tick, 0, MaxHorizon - 1)] != 0;
    }
};
struct Evaluation {
    int survived = 0;
    bool died = false;
    bool interrupted = false;
    float clearance = 0;
    int buttonChanges = -1;
    bool firstActionVerified = true;
    bool boundary = false;
    bool requiresLanding = false, landingVerified = false;
    std::array<std::vector<Point>, 2> traces;
};
struct Config {
    int horizon = 144, candidates = 48;
    double budgetMs = 1.0;
    bool forceRelease = false;
    bool cubePulsePlanning = false;
    std::array<int, 32> jumpEvents{};
    int jumpEventCount = 0;
    std::array<int, 16> orbEvents{};
    int orbEventCount = 0;
    std::array<int, 16> landingEvents{};
    int landingEventCount = 0;
};
struct Decision {
    bool down = false, hasValidatedInput = false, exhausted = false;
    bool completeContinuation = false;
    int candidates = 0, predictedTicks = 0;
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
        cfg.horizon = std::clamp(cfg.horizon, 24, MaxHorizon);
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
                if (std::equal(p.buttons.begin(), p.buttons.begin() + p.length,
                               old.buttons.begin()))
                    return;
            plans.push_back(p);
        };
        for (bool down : {held, !held}) {
            Plan p;
            p.buttons.fill(down);
            add(p);
        }
        Plan warm = warm_;
        int known = std::min(warm_.length, cfg.horizon);
        std::fill(warm.buttons.begin() + known, warm.buttons.end(),
                  cfg.cubePulsePlanning ? false : held);
        warm.length = cfg.horizon;
        add(warm);
        auto pulseAt = [&](int t) {
            if (t < 0 || t >= cfg.horizon)
                return;
            Plan p;
            p.buttons[t] = true;
            add(p);
        };
        if (cfg.cubePulsePlanning) {
            pulseAt(0);
            const int jumpCount = std::clamp(cfg.jumpEventCount, 0, int(cfg.jumpEvents.size()));
            const int orbCount = std::clamp(cfg.orbEventCount, 0, int(cfg.orbEvents.size()));
            for (int i = 0; i < jumpCount; ++i)
                pulseAt(cfg.jumpEvents[i]);
            for (int i = 0; i < orbCount; ++i)
                pulseAt(cfg.orbEvents[i]);
            // Orbs need two independent cube pulses: one to leave the ground and
            // a later one while touching the ring. Native rollouts still decide
            // whether the pair is actually survivable.
            int paired = 0;
            for (int i = 0; i < jumpCount && paired < 16; ++i) {
                for (int j = 0; j < orbCount && paired < 16; ++j) {
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
                for (int i = 0; i < jumpCount && i < 10; ++i)
                    addStepRoute(cfg.jumpEvents[i], 6);
            }

            // Spread fallback candidates over the horizon. Geometry-derived
            // takeoff/orb/landing events are inserted first.
            for (int t : {24, 48, 72, 96, 12, 36, 60, 84, 120, 144, 168, 192, 216,
                          6, 18, 1, 2, 3, 4, 8})
                pulseAt(t);
        } else {
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
        }
        for (int attempts = 0; int(plans.size()) < cfg.candidates && attempts < cfg.candidates * 16;
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
        return plans;
    }
    template <class Evaluator>
    Decision decide(Config cfg, bool held, Evaluator &&evaluate,
                    std::vector<float> const &priority = {}) {
        cfg.horizon = std::clamp(cfg.horizon, 24, MaxHorizon);
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
        auto plans = propose(cfg, held);
        Decision result;
        result.down = cfg.forceRelease ? false : held;
        float bestScore = -std::numeric_limits<float>::infinity();
        auto consider = [&](Plan const &plan, Evaluation e) {
            if (e.survived <= 0 || !e.firstActionVerified)
                return false;
            int changes = e.buttonChanges;
            if (changes < 0) {
                changes = 0;
                bool old = held;
                for (int k = 0; k < e.survived; ++k) {
                    changes += old != plan.at(k);
                    old = plan.at(k);
                }
            }
            bool complete = !e.died && !e.interrupted && (e.boundary || e.survived >= cfg.horizon);
            const int continuityTicks = std::min({e.survived, warm_.length, 64});
            int continuity = 0;
            for (int k = 0; k < continuityTicks; ++k)
                continuity += plan.at(k) == warm_.at(k);
            const bool exactWarm = continuityTicks > 0 && continuity == continuityTicks;
            float score = e.survived * 10000.f +
                          std::clamp(e.clearance, 0.f, 100.f) * 18.f - changes * 12.f +
                          continuity * 10.f;
            if (exactWarm)
                score += 5000.f;
            if (complete)
                score += 3000000.f;
            if (complete && e.landingVerified)
                score += 60000.f;
            if (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)))
                score += 100.f;
            if (score > bestScore) {
                bestScore = score;
                result.down = plan.at(0);
                result.predictedTicks = e.survived;
                result.hasValidatedInput = true;
                result.completeContinuation = complete;
                result.plan = plan;
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
            return complete && warmCompatible && result.plan.at(0) == plan.at(0) &&
                   (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)));
        };
        for (int i = 0; i < 2 && i < int(plans.size()) && Clock::now() < deadline; ++i) {
            Plan probe = plans[i];
            probe.length = std::min(8, probe.length);
            auto e = evaluate(probe, deadline);
            ++result.candidates;
            result.alternatives[probe.at(0)] = e;
            consider(probe, std::move(e));
        }
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
        for (int i : order) {
            if (Clock::now() >= deadline) {
                result.exhausted = true;
                break;
            }
            auto e = evaluate(plans[i], deadline);
            ++result.candidates;
            if (i < 2)
                result.alternatives[plans[i].at(0)] = e;
            if (consider(plans[i], std::move(e)))
                break;
        }
        if (result.hasValidatedInput) {
            warm_ = result.plan;
            warm_.length = std::min(warm_.length, result.selected.survived);
        }
        result.proposals = std::move(plans);
        result.elapsedMs = std::chrono::duration<double, std::milli>(Clock::now() - start).count();
        return result;
    }
};
} // namespace agb
