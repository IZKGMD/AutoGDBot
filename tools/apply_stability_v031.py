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
        raise SystemExit(f"missing patch anchor in {rel}: {old[:80]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")

# 1) Keep a validated warm route instead of accepting the first merely-safe
# continuation. Also make clearance meaningful for equal-survival plans.
patch("src/controller.hpp",
'''            float score =
                e.survived * 10000.f + std::clamp(e.clearance, 0.f, 100.f) * .01f - changes * 2.f;
            if (complete)
                score += 3000000.f;
            if (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)))
                score += 100.f;
''',
'''            const int continuityTicks = std::min({e.survived, warm_.length, 64});
            int continuity = 0;
            for (int k = 0; k < continuityTicks; ++k)
                continuity += plan.at(k) == warm_.at(k);
            float score = e.survived * 10000.f +
                          std::clamp(e.clearance, 0.f, 100.f) * 18.f - changes * 12.f +
                          continuity * 10.f;
            if (complete)
                score += 3000000.f;
            if (complete && e.landingVerified)
                score += 60000.f;
            if (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)))
                score += 100.f;
''')

patch("src/controller.hpp",
'''            // A fully checked continuation preserving the current action is
            // sufficient for this tick. Stop duplicating safe work on flat ground.
            return complete && result.plan.at(0) == plan.at(0) &&
                   (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)));
''',
'''            // Once a route has been validated, do not stop on a different
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
''')

# 2) Detect a cluster of low floor hazards. A pair of adjacent spikes gets a
# wider emergency window; a single spike keeps the old narrow reflex. Large
# saws remain excluded by height.
patch("src/planning_hints.hpp",
'''    const float maxGap = std::clamp(std::abs(dx) * 52.f, 48.f, 84.f);
    for (auto b : hazards) {
        const float hazardHeight = b.maxY - b.minY;
        if (hazardHeight > maxHazardHeight)
            continue; // large saw / tall obstacle: let the rollout time it
        if (b.maxY < player.minY + 1.f || b.minY > player.maxY + 14.f)
            continue;
        float gap = dx > 0 ? b.minX - player.maxX : player.minX - b.maxX;
        if (gap >= -std::abs(dx) * 2.f && gap <= maxGap)
            return true;
    }
    return false;
''',
'''    const float maxGap = std::clamp(std::abs(dx) * 52.f, 48.f, 84.f);
    std::vector<Bounds> low;
    for (auto b : hazards) {
        const float hazardHeight = b.maxY - b.minY;
        if (hazardHeight > maxHazardHeight)
            continue; // large saw / tall obstacle: let the rollout time it
        if (b.maxY < player.minY + 1.f || b.minY > player.maxY + 14.f)
            continue;
        low.push_back(b);
    }
    std::sort(low.begin(), low.end(), [](Bounds a, Bounds b) { return a.minX < b.minX; });
    for (size_t i = 0; i < low.size(); ++i) {
        size_t count = 1;
        float clusterEnd = low[i].maxX;
        for (size_t j = i + 1; j < low.size(); ++j) {
            if (low[j].minX - clusterEnd > 38.f)
                break;
            clusterEnd = std::max(clusterEnd, low[j].maxX);
            ++count;
        }
        float gap = dx > 0 ? low[i].minX - player.maxX : player.minX - low[i].maxX;
        const float window = count >= 2
                                 ? std::clamp(std::abs(dx) * 92.f, 86.f, 132.f)
                                 : maxGap;
        if (gap >= -std::abs(dx) * 2.f && gap <= window)
            return true;
    }
    return false;
''')

