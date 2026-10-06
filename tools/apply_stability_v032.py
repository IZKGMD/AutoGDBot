#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")

def patch(rel, old, new):
    p = root / rel
    s = p.read_text(encoding="utf-8")
    if new in s:
        return
    if old not in s:
        raise SystemExit(f"missing patch anchor in {rel}: {old[:100]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")

patch("src/controller.hpp",
'''    std::array<int, 16> orbEvents{};
    int orbEventCount = 0;
};
''',
'''    std::array<int, 16> orbEvents{};
    int orbEventCount = 0;
    std::array<int, 16> landingEvents{};
    int landingEventCount = 0;
};
''')

patch("src/controller.hpp",
'''            // Spread fallback candidates over the horizon. Geometry-derived
            // takeoff/orb events are inserted first, so route-aware plans are
            // checked before generic timing guesses.
            for (int t : {24, 48, 72, 96, 12, 36, 60, 84, 120, 144, 168, 192, 216,
                          6, 18, 1, 2, 3, 4, 8})
                pulseAt(t);
''',
'''            const int landingCount =
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
''')

patch("src/controller.hpp",
'''        cfg.orbEventCount = std::clamp(cfg.orbEventCount, 0, int(cfg.orbEvents.size()));
''',
'''        cfg.orbEventCount = std::clamp(cfg.orbEventCount, 0, int(cfg.orbEvents.size()));
        cfg.landingEventCount =
            std::clamp(cfg.landingEventCount, 0, int(cfg.landingEvents.size()));
''')

patch("src/controller.hpp",
'''            plans.size(), size_t(8 + std::max(0, cfg.jumpEventCount) +
                                 std::max(0, cfg.orbEventCount) + 16));
''',
'''            plans.size(), size_t(8 + std::max(0, cfg.jumpEventCount) +
                                 std::max(0, cfg.orbEventCount) +
                                 std::max(0, cfg.landingEventCount) + 20));
''')

patch("src/planning_hints.hpp",
'''inline void addOrbEvent(Config &cfg, int tick) {
    if (tick < 0 || tick >= cfg.horizon || cfg.orbEventCount >= int(cfg.orbEvents.size()))
        return;
    for (int i = 0; i < cfg.orbEventCount; ++i)
        if (cfg.orbEvents[i] == tick)
            return;
    cfg.orbEvents[cfg.orbEventCount++] = tick;
}
''',
'''inline void addOrbEvent(Config &cfg, int tick) {
    if (tick < 0 || tick >= cfg.horizon || cfg.orbEventCount >= int(cfg.orbEvents.size()))
        return;
    for (int i = 0; i < cfg.orbEventCount; ++i)
        if (cfg.orbEvents[i] == tick)
            return;
    cfg.orbEvents[cfg.orbEventCount++] = tick;
}
inline void addLandingEvent(Config &cfg, int tick) {
    if (tick < 0 || tick >= cfg.horizon ||
        cfg.landingEventCount >= int(cfg.landingEvents.size()))
        return;
    for (int i = 0; i < cfg.landingEventCount; ++i)
        if (std::abs(cfg.landingEvents[i] - tick) <= 2)
            return;
    cfg.landingEvents[cfg.landingEventCount++] = tick;
}

inline bool shouldUseReflexFallback(bool reflex, bool forceRelease, bool hasValidated,
                                    bool selectedDied, bool selectedInterrupted,
                                    int predictedTicks, int horizon) {
    if (!reflex || forceRelease)
        return false;
    if (!hasValidated)
        return true;
    const int minimumUsefulForecast = std::clamp(horizon / 7, 18, 36);
    return selectedDied || selectedInterrupted || predictedTicks < minimumUsefulForecast;
}
''')

patch("src/planning_hints.hpp",
'''        const float window = count >= 2
                                 ? std::clamp(std::abs(dx) * 92.f, 86.f, 132.f)
                                 : maxGap;
''',
'''        const float window = count >= 2
                                 ? std::clamp(std::abs(dx) * 72.f, 70.f, 104.f)
                                 : maxGap;
''')

patch("src/planning_hints.hpp",
'''    gap = findForwardGap(x, footY, dx, solids,
                         std::max(420.f, std::abs(dx) * float(cfg.horizon + 24)));
''',
'''    struct StepTarget { float tick, topY, width; };
    std::vector<StepTarget> steps;
    const float directionForSteps = dx > 0 ? 1.f : -1.f;
    for (auto b : solids) {
        const float top = b.maxY;
        const float width = b.maxX - b.minX;
        if (width < 6.f || width > 120.f)
            continue;
        if (top <= footY + 3.f || top > footY + 190.f)
            continue;
        const float center = (b.minX + b.maxX) * .5f;
        const float dist = (center - x) * directionForSteps;
        if (dist <= 8.f)
            continue;
        const float t = dist / std::abs(dx);
        if (t > 6.f && t < cfg.horizon)
            steps.push_back({t, top, width});
    }
    std::sort(steps.begin(), steps.end(), [](auto a, auto b) { return a.tick < b.tick; });
    float lastTop = footY;
    int acceptedSteps = 0;
    for (auto s : steps) {
        if (s.topY < lastTop - 8.f || s.topY > lastTop + 72.f)
            continue;
        addLandingEvent(cfg, int(std::lround(s.tick)));
        lastTop = std::max(lastTop, s.topY);
        if (++acceptedSteps >= 8)
            break;
    }

    gap = findForwardGap(x, footY, dx, solids,
                         std::max(420.f, std::abs(dx) * float(cfg.horizon + 24)));
''')

