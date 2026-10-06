#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
main = root / 'src' / 'main.cpp'
tests = root / 'tests' / 'controller_tests.cpp'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'route-v0.3 patch mismatch: {label}')
    return text.replace(old, new, 1)


def patch_main() -> bool:
    s = main.read_text(encoding='utf-8')
    if 'AutoGDBot 0.3.0 | {}' in s and 'hintOrbs' in s and 'routeGap' in s:
        print('src/main.cpp already route-v0.3')
        return False

    s = replace_once(s, 'static bool boundaryObject(GameObject *o) {', r'''static bool orbObject(GameObjectType t) {
    switch (t) {
    case GameObjectType::YellowJumpRing:
    case GameObjectType::PinkJumpRing:
    case GameObjectType::GravityRing:
    case GameObjectType::GreenRing:
    case GameObjectType::DropRing:
    case GameObjectType::RedJumpRing:
    case GameObjectType::SpiderOrb:
    case GameObjectType::DashRing:
    case GameObjectType::GravityDashRing:
    case GameObjectType::CustomRing:
    case GameObjectType::TeleportOrb:
        return true;
    default:
        return false;
    }
}
static bool boundaryObject(GameObject *o) {''', 'orbObject')

    s = s.replace('bool enabled = false, traces = true, gpu = true, telemetry = false;',
                  'bool enabled = false, gpu = true, telemetry = false;', 1)
    s = s.replace('        traces = m->getSettingValue<bool>("traces");\n', '', 1)

    s = replace_once(s,
        'std::vector<std::pair<float, GameObject *>> hintSolids;',
        'std::vector<std::pair<float, GameObject *>> hintSolids;\n'
        '    std::vector<std::pair<float, GameObject *>> hintOrbs;\n'
        '    GapHint routeGap;\n'
        '    int routeJumpEvents = 0, routeOrbEvents = 0;',
        'runtime route fields')

    s = replace_once(s, '''                if (o && o->m_objectType == GameObjectType::Solid)
                    hintSolids.emplace_back(o->getPositionX(), o);''', '''                if (o && (o->m_objectType == GameObjectType::Solid ||
                          o->m_objectType == GameObjectType::Slope))
                    hintSolids.emplace_back(o->getPositionX(), o);
                if (o && orbObject(o->m_objectType))
                    hintOrbs.emplace_back(o->getPositionX(), o);''', 'collect solids/orbs')

    s = replace_once(s, '''        std::sort(hintSolids.begin(), hintSolids.end(),
                  [](auto const &a, auto const &b) { return a.first < b.first; });''', '''        std::sort(hintSolids.begin(), hintSolids.end(),
                  [](auto const &a, auto const &b) { return a.first < b.first; });
        std::sort(hintOrbs.begin(), hintOrbs.end(),
                  [](auto const &a, auto const &b) { return a.first < b.first; });''', 'sort orbs')

    s = replace_once(s, '''        predictionError = 0;
        hazardGuard.set({});''', '''        predictionError = 0;
        routeGap = {};
        routeJumpEvents = 0;
        routeOrbEvents = 0;
        hazardGuard.set({});''', 'reset route state')

    s = replace_once(s, '''        if (mode(p) == 0)
            searchConfig.horizon = std::max(searchConfig.horizon, 144);''', '''        if (mode(p) == 0) {
            // Keep planning while the cube is still on safe ground. The longer
            // rolling horizon sees the edge/gap/orb before the takeoff is urgent.
            searchConfig.horizon = std::max(searchConfig.horizon, 216);
            searchConfig.candidates = std::max(searchConfig.candidates, 64);
            searchConfig.budgetMs = std::max(searchConfig.budgetMs, 1.35);
        }''', 'cube horizon')

    s = replace_once(s, '''        if (pulseMode && canPress) {
            std::vector<Bounds> solids;
            auto firstSolid =
                std::lower_bound(hintSolids.begin(), hintSolids.end(), originX - range,
                                 [](auto const &a, float x) { return a.first < x; });
            for (auto it = firstSolid; it != hintSolids.end() && it->first <= originX + range;
                 ++it) {
                auto *o = it->second;
                if (!o || o->m_isDisabled || o->m_isDisabled2)
                    continue;
                auto r = o->getObjectRect();
                solids.push_back({r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()});
            }
            int players = layer->m_gameState.m_isDualMode ? 2 : 1;
            for (int i = 0; i < players; ++i) {
                auto *real = i == 0 ? p : layer->m_player2;
                if (real && real->m_isOnGround)
                    seedCubeEvents(searchConfig, real->getPositionX(),
                                   real->getObjectRect().getMinY(), travelSteps[i].x, solids,
                                   guardBounds);
            }
        }''', '''        routeGap = {};
        routeJumpEvents = 0;
        routeOrbEvents = 0;
        if (pulseMode && canPress) {
            std::vector<Bounds> solids;
            std::vector<Bounds> orbs;
            auto firstSolid =
                std::lower_bound(hintSolids.begin(), hintSolids.end(), originX - range,
                                 [](auto const &a, float x) { return a.first < x; });
            for (auto it = firstSolid; it != hintSolids.end() && it->first <= originX + range;
                 ++it) {
                auto *o = it->second;
                if (!o || o->m_isDisabled || o->m_isDisabled2)
                    continue;
                auto r = o->getObjectRect();
                solids.push_back({r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()});
            }
            auto firstOrb =
                std::lower_bound(hintOrbs.begin(), hintOrbs.end(), originX - range,
                                 [](auto const &a, float x) { return a.first < x; });
            for (auto it = firstOrb; it != hintOrbs.end() && it->first <= originX + range; ++it) {
                auto *o = it->second;
                if (!o || o->m_isDisabled || o->m_isDisabled2 || o->m_isActivated)
                    continue;
                auto r = o->getObjectRect();
                orbs.push_back({r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()});
            }
            int players = layer->m_gameState.m_isDualMode ? 2 : 1;
            for (int i = 0; i < players; ++i) {
                auto *real = i == 0 ? p : layer->m_player2;
                if (real && real->m_isOnGround) {
                    auto gap = seedCubeEvents(searchConfig, real->getPositionX(),
                                              real->getObjectRect().getMinY(), travelSteps[i].x,
                                              solids, guardBounds, orbs);
                    if (i == 0)
                        routeGap = gap;
                }
            }
            routeJumpEvents = searchConfig.jumpEventCount;
            routeOrbEvents = searchConfig.orbEventCount;
        }''', 'route geometry seeding')

    s = s.replace('"AutoGDBot 0.2.1 | {}\\nsearch={:.2f} total={:.2f} ms | {} candidates | {} ticks | "',
                  '"AutoGDBot 0.3.0 | {}\\nsearch={:.2f} total={:.2f} ms | {} candidates | {} ticks | "', 1)
    s = replace_once(s, '''                "error={:.2f} | guard={} | light={} | reflex={} | forecast={} | landing={}",''', '''                "error={:.2f} | guard={} | light={} | reflex={} | forecast={} | landing={}\\n"
                "route: jumps={} orbs={} gap={}",''', 'status route line')
    s = replace_once(s, '''                decision.selected.requiresLanding
                    ? (decision.selected.landingVerified ? "OK" : "NO")
                    : "n/a")''', '''                decision.selected.requiresLanding
                    ? (decision.selected.landingVerified ? "OK" : "NO")
                    : "n/a",
                routeJumpEvents, routeOrbEvents, routeGap.found ? "YES" : "no")''', 'status route args')
    s = replace_once(s, '''        if (!settings.traces || !settings.enabled)
            return;''', '''        if (!settings.enabled)
            return;''', 'mandatory rendering')

    marker = '''
        for (auto *p : {layer->m_player1, layer->m_player2}) {'''
    extra = r'''
        if (layer->m_player1) {
            float x = layer->m_player1->getPositionX();
            int shownOrbs = 0;
            auto beginOrb = std::lower_bound(hintOrbs.begin(), hintOrbs.end(), x - 80,
                                             [](auto const &a, float q) { return a.first < q; });
            for (auto it = beginOrb;
                 it != hintOrbs.end() && it->first <= x + 900 && shownOrbs < 48; ++it) {
                auto *o = it->second;
                if (!o || o->m_isDisabled || o->m_isActivated)
                    continue;
                auto r = o->getObjectRect();
                std::array<CCPoint, 4> v = {
                    CCPoint{r.getMinX(), r.getMinY()}, CCPoint{r.getMinX(), r.getMaxY()},
                    CCPoint{r.getMaxX(), r.getMaxY()}, CCPoint{r.getMaxX(), r.getMinY()}};
                draw->drawPolygon(v.data(), 4, {0, 0, 0, 0}, .6f, {.2f, .9f, 1.f, .75f});
                ++shownOrbs;
            }
            if (routeGap.found) {
                float y = layer->m_player1->getPositionY();
                draw->drawSegment({routeGap.edgeX, y - 80}, {routeGap.edgeX, y + 120}, .7f,
                                  {1.f, .75f, .15f, .8f});
                draw->drawSegment({routeGap.landingX, y - 80}, {routeGap.landingX, y + 120}, .5f,
                                  {.4f, 1.f, .55f, .55f});
            }
            auto const &tracePoints = decision.selected.traces[0];
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
        }
'''
    s = replace_once(s, marker, extra + marker, 'render orbs/gap/pulses')
    main.write_text(s, encoding='utf-8')
    print('patched src/main.cpp -> route-v0.3')
    return True


