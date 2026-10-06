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
        raise SystemExit(f"v0.4 missing anchor in {rel}: {old[:120]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")

# Robust trajectory metrics: not merely alive/dead, but worst clearance and landing quality.
patch("src/controller.hpp",
'''    float clearance = 0;\n    int buttonChanges = -1;\n''',
'''    float clearance = 0;\n    float landingMargin = 0;\n    float robustness = 0;\n    int buttonChanges = -1;\n''')

# Dense timing neighborhoods around every geometry-derived event. This keeps the solver online
# but makes it much less likely to miss the narrow safe takeoff window by 1-2 physics ticks.
patch("src/controller.hpp",
'''            for (int i = 0; i < jumpCount; ++i)\n                pulseAt(cfg.jumpEvents[i]);\n            for (int i = 0; i < orbCount; ++i)\n                pulseAt(cfg.orbEvents[i]);\n''',
'''            for (int i = 0; i < jumpCount; ++i) {\n                const int t = cfg.jumpEvents[i];\n                for (int d : {0, -1, 1, -2, 2, -3, 3})\n                    pulseAt(t + d);\n            }\n            for (int i = 0; i < orbCount; ++i) {\n                const int t = cfg.orbEvents[i];\n                for (int d : {0, -1, 1, -2, 2})\n                    pulseAt(t + d);\n            }\n''')

# Survival stays authoritative, but robust geometric margin now matters much more than warm-plan
# stickiness. A route that merely grazes a spike must lose to a route with real margin.
patch("src/controller.hpp",
'''            float score = e.survived * 10000.f +\n                          std::clamp(e.clearance, 0.f, 100.f) * 18.f - changes * 12.f +\n                          continuity * 10.f;\n            if (exactWarm)\n                score += 5000.f;\n''',
'''            const float safeClearance = std::clamp(e.clearance, 0.f, 120.f);\n            const float safeLanding = std::clamp(e.landingMargin, 0.f, 80.f);\n            const float robust = std::clamp(e.robustness, 0.f, 1.f);\n            float score = e.survived * 10000.f +\n                          safeClearance * 85.f + safeLanding * 55.f + robust * 18000.f -\n                          changes * 18.f + continuity * 5.f;\n            if (exactWarm)\n                score += 1200.f;\n''')

# Add semantic map and use it as the static object index. Path choice itself is still per-tick.
patch("src/main.cpp",
'''#include "planning_hints.hpp"\n#include <fstream>\n''',
'''#include "planning_hints.hpp"\n#include "semantic_map.hpp"\n#include <fstream>\n''')

patch("src/main.cpp",
'''    std::vector<std::pair<float, GameObject *>> hintObjects;\n    std::vector<std::pair<float, GameObject *>> hintSolids;\n    std::vector<std::pair<float, GameObject *>> hintOrbs;\n''',
'''    SemanticMap semantic;\n    std::vector<std::pair<float, GameObject *>> hintObjects;\n    std::vector<std::pair<float, GameObject *>> hintSolids;\n    std::vector<std::pair<float, GameObject *>> hintOrbs;\n''')

patch("src/main.cpp",
'''        settings.read();\n        if (pl->m_objects)\n            for (unsigned i = 0; i < pl->m_objects->count(); ++i) {\n                auto *o = static_cast<GameObject *>(pl->m_objects->objectAtIndex(i));\n                if (o && (o->m_objectType == GameObjectType::Hazard ||\n                          o->m_objectType == GameObjectType::AnimatedHazard))\n                    hintObjects.emplace_back(o->getPositionX(), o);\n                if (o && (o->m_objectType == GameObjectType::Solid ||\n                          o->m_objectType == GameObjectType::Slope))\n                    hintSolids.emplace_back(o->getPositionX(), o);\n                if (o && orbObject(o->m_objectType))\n                    hintOrbs.emplace_back(o->getPositionX(), o);\n            }\n        std::sort(hintObjects.begin(), hintObjects.end(),\n                  [](auto const &a, auto const &b) { return a.first < b.first; });\n        std::sort(hintSolids.begin(), hintSolids.end(),\n                  [](auto const &a, auto const &b) { return a.first < b.first; });\n        std::sort(hintOrbs.begin(), hintOrbs.end(),\n                  [](auto const &a, auto const &b) { return a.first < b.first; });\n''',
'''        settings.read();\n        semantic.build(pl);\n        hintObjects = semantic.hazards();\n        hintSolids = semantic.solids();\n        hintOrbs = semantic.orbs();\n''')