# Seed several earlier timings for adjacent low hazards, so native rollout can
# choose a comfortable arc over the whole cluster instead of reacting to spike 1.
patch("src/planning_hints.hpp",
'''    float nearestHazard = float(cfg.horizon + 100);
    float nearestLanding = nearestHazard;
''',
'''    struct FloorHazard { float nearDist, farDist; };
    std::vector<FloorHazard> floorHazards;
    const float direction = dx > 0 ? 1.f : -1.f;
    const float speed = std::abs(dx);
    for (auto b : hazards) {
        const float h = b.maxY - b.minY;
        if (h > 46.f || b.maxY < footY || b.minY > footY + 42.f)
            continue;
        float nearFace = dx > 0 ? b.minX : b.maxX;
        float farFace = dx > 0 ? b.maxX : b.minX;
        float nearDist = (nearFace - x) * direction;
        float farDist = (farFace - x) * direction;
        if (farDist > 0)
            floorHazards.push_back({nearDist, farDist});
    }
    std::sort(floorHazards.begin(), floorHazards.end(),
              [](auto a, auto b) { return a.nearDist < b.nearDist; });
    for (size_t i = 0; i < floorHazards.size();) {
        size_t j = i + 1;
        float end = floorHazards[i].farDist;
        while (j < floorHazards.size() && floorHazards[j].nearDist - end <= 38.f) {
            end = std::max(end, floorHazards[j].farDist);
            ++j;
        }
        if (j - i >= 2 && floorHazards[i].nearDist > 0) {
            int firstTick = int(std::floor(floorHazards[i].nearDist / speed));
            for (int lead : {40, 48, 56, 64, 72, 80, 88, 96})
                addJumpEvent(cfg, firstTick - lead);
        }
        i = j;
    }

    float nearestHazard = float(cfg.horizon + 100);
    float nearestLanding = nearestHazard;
''')

# 3) The independent guard is only a conservative supplement to native GD
# collision. Slightly inset visual AABBs to avoid rejecting a valid arc over
# triangular spike corners.
patch("src/hazard_guard.hpp",
'''            maxWidth_ = std::max(maxWidth_, b.maxX - b.minX);
            hazards_.push_back(b);
''',
'''            const float w = b.maxX - b.minX;
            const float h = b.maxY - b.minY;
            const float insetX = std::min(2.0f, w * .08f);
            const float insetY = std::min(2.0f, h * .08f);
            b.minX += insetX;
            b.maxX -= insetX;
            b.minY += insetY;
            b.maxY -= insetY;
            if (b.maxX <= b.minX || b.maxY <= b.minY)
                continue;
            maxWidth_ = std::max(maxWidth_, b.maxX - b.minX);
            hazards_.push_back(b);
''')

# 4) Calm renderer: cache one chosen trace, do not draw competing alternatives
# or dozens of future button diamonds.
patch("src/main.cpp",
'''    GapHint routeGap;
    int routeJumpEvents = 0, routeOrbEvents = 0;
''',
'''    GapHint routeGap;
    int routeJumpEvents = 0, routeOrbEvents = 0;
    std::vector<Point> stableTrace;
    uint64_t stableTraceTick = 0;
''')

patch("src/main.cpp",
'''        routeOrbEvents = 0;
        hazardGuard.set({});
''',
'''        routeOrbEvents = 0;
        stableTrace.clear();
        stableTraceTick = 0;
        hazardGuard.set({});
''')

patch("src/main.cpp", '"AutoGDBot 0.3.0 | {}\\nsearch=', '"AutoGDBot 0.3.1 | {}\\nsearch=')

patch("src/main.cpp",
'''        auto trace = [&](std::vector<Point> const &points, ccColor4F color, float width) {
            for (size_t i = 2; i < points.size(); i += 2)
''',
'''        if (layer->m_player1 && !stableTrace.empty()) {
            auto p = layer->m_player1->getPosition();
            if (std::hypot(stableTrace.front().x - p.x, stableTrace.front().y - p.y) > 70.f)
                stableTrace.clear();
        }
        if (decision.hasValidatedInput && decision.selected.traces[0].size() >= 6 &&
            (stableTrace.empty() || ticks >= stableTraceTick + 16)) {
            stableTrace = decision.selected.traces[0];
            stableTraceTick = ticks;
        }
        auto trace = [&](std::vector<Point> const &points, ccColor4F color, float width) {
            for (size_t i = 3; i < points.size(); i += 3)
''')