def patch_tests() -> bool:
    s = tests.read_text(encoding='utf-8')
    if 'large saws must be timed by native rollout' in s and 'cube planner must propose takeoff-plus-orb' in s:
        print('tests already route-v0.3')
        return False
    s = replace_once(s, '''    std::array<Bounds, 1> farSpike{{{130, 0, 154, 30}}};
    require(!shouldReflexJump({0, 0, 30, 30}, 1.3f, farSpike),
            "ground reflex must wait until a distant spike enters the takeoff window");''', '''    std::array<Bounds, 1> farSpike{{{130, 0, 154, 30}}};
    require(!shouldReflexJump({0, 0, 30, 30}, 1.3f, farSpike),
            "ground reflex must wait until a distant spike enters the takeoff window");
    std::array<Bounds, 1> saw{{{64, -4, 112, 50}}};
    require(!shouldReflexJump({0, 0, 30, 30}, 1.3f, saw),
            "large saws must be timed by native rollout instead of the early spike reflex");''', 'saw regression test')
    s = replace_once(s, '''    require(ledgeCfg.jumpEventCount > 0 && ledgeCfg.jumpEvents[0] == 48,
            "merge adjoining support tiles and prioritize takeoff near the actual platform edge");''', '''    require(ledgeCfg.jumpEventCount > 0 && ledgeCfg.jumpEvents[0] == 48,
            "merge adjoing support tiles and prioritize takeoff near the actual platform edge");
    std::array<Bounds, 5> gapSupport = {
        {{0, 0, 30, 60}, {30, 0, 60, 60}, {60, 0, 90, 60}, {90, 0, 120, 60},
          {190, 0, 230, 60}}};
    auto gap = findForwardGap(55, 60, 1.3f, gapSupport);
    require(gap.found && gap.edgeX == 120 && gap.width > 60,
            "route lookahead must expose a platform gap before the cube falls off it");
    Config orbCfg = ledgeCfg;
    orbCfg.jumpEventCount = 0;
    orbCfg.orbEventCount = 0;
    std::array<Bounds, 1> orb{{{146, 80, 166, 100}}};
    seedCubeEvents(orbCfg, 55, 60, 1.3f, support, {}, orb);
    require(orbCfg.orbEventCount > 0 && orbCfg.jumpEventCount > 0,
            "orb routing must seed both takeoff and orb-activation events");
    bool foundTwoPulse = false;
    for (auto const &plan : c.propose(orbCfg, false)) {
        int pulses = 0;
        for (int t = 0; t < plan.length; ++t)
            pulses += plan.at(t);
        foundTwoPulse |= pulses >= 2;
    }
    require(foundTwoPulse, "cube planner must propose takeoff-plus-orb two-pulse routes");''', 'gap/orb regression tests')
    tests.write_text(s, encoding='utf-8')
    print('patched controller tests -> route-v0.3')
    return True

changed = patch_main() | patch_tests()
print('route-v0.3 changed=' + str(changed).lower())