# Track minimum hazard clearance over the whole native rollout rather than just at its final point.
patch("src/main.cpp",
'''        out.clearance = 100;\n        for (int t = 0; t < plan.length; ++t) {\n''',
'''        out.clearance = 120;\n        out.landingMargin = 0;\n        out.robustness = 1.f;\n        for (int t = 0; t < plan.length; ++t) {\n''')

patch("src/main.cpp",
'''                out.traces[i].push_back({p->getPositionX(), p->getPositionY()});\n                if (needsLanding[i]) {\n''',
'''                out.traces[i].push_back({p->getPositionX(), p->getPositionY()});\n                if (mode(p) == 0 && i == 0) {\n                    auto pos = p->getPosition();\n                    auto pr = p->getObjectRect(.35f, .35f);\n                    auto beginHaz = std::lower_bound(\n                        hintObjects.begin(), hintObjects.end(), pos.x - 100.f,\n                        [](auto const &a, float x) { return a.first < x; });\n                    int seen = 0;\n                    for (auto it = beginHaz; it != hintObjects.end() && it->first <= pos.x + 100.f && seen < 48; ++it) {\n                        auto *o = it->second;\n                        if (!o || o->m_isDisabled || o->m_isDisabled2)\n                            continue;\n                        auto r = o->getObjectRect();\n                        float dxr = std::max({r.getMinX() - pr.getMaxX(), pr.getMinX() - r.getMaxX(), 0.f});\n                        float dyr = std::max({r.getMinY() - pr.getMaxY(), pr.getMinY() - r.getMaxY(), 0.f});\n                        out.clearance = std::min(out.clearance, std::sqrt(dxr * dxr + dyr * dyr));\n                        ++seen;\n                    }\n                }\n                if (needsLanding[i]) {\n''')

# When a verified landing happens, estimate horizontal platform margin. Tiny edge landings are fragile.
patch("src/main.cpp",
'''                    if (leftGround[i] && p->m_isOnGround)\n                        landed[i] = true;\n''',
'''                    if (leftGround[i] && p->m_isOnGround) {\n                        landed[i] = true;\n                        if (i == 0) {\n                            auto pr = p->getObjectRect();\n                            const float px = p->getPositionX();\n                            auto first = std::lower_bound(\n                                hintSolids.begin(), hintSolids.end(), px - 80.f,\n                                [](auto const &a, float x) { return a.first < x; });\n                            for (auto it = first; it != hintSolids.end() && it->first <= px + 80.f; ++it) {\n                                auto *s = it->second;\n                                if (!s || s->m_isDisabled || s->m_isDisabled2)\n                                    continue;\n                                auto sr = s->getObjectRect();\n                                if (std::abs(sr.getMaxY() - pr.getMinY()) > 5.f)\n                                    continue;\n                                if (pr.getMaxX() < sr.getMinX() || pr.getMinX() > sr.getMaxX())\n                                    continue;\n                                const float left = pr.getMinX() - sr.getMinX();\n                                const float right = sr.getMaxX() - pr.getMaxX();\n                                out.landingMargin = std::max(out.landingMargin, std::max(0.f, std::min(left, right)));\n                            }\n                        }\n                    }\n''')

# Remove old final-position-only clearance pass; the whole-trajectory minimum above is stronger.
old = '''        // Native survival is authoritative only within this frozen-world\n        // rollout; the game can still disagree. Clearance is a tie-breaker.\n        if (out.survived && layer->m_objects) {\n            auto *p = clones[0].data();\n            auto pos = p->getPosition();\n            int checked = 0;\n            auto begin = std::lower_bound(hintObjects.begin(), hintObjects.end(), pos.x - 150,\n                                          [](auto const &a, float x) { return a.first < x; });\n            for (auto it = begin;\n                 it != hintObjects.end() && it->first <= pos.x + 150 && checked < 128; ++it) {\n                auto *o = it->second;\n                if (!o || o->m_isDisabled || std::abs(o->getPositionX() - pos.x) > 100)\n                    continue;\n                if (o->m_objectType != GameObjectType::Hazard &&\n                    o->m_objectType != GameObjectType::AnimatedHazard)\n                    continue;\n                ++checked;\n                auto r = o->getObjectRect();\n                float dx = std::max({r.getMinX() - pos.x, pos.x - r.getMaxX(), 0.f});\n                float dy = std::max({r.getMinY() - pos.y, pos.y - r.getMaxY(), 0.f});\n                out.clearance = std::min(out.clearance, std::sqrt(dx * dx + dy * dy));\n            }\n        }\n        return out;\n'''
new = '''        // Robustness is intentionally cheap: map the worst geometric margin to [0,1].\n        // Native collisions remain authoritative; this only ranks surviving candidates.\n        out.robustness = std::clamp(out.clearance / 28.f, 0.f, 1.f);\n        return out;\n'''
patch("src/main.cpp", old, new)