patch("src/main.cpp",
'''        trace(decision.alternatives[0].traces[0], {1, .25f, .3f, .4f}, .4f);
        trace(decision.alternatives[1].traces[0], {.35f, .6f, 1, .4f}, .4f);
        bool complete = decision.completeContinuation;
''',
'''        bool complete = decision.completeContinuation;
''')

patch("src/main.cpp",
'''        trace(decision.selected.traces[0], chosen, .7f);
        trace(decision.selected.traces[1],
              decision.selected.died ? chosen : ccColor4F{1, .7f, .2f, .9f}, .7f);
''',
'''        trace(stableTrace, chosen, .85f);
''')

patch("src/main.cpp",
'''            auto const &tracePoints = decision.selected.traces[0];
            for (int t = 0; t < decision.plan.length && size_t(t + 1) < tracePoints.size(); ++t) {
                if (!decision.plan.at(t))
                    continue;
                auto q = tracePoints[t + 1];
                std::array<CCPoint, 4> v = {
                    CCPoint{q.x - 4, q.y}, CCPoint{q.x, q.y + 4},
                    CCPoint{q.x + 4, q.y}, CCPoint{q.x, q.y - 4}};
                draw->drawPolygon(v.data(), 4, {.2f, 1.f, 1.f, .16f}, .7f,
                                  {.2f, 1.f, 1.f, .95f});
            }
''',
'''            // The selected route line is the only moving forecast overlay.
            // Input timing remains visible in the HUD instead of flickering as
            // a cloud of per-tick diamonds.
''')

# 5) Version/default rendering cadence.
patch("mod.json", '"version": "v0.3.0"', '"version": "v0.3.1"')
patch("mod.json", '"default": 30,\n      "min": 5,\n      "max": 60', '"default": 20,\n      "min": 5,\n      "max": 60')

# 6) Regression cases for clustered spikes and warm-plan stickiness.
patch("tests/controller_tests.cpp",
'''    std::array<Bounds, 1> saw{{{64, -4, 112, 50}}};
    require(!shouldReflexJump({0, 0, 30, 30}, 1.3f, saw),
            "large saws must be timed by native rollout instead of the early spike reflex");
''',
'''    std::array<Bounds, 1> saw{{{64, -4, 112, 50}}};
    require(!shouldReflexJump({0, 0, 30, 30}, 1.3f, saw),
            "large saws must be timed by native rollout instead of the early spike reflex");
    std::array<Bounds, 2> doubleSpike{{{112, 0, 132, 30}, {136, 0, 156, 30}}};
    require(shouldReflexJump({0, 0, 30, 30}, 1.3f, doubleSpike),
            "adjacent floor spikes must open an earlier committed takeoff window");
''')

patch("tests/controller_tests.cpp",
'''    c.reset();
    Config releaseCfg = cfg;
''',
'''    c.reset();
    Controller sticky;
    Config stickyCfg;
    stickyCfg.horizon = 48;
    stickyCfg.candidates = 16;
    stickyCfg.budgetMs = 8;
    stickyCfg.cubePulsePlanning = true;
    addJumpEvent(stickyCfg, 10);
    auto routeOnly = [](Plan const &plan, Clock::time_point) {
        Evaluation e;
        if (!plan.at(0) && plan.at(10)) {
            e.survived = plan.length;
            e.clearance = 50;
        } else {
            e.survived = std::min(plan.length, 18);
            e.died = plan.length > 18;
        }
        return e;
    };
    auto firstRoute = sticky.decide(stickyCfg, false, routeOnly);
    require(firstRoute.hasValidatedInput && firstRoute.plan.at(10),
            "geometry-timed route becomes the warm plan");
    auto equallySafe = [](Plan const &plan, Clock::time_point) {
        Evaluation e;
        e.survived = plan.length;
        e.clearance = 50;
        return e;
    };
    auto heldRoute = sticky.decide(stickyCfg, false, equallySafe);
    require(heldRoute.hasValidatedInput && heldRoute.plan.at(10),
            "planner must keep the validated warm takeoff instead of jittering to a new timing");

    c.reset();
    Config releaseCfg = cfg;
''')

print("AutoGDBot stability v0.3.1 patch applied")