patch("src/main.cpp",
'''    void reset() {
        controller.reset();
''',
'''    void releaseLiveInput() {
        if (!layer)
            return;
        injected = true;
        layer->handleButton(false, 1, true);
        if (layer->m_gameState.m_isDualMode)
            layer->handleButton(false, 1, false);
        injected = false;
        for (auto *p : {layer->m_player1, layer->m_player2}) {
            if (p && p->m_holdingButtons[1])
                p->releaseButton(PlayerButton::Jump);
        }
        botHeld = false;
    }
    void reset() {
        releaseLiveInput();
        controller.reset();
''')

patch("src/main.cpp",
'''        botHeld = false;
        controlling = false;
''',
'''        botHeld = false;
        wasEnabled = false;
        controlling = false;
''')

patch("src/main.cpp",
'''        if (reflex && !searchConfig.forceRelease &&
            (!decision.hasValidatedInput || !decision.down)) {
''',
'''        const bool reflexRescue = shouldUseReflexFallback(
            reflex, searchConfig.forceRelease, decision.hasValidatedInput,
            decision.selected.died, decision.selected.interrupted,
            decision.predictedTicks, searchConfig.horizon);
        if (reflexRescue && !decision.down) {
''')

patch("src/main.cpp",
'''    void resetLevel() {
        if (m_fields->runtime)
            m_fields->runtime->reset();
        PlayLayer::resetLevel();
    }
''',
'''    void resetLevel() {
        if (m_fields->runtime)
            m_fields->runtime->releaseLiveInput();
        PlayLayer::resetLevel();
        if (m_fields->runtime)
            m_fields->runtime->reset();
    }
''')

patch("src/main.cpp", '"AutoGDBot 0.3.1 | {}\\nsearch=', '"AutoGDBot 0.3.2 | {}\\nsearch=')
patch("mod.json", '"version": "v0.3.1"', '"version": "v0.3.2"')

patch("tests/controller_tests.cpp",
'''    require(shouldReflexJump({0, 0, 30, 30}, 1.3f, doubleSpike),
            "adjacent floor spikes must open an earlier committed takeoff window");
''',
'''    require(shouldReflexJump({0, 0, 30, 30}, 1.3f, doubleSpike),
            "adjacent floor spikes must open an earlier committed takeoff window");
    require(!shouldUseReflexFallback(true, false, true, false, false, 216, 216),
            "reflex must never override a fully validated wait/route");
    require(shouldUseReflexFallback(true, false, false, false, false, 0, 216),
            "reflex may rescue the planner only when no route was validated");
''')

patch("tests/controller_tests.cpp",
'''    require(foundTwoPulse, "cube planner must propose takeoff-plus-orb two-pulse routes");
''',
'''    require(foundTwoPulse, "cube planner must propose takeoff-plus-orb two-pulse routes");

    Config stairCfg = ledgeCfg;
    stairCfg.horizon = 216;
    stairCfg.candidates = 64;
    stairCfg.jumpEventCount = 0;
    stairCfg.orbEventCount = 0;
    stairCfg.landingEventCount = 0;
    std::array<Bounds, 7> stair = {{{0, 0, 120, 60}, {145, 70, 175, 90},
                                    {205, 100, 235, 120}, {265, 130, 295, 150},
                                    {325, 160, 355, 180}, {385, 190, 415, 210},
                                    {445, 220, 475, 240}}};
    seedCubeEvents(stairCfg, 70, 60, 1.3f, stair, {});
    require(stairCfg.landingEventCount >= 4,
            "staircase lookahead must expose several future landing platforms");
    bool foundStepChain = false;
    for (auto const &plan : c.propose(stairCfg, false)) {
        if (!plan.at(0))
            continue;
        int covered = 0;
        for (int i = 0; i < stairCfg.landingEventCount; ++i) {
            const int t = stairCfg.landingEvents[i];
            bool buffered = false;
            for (int k = std::max(0, t - 6); k <= std::min(plan.length - 1, t + 6); ++k)
                buffered |= plan.at(k);
            covered += buffered;
        }
        foundStepChain |= covered >= 4;
    }
    require(foundStepChain,
            "cube planner must propose a takeoff plus buffered re-jumps across a staircase");
''')

print("AutoGDBot stability v0.3.2 patch applied")