# Do full 240 TPS replanning only near actionable geometry. Safe cube flight keeps the cheaper 60 Hz path.
patch("src/main.cpp",
'''        if (pulseMode && !canPress && (ticks & 3) != 0) {\n''',
'''        bool nearAction = false;\n        if (pulseMode && !canPress) {\n            const float px = p->getPositionX();\n            auto h = std::lower_bound(hintObjects.begin(), hintObjects.end(), px - 12.f,\n                                      [](auto const &a, float x) { return a.first < x; });\n            nearAction = h != hintObjects.end() && h->first <= px + 230.f;\n            if (!nearAction) {\n                auto o = std::lower_bound(hintOrbs.begin(), hintOrbs.end(), px - 12.f,\n                                          [](auto const &a, float x) { return a.first < x; });\n                nearAction = o != hintOrbs.end() && o->first <= px + 230.f;\n            }\n        }\n        if (pulseMode && !canPress && !nearAction && (ticks & 3) != 0) {\n''')

# Spend extra CPU only when complex geometry is actually close.
patch("src/main.cpp",
'''            searchConfig.budgetMs = std::max(searchConfig.budgetMs, 1.35);\n''',
'''            searchConfig.budgetMs = std::max(searchConfig.budgetMs, nearAction ? 2.25 : 1.35);\n            if (nearAction)\n                searchConfig.candidates = std::max(searchConfig.candidates, 80);\n''')

patch("src/main.cpp", '"AutoGDBot 0.3.2 | {}\\nsearch=', '"AutoGDBot 0.4.0 | {}\\nsearch=')
patch("mod.json", '"version": "v0.3.2"', '"version": "v0.4.0"')
patch("mod.json", 'rolling-horizon Geometry Dash controller with native rollouts, route lookahead, gap/orb planning and live path rendering.',
      'Realtime semantic MPC controller with native rollouts, per-tick replanning, robust clearance scoring and live path rendering.')

# Standalone tests for robust scoring: equally-surviving plans must prefer real margin over a warm but fragile path.
patch("tests/controller_tests.cpp",
'''    auto heldRoute = sticky.decide(stickyCfg, false, equallySafe);\n    require(heldRoute.hasValidatedInput && heldRoute.plan.at(10),\n            "planner must keep the validated warm takeoff instead of jittering to a new timing");\n''',
'''    auto heldRoute = sticky.decide(stickyCfg, false, equallySafe);\n    require(heldRoute.hasValidatedInput && heldRoute.plan.at(10),\n            "planner must keep the validated warm takeoff instead of jittering to a new timing");\n\n    Controller robust;\n    Config robustCfg = stickyCfg;\n    addJumpEvent(robustCfg, 12);\n    auto robustEval = [](Plan const &plan, Clock::time_point) {\n        Evaluation e;\n        e.survived = plan.length;\n        if (plan.at(10)) {\n            e.clearance = 2;\n            e.landingMargin = 1;\n            e.robustness = .05f;\n        } else if (plan.at(12)) {\n            e.clearance = 32;\n            e.landingMargin = 16;\n            e.robustness = 1.f;\n        } else {\n            e.survived = std::min(plan.length, 14);\n            e.died = plan.length > 14;\n        }\n        return e;\n    };\n    auto robustDecision = robust.decide(robustCfg, false, robustEval);\n    require(robustDecision.hasValidatedInput && robustDecision.plan.at(12),\n            "robust MPC must prefer a wide-margin takeoff over a spike-grazing route");\n''')

print("AutoGDBot realtime semantic MPC v0.4 patch applied")
