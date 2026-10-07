#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayerObject.hpp>
#include <Geode/modify/EffectGameObject.hpp>
#include <Geode/modify/GameObject.hpp>
#include <Geode/modify/HardStreak.hpp>
#include <Geode/modify/RingObject.hpp>
#include "controller.hpp"
#include "gpu_ranker.hpp"
#include "input_motion.hpp"
#include "hazard_guard.hpp"
#include "planning_hints.hpp"
#include "semantic_map.hpp"
#include "object_semantics.hpp"
#include "flight_certificate.hpp"
#include "landing_schedule.hpp"
#include <fstream>
#include <unordered_set>

using namespace geode::prelude;
namespace agb {
// No memcpy of a PlayerObject: containers, sprites and ownership stay on the
// clone. This list contains only numeric values from the pinned GD bindings.
struct PlayerSeed {
#define AGB_SCALAR(n) decltype(PlayerObject::n) n{};
#include "player_scalars.inc"
#undef AGB_SCALAR
    Ref<PlayerCheckpoint> checkpoint;
    decltype(PlayerObject::m_touchedRings) touched;
    decltype(PlayerObject::m_ringRelatedSet) rings;
    decltype(PlayerObject::m_jumpPadRelated) pads;
    decltype(PlayerObject::m_potentialSlopeMap) slopes;
    decltype(PlayerObject::m_rotateObjectsRelated) rotated;
    decltype(PlayerObject::m_holdingButtons) buttons;
    std::vector<CCObject *> touching;
    PlayerObject *source = nullptr;
    CCPoint nodePosition;
    float rotation = 0, scaleX = 1, scaleY = 1;
    void capture(PlayerObject *p) {
        source = p;
        if (!checkpoint)
            checkpoint = PlayerCheckpoint::create();
        p->saveToCheckpoint(checkpoint);
#define AGB_SCALAR(n) n = p->n;
#include "player_scalars.inc"
#undef AGB_SCALAR
        touched = p->m_touchedRings;
        rings = p->m_ringRelatedSet;
        pads = p->m_jumpPadRelated;
        slopes = p->m_potentialSlopeMap;
        rotated = p->m_rotateObjectsRelated;
        buttons = p->m_holdingButtons;
        touching.clear();
        if (p->m_touchingRings)
            for (unsigned i = 0; i < p->m_touchingRings->count(); ++i)
                touching.push_back(p->m_touchingRings->objectAtIndex(i));
        nodePosition = p->getPosition();
        rotation = p->getRotation();
        scaleX = p->getScaleX();
        scaleY = p->getScaleY();
    }
    void restore(PlayerObject *p) const {
        p->copyAttributes(source);
        p->loadFromCheckpoint(checkpoint);
#define AGB_SCALAR(n) p->n = n;
#include "player_scalars.inc"
#undef AGB_SCALAR
        p->m_touchedRings = touched;
        p->m_ringRelatedSet = rings;
        p->m_jumpPadRelated = pads;
        p->m_potentialSlopeMap = slopes;
        p->m_rotateObjectsRelated = rotated;
        p->m_holdingButtons = buttons;
        p->m_currentSlope = source->m_currentSlope;
        p->m_currentSlope2 = source->m_currentSlope2;
        p->m_currentPotentialSlope = source->m_currentPotentialSlope;
        p->m_lastGroundObject = source->m_lastGroundObject;
        p->m_preLastGroundObject = source->m_preLastGroundObject;
        p->m_maybeLastGroundObject = source->m_maybeLastGroundObject;
        p->m_collidedObject = source->m_collidedObject;
        p->m_objectSnappedTo = source->m_objectSnappedTo;
        p->m_collidingWithLeft = source->m_collidingWithLeft;
        p->m_collidingWithRight = source->m_collidingWithRight;
        p->m_lastActivatedPortal = source->m_lastActivatedPortal;
        p->m_dashRing = source->m_dashRing;
        for (auto *dict : {p->m_collisionLogTop, p->m_collisionLogBottom, p->m_collisionLogLeft,
                           p->m_collisionLogRight})
            if (dict)
                dict->removeAllObjects();
        if (p->m_touchingRings) {
            p->m_touchingRings->removeAllObjects();
            for (auto *o : touching)
                p->m_touchingRings->addObject(o);
        }
        p->setScaleX(scaleX);
        p->setScaleY(scaleY);
        p->setPosition(nodePosition);
        p->setRotation(rotation);
        p->m_isDead = false;
        p->m_playEffects = false;
        p->setVisible(false);
    }
};
struct SimulationContext {
    bool active = false, boundary = false;
    PlayLayer *layer = nullptr;
    std::array<PlayerObject *, 2> clones{};
    std::array<bool, 2> dead{};
    struct Flags {
        GameObject *object;
        bool activated, disabled, disabled2, powered;
    };
    std::vector<Flags> changed;
    std::unordered_set<GameObject *> visited;
    bool isClone(PlayerObject *p) const {
        return active && (p == clones[0] || p == clones[1]);
    }
    void save(GameObject *o) {
        if (visited.insert(o).second)
            changed.push_back(
                {o, o->m_isActivated, o->m_isDisabled, o->m_isDisabled2, o->m_isRingPoweredOn});
    }
    void restoreFlags() {
        for (auto const &s : changed) {
            s.object->m_isActivated = s.activated;
            s.object->m_isDisabled = s.disabled;
            s.object->m_isDisabled2 = s.disabled2;
            s.object->m_isRingPoweredOn = s.powered;
        }
        changed.clear();
        visited.clear();
    }
};
static SimulationContext sim;
static bool injected = false;
static bool physicsObject(GameObjectType t) {
    return isClonePhysicsType(t);
}
static bool padObject(GameObjectType t) {
    return isPadType(t);
}
static bool orbObject(GameObjectType t) {
    return isOrbType(t);
}
static bool boundaryObject(GameObject *o) {
    return isForecastBoundary(o);
}
static int mode(PlayerObject *p) {
    return p->m_isShip     ? 1
           : p->m_isBall   ? 2
           : p->m_isBird   ? 3
           : p->m_isDart   ? 4
           : p->m_isRobot  ? 5
           : p->m_isSpider ? 6
           : p->m_isSwing  ? 7
                           : 0;
}
static bool cubePulseMode(PlayerObject *p1, PlayerObject *p2, bool dual) {
    return p1 && mode(p1) == 0 && !p1->m_isDashing &&
           (!dual || (p2 && mode(p2) == 0 && !p2->m_isDashing));
}
static bool touchingUsableOrb(PlayerObject *p) {
    if (!p || !p->m_touchingRings)
        return false;
    for (unsigned i = 0; i < p->m_touchingRings->count(); ++i) {
        auto *o = static_cast<GameObject *>(p->m_touchingRings->objectAtIndex(i));
        if (o && orbObject(o->m_objectType) && !o->m_isDisabled && !o->m_isDisabled2 &&
            !o->m_isActivated)
            return true;
    }
    return false;
}
static bool cubeCanPress(PlayerObject *p) {
    return p && (p->m_isOnGround || touchingUsableOrb(p));
}
struct Settings {
    bool enabled = false, gpu = true, telemetry = false;
    int traceHz = 30;
    Config config;
    void read() {
        auto *m = Mod::get();
        enabled = m->getSettingValue<bool>("enabled");
        gpu = m->getSettingValue<bool>("gpu");
        telemetry = m->getSettingValue<bool>("telemetry");
        traceHz = int(m->getSettingValue<int64_t>("trace-hz"));
        config.horizon = int(m->getSettingValue<int64_t>("horizon"));
        config.candidates = int(m->getSettingValue<int64_t>("candidates"));
        config.budgetMs = m->getSettingValue<double>("budget-ms");
    }
};
class TraceNode : public CCDrawNode {
  public:
    static TraceNode *create() {
        auto *p = new TraceNode();
        if (p->init()) {
            p->m_bUseArea = false;
            p->autorelease();
            return p;
        }
        delete p;
        return nullptr;
    }
};
class Runtime {
  public:
    PlayLayer *layer = nullptr;
    std::array<Ref<PlayerObject>, 2> clones;
    std::array<PlayerSeed, 2> seeds;
    Controller controller;
    PhysicsTiming timing;
    PhysicsOrder physicsOrder;
    CubePulse cubePulse;
    HazardGuard hazardGuard;
    uint64_t geometryRejects = 0;
    float predictionError = 0;
    GpuRanker gpu;
    Settings settings;
    Decision decision;
    TraceNode *draw = nullptr;
    CCLabelBMFont *label = nullptr;
    SemanticMap semantic;
    std::vector<std::pair<float, GameObject *>> hintObjects;
    std::vector<std::pair<float, GameObject *>> hintSolids;
    std::vector<std::pair<float, GameObject *>> hintOrbs;
    std::vector<std::pair<float, GameObject *>> hintControls;
    GapHint routeGap;
    BoundaryHandoffHint routeHandoff;
    int routeJumpEvents = 0, routeOrbEvents = 0;
    std::vector<Point> stableTrace;
    uint64_t stableTraceTick = 0;
    uint64_t ticks = 0;
    bool botHeld = false, wasEnabled = false, gpuTried = false, controlling = false;
    int lastMode = -1;
    CCPoint lastPosition;
    bool positionValid = false;
    std::array<Point, 2> previousPositions{}, travelSteps{};
    float previousDt = 0;
    bool motionValid = false, inputPending = false;
    bool nativeCommandActive = false, nativeSawDown = false;
    unsigned nativeUpdates = 0, nativeCollisions = 0, nativeUpdatesInside = 0;
    uint64_t inputPhaseMismatches = 0;
    std::string lastAttempt;
    uint64_t inputTransitions = 0;
    uint64_t reflexJumps = 0;
    uint64_t lightTicks = 0;
    double totalMs = 0;
    bool telemetryPending = false;
    Clock::time_point redraw = Clock::now();
    std::string message = "Autopilot off";
    std::vector<std::string> csv;
    void init(PlayLayer *pl) {
        layer = pl;
        settings.read();
        semantic.build(pl);
        hintObjects = semantic.hazards();
        hintSolids = semantic.solids();
        hintOrbs = semantic.orbs();
        hintControls = semantic.controlObjects();
        for (int i = 0; i < 2; ++i) {
            auto *p = PlayerObject::create(1, 1, pl, pl, true);
            if (!p)
                continue;
            clones[i] = p;
            p->setVisible(false);
            pl->m_objectLayer->addChild(p, -1000);
            auto ensure = [](CCDictionary *&d) {
                if (!d) {
                    d = CCDictionary::create();
                    d->retain();
                }
            };
            ensure(p->m_collisionLogTop);
            ensure(p->m_collisionLogBottom);
            ensure(p->m_collisionLogLeft);
            ensure(p->m_collisionLogRight);
        }
        draw = TraceNode::create();
        if (draw) {
            draw->setID("autogdbot-traces");
            pl->m_objectLayer->addChild(draw, 10000);
        }
        label = CCLabelBMFont::create("AutoGDBot | experimental", "bigFont.fnt");
        if (label) {
            auto size = CCDirector::sharedDirector()->getWinSize();
            label->setScale(.22f);
            label->setAnchorPoint({0, 1});
            label->setPosition({8, size.height - 8});
            label->setID("autogdbot-status");
            pl->addChild(label, 10000);
        }
    }

    // Predict only the tiny pre-contact window needed to buffer a ring press.
    // This is deliberately local (<= 2 physics ticks): the normal planner
    // still decides whether using the orb is safe.
    bool orbImminent(PlayerObject *p, Point travel, int lookaheadTicks = 2) const {
        if (!p || p->m_isOnGround || hintOrbs.empty())
            return false;

        auto pr = p->getObjectRect(.35f, .35f);
        const float dx = travel.x * float(lookaheadTicks);
        const float dy = travel.y * float(lookaheadTicks);
        const float minX = std::min(pr.getMinX(), pr.getMinX() + dx) - 7.f;
        const float maxX = std::max(pr.getMaxX(), pr.getMaxX() + dx) + 7.f;
        // Vertical native movement is not represented in travelSteps for the
        // normal horizontal cube. A small envelope safely covers two 240-TPS
        // substeps without arming distant rings.
        const float minY = std::min(pr.getMinY(), pr.getMinY() + dy) - 18.f;
        const float maxY = std::max(pr.getMaxY(), pr.getMaxY() + dy) + 18.f;

        const float px = p->getPositionX();
        auto first = std::lower_bound(
            hintOrbs.begin(), hintOrbs.end(), px - 90.f,
            [](auto const &a, float x) { return a.first < x; });
        for (auto it = first; it != hintOrbs.end() && it->first <= px + 90.f; ++it) {
            auto *o = it->second;
            if (!o || o->m_isDisabled || o->m_isDisabled2 || o->m_isActivated ||
                !orbObject(o->m_objectType))
                continue;
            auto r = o->getObjectRect();
            if (maxX >= r.getMinX() - 8.f && minX <= r.getMaxX() + 8.f &&
                maxY >= r.getMinY() - 8.f && minY <= r.getMaxY() + 8.f)
                return true;
        }
        return false;
    }
    void releaseLiveInput() {
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
        if (ticks > 0) {
            auto *p = layer ? layer->m_player1 : nullptr;
            lastAttempt = fmt::format(
                "last attempt: step={} x={:.1f} want={} held={} native={} U={}/{} C={} mismatch={} | "
                "route={} rejected={} best={} deaths={} landing={} timeout={}",
                ticks, p ? p->getPositionX() : 0.f, decision.down ? "DOWN" : "UP",
                botHeld ? "DOWN" : "UP", nativeSawDown ? "DOWN" : "UP",
                nativeUpdates, nativeUpdatesInside, nativeCollisions, inputPhaseMismatches,
                decision.hasValidatedInput ? "OK" : "NONE", decision.rejectedUnsafe,
                decision.bestRejectedTicks, decision.deaths, decision.landingRejects,
                decision.interruptions);
        }
        flush();
        releaseLiveInput();
        controller.reset();
        timing.reset();
        physicsOrder = {};
        cubePulse.reset();
        ticks = 0;
        botHeld = false;
        wasEnabled = false;
        controlling = false;
        positionValid = false;
        motionValid = false;
        inputPending = false;
        nativeCommandActive = false;
        nativeSawDown = false;
        nativeUpdates = nativeCollisions = nativeUpdatesInside = 0;
        inputPhaseMismatches = 0;
        previousDt = 0;
        inputTransitions = 0;
        reflexJumps = 0;
        lightTicks = 0;
        totalMs = 0;
        telemetryPending = false;
        geometryRejects = 0;
        predictionError = 0;
        routeGap = {};
        routeHandoff = {};
        routeJumpEvents = 0;
        routeOrbEvents = 0;
        stableTrace.clear();
        stableTraceTick = 0;
        hazardGuard.set({});
        lastMode = -1;
        decision = {};
        csv.clear();
        if (draw)
            draw->clear();
    }
    void flush() {
        if (csv.empty())
            return;
        auto path = Mod::get()->getSaveDir() / "timing-v0.8.0.csv";
        bool fresh = !std::filesystem::exists(path);
        std::ofstream f(path, std::ios::app);
        if (f) {
            if (fresh)
                f << "tick,x,y,command_dt,native_dt,search_ms,total_ms,candidates,predicted_ticks,"
                     "requested_down,actual_down,deadline_hit,selected_died,selected_interrupted,"
                     "first_action_verified,requires_landing,landing_verified,boundary,position_"
                     "error,light_ticks,reflex_jumps\n";
            for (auto const &row : csv)
                f << row << '\n';
        }
        csv.clear();
    }
    Evaluation evaluate(Plan const &plan, Clock::time_point deadline, float dt) {
        Evaluation out;
        CubePulse pulse = cubePulse;
        bool previousButton = botHeld;
        std::array<FlightCertificate, 2> flights;
        std::array<bool, 2> groundedBefore{};
        out.buttonChanges = 0;
        const float physicsDt = timing.nativeStep(dt);
        bool dual = layer->m_gameState.m_isDualMode;
        const int players = dual ? 2 : 1;
        const auto gameState = layer->m_gameState;
        struct Scope {
            PlayLayer *l;
            GJGameState state;
            Scope(PlayLayer *p, GJGameState const &s) : l(p), state(s) {
                sim.active = true;
                sim.layer = p;
                sim.dead = {};
                sim.boundary = false;
            }
            ~Scope() {
                sim.restoreFlags();
                l->m_gameState = state;
                sim.active = false;
                sim.layer = nullptr;
                sim.clones = {};
            }
        } scope(layer, gameState);
        for (int i = 0; i < 2; ++i) {
            sim.clones[i] = clones[i];
            if (i < players) {
                seeds[i].restore(clones[i]);
                flights[i] = FlightCertificate(clones[i]->m_isOnGround);
                out.traces[i].reserve(plan.length + 1);
                out.traces[i].push_back({clones[i]->getPositionX(), clones[i]->getPositionY()});
            }
        }
        LandingSchedule schedule(plan.landingDelay, clones[0]->m_isOnGround);
        out.hasRealized = true;
        const bool cubeRollout = cubePulseMode(clones[0], clones[1], dual);
        const int limit = cubeRollout ? MaxHorizon : plan.length;
        out.clearance = 120;
        out.landingMargin = 0;
        out.robustness = 1.f;
        auto addOrbAction = [&](int tick) {
            if (tick < 0 || tick >= plan.length)
                return;
            if (std::find(out.orbActions.begin(), out.orbActions.end(), tick) ==
                out.orbActions.end())
                out.orbActions.push_back(tick);
        };
        for (int t = 0; t < limit; ++t) {
            if (Clock::now() >= deadline) {
                out.interrupted = true;
                break;
            }
            bool pulseMode = cubePulseMode(clones[0], clones[1], dual);
            const bool orbContact =
                touchingUsableOrb(clones[0]) || (dual && touchingUsableOrb(clones[1]));
            bool imminentOrb = false;
            for (int i = 0; i < players; ++i)
                imminentOrb |= orbImminent(clones[i], travelSteps[i]);
            bool canPress = cubeCanPress(clones[0]) || (dual && cubeCanPress(clones[1]));
            if (pulseMode && clones[0]->m_isOnGround && !pulse.mustRelease())
                out.groundedActions.push_back(t);
            if (pulseMode && orbContact && !pulse.mustRelease())
                addOrbAction(t);
            const bool requested = schedule.request(t < plan.length && plan.at(t),
                clones[0]->m_isOnGround, pulse.mustRelease(), pulseMode && t < plan.length);
            bool down =
                pulse.command(requested, pulseMode, canPress, imminentOrb, orbContact);
            schedule.sent(down, clones[0]->m_isOnGround);
            out.realized.buttons[t] = down;
            for (int i = 0; i < players; ++i)
                groundedBefore[i] = clones[i]->m_isOnGround;
            out.buttonChanges += down != previousButton;
            previousButton = down;
            for (int i = 0; i < players; ++i) {
                auto *p = clones[i].data();
                applyJumpState(
                    down, [&] { return p->m_holdingButtons[1]; },
                    [&] { p->pushButton(PlayerButton::Jump); },
                    [&] { p->releaseButton(PlayerButton::Jump); });
                // Match the layer's per-step bookkeeping, not just its reset
                // at the beginning of the whole hypothetical timeline.
                p->resetTouchedRings(false);
                p->resetCollisionLog(false);
                auto rectBefore = p->getObjectRect(.35f, .35f);
                Point before{p->getPositionX(), p->getPositionY()};
                if (physicsOrder.collisionFirst)
                    layer->checkCollisions(p, physicsDt, false);
                if (sim.dead[i] || p->m_isDead) {
                    out.died = true;
                    break;
                }
                p->update(physicsDt);
                Point after{p->getPositionX(), p->getPositionY()};
                auto completed = completeTravelStep(before, after, travelSteps[i], p->m_isSideways);
                if (completed.x != after.x || completed.y != after.y)
                    p->setPosition({completed.x, completed.y});
                // Mirror the real collision/update order observed in this game.
                // Never run the native collision cycle twice for one step.
                if (!physicsOrder.collisionFirst)
                    layer->checkCollisions(p, physicsDt, false);
                // Input is injected before collision processing. If a no-press
                // clone first becomes eligible for a ring in this collision
                // pass, expose both the exact tick and one pre-arm tick to the
                // event-driven brancher.
                if (!down && touchingUsableOrb(p) && !pulse.mustRelease()) {
                    addOrbAction(t - 1);
                    addOrbAction(t);
                }
                if (sim.dead[i] || p->m_isDead) {
                    out.died = true;
                    out.traces[i].push_back({p->getPositionX(), p->getPositionY()});
                    break;
                }
                if (mode(p) == 0) {
                    auto rectAfter = p->getObjectRect(.35f, .35f);
                    auto bounds = [](CCRect r) {
                        return Bounds{r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()};
                    };
                    if (hazardGuard.hit(bounds(rectBefore), bounds(rectAfter))) {
                        ++geometryRejects;
                        out.died = true;
                        out.traces[i].push_back({p->getPositionX(), p->getPositionY()});
                        break;
                    }
                }
                if (!std::isfinite(p->getPositionX()) || !std::isfinite(p->getPositionY())) {
                    out.died = true;
                    break;
                }
                out.traces[i].push_back({p->getPositionX(), p->getPositionY()});
                if (mode(p) == 0 && i == 0) {
                    auto pos = p->getPosition();
                    auto pr = p->getObjectRect(.35f, .35f);
                    auto beginHaz = std::lower_bound(
                        hintObjects.begin(), hintObjects.end(), pos.x - 100.f,
                        [](auto const &a, float x) { return a.first < x; });
                    int seen = 0;
                    for (auto it = beginHaz; it != hintObjects.end() && it->first <= pos.x + 100.f && seen < 48; ++it) {
                        auto *o = it->second;
                        if (!o || o->m_isDisabled || o->m_isDisabled2)
                            continue;
                        auto r = o->getObjectRect();
                        float dxr = std::max({r.getMinX() - pr.getMaxX(), pr.getMinX() - r.getMaxX(), 0.f});
                        float dyr = std::max({r.getMinY() - pr.getMaxY(), pr.getMinY() - r.getMaxY(), 0.f});
                        out.clearance = std::min(out.clearance, std::sqrt(dxr * dxr + dyr * dyr));
                        ++seen;
                    }
                }
                if (mode(p) == 0) {
                    if (!groundedBefore[i] && p->m_isOnGround && i == 0) {
                        ++out.landingCount;
                        if (out.firstLandingTick < 0) out.firstLandingTick = t;
                    }
                    const bool wasVerified = flights[i].verified();
                    flights[i].observe(groundedBefore[i], p->m_isOnGround, down);
                    if (!wasVerified && flights[i].verified()) {
                        if (i == 0) {
                            auto pr = p->getObjectRect();
                            const float px = p->getPositionX();
                            std::vector<std::pair<float, float>> spans;
                            auto first = std::lower_bound(
                                hintSolids.begin(), hintSolids.end(), px - 180.f,
                                [](auto const &a, float x) { return a.first < x; });
                            for (auto it = first; it != hintSolids.end() && it->first <= px + 180.f;
                                 ++it) {
                                auto *solid = it->second;
                                if (!solid || solid->m_isDisabled || solid->m_isDisabled2)
                                    continue;
                                auto sr = solid->getObjectRect();
                                if (std::abs(sr.getMaxY() - pr.getMinY()) > 5.f)
                                    continue;
                                spans.emplace_back(sr.getMinX(), sr.getMaxX());
                            }
                            std::sort(spans.begin(), spans.end());
                            std::vector<std::pair<float, float>> merged;
                            for (auto span : spans) {
                                if (!merged.empty() && span.first <= merged.back().second + 1.5f)
                                    merged.back().second = std::max(merged.back().second, span.second);
                                else
                                    merged.push_back(span);
                            }
                            for (auto span : merged) {
                                if (pr.getMaxX() < span.first || pr.getMinX() > span.second)
                                    continue;
                                const float left = pr.getMinX() - span.first;
                                const float right = span.second - pr.getMaxX();
                                out.landingMargin =
                                    std::max(out.landingMargin, std::max(0.f, std::min(left, right)));
                            }
                        }
                    }
                }
                // The real layer advances this timer in seconds, separately
                // from PlayerObject's frame-unit integration argument.
                p->m_totalTime += dt;
            }
            if (out.died)
                break;
            ++out.survived;
            out.realized.length = out.survived;
            bool settled = true;
            for (int i = 0; i < players; ++i)
                if (mode(clones[i]) == 0) settled &= flights[i].verified();
            if (out.survived >= plan.length && (!cubeRollout || settled))
                break;
            // Do not invent a future after a mode/speed/dual portal. The actual
            // game performs it; the following physics tick starts a fresh plan.
            if (sim.boundary)
                break;
            layer->m_gameState.m_levelTime += dt;
        }
        out.boundary = sim.boundary && !out.died;
        bool allLanded = true;
        for (int i = 0; i < players; ++i) {
            if (mode(clones[i]) == 0) {
                out.requiresLanding |= flights[i].required;
                allLanded &= flights[i].verified();
            }
        }
        out.landingVerified = out.requiresLanding && allLanded;
        out.firstActionVerified = !out.requiresLanding || allLanded || out.boundary;
        // Robustness is intentionally cheap: map the worst geometric margin to [0,1].
        // Native collisions remain authoritative; this only ranks surviving candidates.
        out.robustness = std::clamp(out.clearance / 28.f, 0.f, 1.f);
        return out;
    }
    void tick(float dt) {
        if (!layer || sim.active)
            return;
        timing.beginCommand(dt);
        const auto start = Clock::now();
        controlling = false;
        inputPending = false;
        if ((ticks & 15) == 0)
            settings.read();
        if (!settings.enabled) {
            if (wasEnabled) {
                injected = true;
                layer->handleButton(false, 1, true);
                injected = false;
                controller.reset();
                cubePulse.reset();
                botHeld = false;
            }
            wasEnabled = false;
            message = "Autopilot off";
            return;
        }
        auto *p = layer->m_player1;
        if (!p || p->m_isDead || layer->m_isPaused || dt <= 0) {
            message = "Paused / waiting";
            return;
        }
        if (layer->m_isPlatformer ||
            (layer->m_levelSettings && layer->m_levelSettings->m_twoPlayerMode)) {
            message = "Unsupported: platformer / 2-player controls";
            return;
        }
        if (!clones[0] || !clones[1]) {
            message = "Clone allocation failed";
            return;
        }
        controlling = true;
        if (!wasEnabled) {
            botHeld = p->m_holdingButtons[1];
            controller.reset();
            cubePulse.reset();
            wasEnabled = true;
        }
        botHeld = p->m_holdingButtons[1];
        // Use observed travel from the preceding physics tick when available.
        // At spawn/reset use the current native velocity and tick duration.
        for (int i = 0; i < 2; ++i) {
            auto *real = i == 0 ? p : layer->m_player2;
            if (!real)
                continue;
            Point now{real->getPositionX(), real->getPositionY()};
            float step = std::abs(float(real->getCurrentXVelocity())) * dt * 60.f;
            Point expected = real->m_isSideways ? Point{0, real->m_isGoingLeft ? -step : step}
                                                : Point{real->m_isGoingLeft ? -step : step, 0};
            if (motionValid && previousDt > 0) {
                float delta = real->m_isSideways ? now.y - previousPositions[i].y
                                                 : now.x - previousPositions[i].x;
                if (std::isfinite(delta) && std::abs(delta) > .0001f && std::abs(delta) < 32.f) {
                    float scaled = delta * dt / previousDt;
                    expected = real->m_isSideways ? Point{0, scaled} : Point{scaled, 0};
                }
            }
            travelSteps[i] = expected;
            previousPositions[i] = now;
        }
        previousDt = dt;
        motionValid = true;
        if (settings.gpu && !gpuTried) {
            gpuTried = true;
            gpu.init();
        }
        float error = 0;
        if (!decision.selected.traces[0].empty() && decision.selected.traces[0].size() > 1) {
            auto predicted = decision.selected.traces[0][1];
            error = std::hypot(p->getPositionX() - predicted.x, p->getPositionY() - predicted.y);
        }
        if (lastMode != mode(p) ||
            (positionValid && std::abs(p->getPositionX() - lastPosition.x) > 40) || error > 2.0f)
            controller.reset();
        lastMode = mode(p);
        lastPosition = p->getPosition();
        positionValid = true;
        predictionError = error;
        bool pulseMode = cubePulseMode(p, layer->m_player2, layer->m_gameState.m_isDualMode);
        const bool touchingOrb =
            touchingUsableOrb(p) ||
            (layer->m_gameState.m_isDualMode && touchingUsableOrb(layer->m_player2));
        bool imminentOrb = orbImminent(p, travelSteps[0]);
        if (layer->m_gameState.m_isDualMode && layer->m_player2)
            imminentOrb |= orbImminent(layer->m_player2, travelSteps[1]);
        bool canPress =
            cubeCanPress(p) || (layer->m_gameState.m_isDualMode && cubeCanPress(layer->m_player2));
        // Observe game state and release input every tick. Heavy prediction is
        // 60 Hz during uncontrollable cube flight at 240 TPS, and every tick
        // on the ground, at an orb or in a continuous-control mode.
        bool nearAction = false;
        bool orbSoon = false;
        if (pulseMode) {
            const float px = p->getPositionX();
            auto o = std::lower_bound(hintOrbs.begin(), hintOrbs.end(), px - 12.f,
                                      [](auto const &a, float x) { return a.first < x; });
            orbSoon = o != hintOrbs.end() && o->first <= px + 230.f;
            auto h = std::lower_bound(hintObjects.begin(), hintObjects.end(), px - 12.f,
                                      [](auto const &a, float x) { return a.first < x; });
            nearAction = (h != hintObjects.end() && h->first <= px + 230.f) || orbSoon;
            if (!nearAction) {
                // A wall/raised block is as timing-sensitive as a spike. In
                // v0.6.0 these solids did not raise the search budget, which
                // could leave only a handful of candidates before timeout.
                auto pr = p->getObjectRect();
                auto solid = std::lower_bound(
                    hintSolids.begin(), hintSolids.end(), px - 10.f,
                    [](auto const &a, float x) { return a.first < x; });
                int checked = 0;
                for (auto it = solid;
                     it != hintSolids.end() && it->first <= px + 260.f && checked < 48;
                     ++it, ++checked) {
                    auto *obj = it->second;
                    if (!obj || obj->m_isDisabled || obj->m_isDisabled2)
                        continue;
                    auto sr = obj->getObjectRect();
                    const float face = p->m_isGoingLeft ? sr.getMaxX() : sr.getMinX();
                    const float distance =
                        (face - px) * (p->m_isGoingLeft ? -1.f : 1.f);
                    if (distance <= 3.f)
                        continue;
                    if (sr.getMaxY() > pr.getMinY() + 5.f &&
                        sr.getMinY() < pr.getMaxY() + 10.f) {
                        nearAction = true;
                        break;
                    }
                }
            }
            if (!nearAction) {
                // Portals can terminate clone forecasting. Increase search
                // effort before the handoff so a required pre-portal action is
                // considered while it is still physically possible.
                auto control = std::lower_bound(
                    hintControls.begin(), hintControls.end(), px - 10.f,
                    [](auto const &a, float x) { return a.first < x; });
                for (auto it = control; it != hintControls.end() && it->first <= px + 280.f;
                     ++it) {
                    auto *obj = it->second;
                    if (obj && !obj->m_isDisabled && !obj->m_isDisabled2 &&
                        (boundaryObject(obj) || padObject(obj->m_objectType))) {
                        nearAction = true;
                        break;
                    }
                }
            }
            // A native touchdown can immediately enable the next staircase
            // pulse. Replan at full rate while descending into a nearby
            // support surface instead of skipping the exact landing substep.
            if (!nearAction && !canPress) {
                const double vy = p->m_yVelocity;
                const bool towardGravity = p->m_isUpsideDown ? vy >= 0.0 : vy <= 0.0;
                if (towardGravity) {
                    auto pr = p->getObjectRect();
                    auto solid = std::lower_bound(
                        hintSolids.begin(), hintSolids.end(), px - 220.f,
                        [](auto const &a, float x) { return a.first < x; });
                    int checked = 0;
                    for (auto it = solid;
                         it != hintSolids.end() && it->first <= px + 220.f && checked < 48;
                         ++it, ++checked) {
                        auto *obj = it->second;
                        if (!obj || obj->m_isDisabled || obj->m_isDisabled2)
                            continue;
                        auto sr = obj->getObjectRect();
                        const bool horizontalReach =
                            p->m_isGoingLeft
                                ? sr.getMaxX() >= pr.getMinX() - 180.f &&
                                      sr.getMinX() <= pr.getMaxX() + 20.f
                                : sr.getMinX() <= pr.getMaxX() + 180.f &&
                                      sr.getMaxX() >= pr.getMinX() - 20.f;
                        if (!horizontalReach)
                            continue;
                        const float gap = p->m_isUpsideDown
                                              ? sr.getMinY() - pr.getMaxY()
                                              : pr.getMinY() - sr.getMaxY();
                        if (gap >= -6.f && gap <= 90.f) {
                            nearAction = true;
                            break;
                        }
                    }
                }
            }
        }
        if (pulseMode && !canPress && !nearAction && (ticks & 3) != 0) {
            auto shift = [](Evaluation &e) {
                for (auto &trace : e.traces)
                    if (!trace.empty())
                        trace.erase(trace.begin());
                e.survived = std::max(0, e.survived - 1);
            };
            shift(decision.selected);
            for (auto &e : decision.alternatives)
                shift(e);
            decision.down = false;
            decision.elapsedMs = 0;
            decision.candidates = 0;
            decision.predictedTicks = decision.selected.survived;
            if (!decision.predictedTicks)
                decision.completeContinuation = false;
            decision.plan.length = std::max(0, decision.plan.length - 1);
            decision.exhausted = false;
            inputPending = true;
            message = "BOT | cube flight / release";
            ++lightTicks;
            finishTick(start, error);
            return;
        }
        seeds[0].capture(p);
        if (layer->m_player2)
            seeds[1].capture(layer->m_player2);
        predictionError = error;
        Config searchConfig = settings.config;
        const bool orbOpportunity = touchingOrb || imminentOrb || cubePulse.orbArmed();
        searchConfig.forceRelease =
            pulseMode && (cubePulse.mustRelease() || (!canPress && !orbOpportunity));
        searchConfig.cubePulsePlanning = pulseMode;
        searchConfig.immediateOrb =
            pulseMode && orbOpportunity && !cubePulse.mustRelease();
        if (mode(p) == 0) {
            // Keep planning while the cube is still on safe ground. The longer
            // rolling horizon sees the edge/gap/orb before the takeoff is urgent.
            searchConfig.horizon = std::max(searchConfig.horizon, 216);
            searchConfig.candidates = std::max(searchConfig.candidates, 64);
            searchConfig.budgetMs = std::max(searchConfig.budgetMs, nearAction ? 3.5 : 1.35);
            if (nearAction)
                searchConfig.candidates = std::max(searchConfig.candidates, 96);
            if (orbSoon) {
                searchConfig.horizon = std::max(searchConfig.horizon, 240);
                searchConfig.candidates = 96;
                searchConfig.budgetMs = std::max(searchConfig.budgetMs, 4.0);
            }
            if (searchConfig.immediateOrb) {
                // The actual/pre-contact ring window is only a few physics
                // ticks wide. Spend the largest local burst here, never as a
                // level-wide precomputation.
                searchConfig.horizon = std::max(searchConfig.horizon, 240);
                searchConfig.candidates = 96;
                searchConfig.budgetMs = std::max(searchConfig.budgetMs, 5.0);
            }
        }
        // Cache frozen-world geometry once per tick. Candidate steps query the
        // sorted bounds without repeatedly calling native object functions.
        std::vector<Bounds> guardBounds;
        float range =
            MaxHorizon * std::max(std::abs(travelSteps[0].x), std::abs(travelSteps[1].x)) + 512;
        float originX = p->getPositionX();
        auto firstHazard = std::lower_bound(hintObjects.begin(), hintObjects.end(), originX - range,
                                            [](auto const &a, float x) { return a.first < x; });
        for (auto it = firstHazard; it != hintObjects.end() && it->first <= originX + range; ++it) {
            auto *o = it->second;
            if (!o || o->m_isDisabled || o->m_isDisabled2)
                continue;
            auto r = o->getObjectRect();
            guardBounds.push_back({r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()});
        }
        routeGap = {};
        routeHandoff = {};
        routeJumpEvents = 0;
        routeOrbEvents = 0;
        if (pulseMode) {
            std::vector<Bounds> solids;
            std::vector<Bounds> orbs;
            std::vector<Bounds> boundaries;
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
            auto firstControl =
                std::lower_bound(hintControls.begin(), hintControls.end(), originX - range,
                                 [](auto const &a, float x) { return a.first < x; });
            for (auto it = firstControl;
                 it != hintControls.end() && it->first <= originX + range; ++it) {
                auto *o = it->second;
                if (!o || o->m_isDisabled || o->m_isDisabled2 || !boundaryObject(o))
                    continue;
                auto r = o->getObjectRect();
                boundaries.push_back(
                    {r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()});
            }
            int players = layer->m_gameState.m_isDualMode ? 2 : 1;
            for (int i = 0; i < players; ++i) {
                auto *real = i == 0 ? p : layer->m_player2;
                if (!real)
                    continue;

                auto pr = real->getObjectRect();
                seedOrbContactEvents(
                    searchConfig,
                    {pr.getMinX(), pr.getMinY(), pr.getMaxX(), pr.getMaxY()},
                    travelSteps[i].x, orbs);

                if (real->m_isOnGround) {
                    auto gap = seedCubeEvents(searchConfig, real->getPositionX(),
                                              pr.getMinY(), travelSteps[i].x,
                                              solids, guardBounds, orbs);
                    if (i == 0)
                        routeGap = gap;
                }

                if (i == 0 && real->m_isOnGround) {
                    routeHandoff = seedBoundaryHandoff(
                        searchConfig,
                        {pr.getMinX(), pr.getMinY(), pr.getMaxX(), pr.getMaxY()},
                        travelSteps[i].x, solids, guardBounds, boundaries,
                        real->m_isUpsideDown);
                    if (routeHandoff.found) {
                        guardBounds.push_back(routeHandoff.gate);
                        searchConfig.candidates = std::max(searchConfig.candidates, 96);
                        searchConfig.budgetMs = std::max(searchConfig.budgetMs, 3.5);
                    }
                }
            }
            routeJumpEvents = searchConfig.jumpEventCount;
            routeOrbEvents = searchConfig.orbEventCount;
        }
        hazardGuard.set(std::move(guardBounds));
        auto priorities = settings.gpu ? gpu.poll() : std::vector<float>{};
        decision = controller.decide(
            searchConfig, botHeld,
            [&](Plan const &plan, auto deadline) { return evaluate(plan, deadline, dt); },
            priorities);
        if (decision.hasValidatedInput || searchConfig.forceRelease) {
            inputPending = true;
            message = !decision.hasValidatedInput     ? "BOT | forced release"
                      : decision.selected.died        ? "BOT | no safe continuation"
                      : decision.selected.interrupted ? "BOT | partial forecast"
                      : decision.completeContinuation ? "BOT | online"
                                                      : "BOT | short forecast";
        } else
            message = "BOT | no surviving candidate";
        // No unverified reflex override: when no complete cube route exists,
        // release deterministically and expose the failure in the HUD.
        if (!decision.hasValidatedInput && pulseMode) {
            decision.down = false;
            inputPending = true;
            message = "BOT | NO VERIFIED ROUTE";
            stableTrace.clear();
        }
        if (settings.gpu && gpu.ready() && (ticks & 7) == 0) {
            GpuState s;
            s.x = p->getPositionX();
            s.y = p->getPositionY();
            s.vx = travelSteps[0].x;
            s.vy = float(p->m_yVelocity);
            s.mode = uint32_t(mode(p));
            s.held = botHeld;
            s.gravitySign = p->m_isUpsideDown ? -1 : 1;
            s.size = p->m_vehicleSize < .8f ? 3 : 6;
            s.horizon = searchConfig.horizon;
            std::vector<Obstacle> hazards;
            auto begin = std::lower_bound(hintObjects.begin(), hintObjects.end(), s.x - 700,
                                          [](auto const &a, float x) { return a.first < x; });
            for (auto it = begin;
                 it != hintObjects.end() && it->first <= s.x + 700 && hazards.size() < 256; ++it) {
                auto *o = it->second;
                if (!o || o->m_isDisabled || std::abs(o->getPositionX() - s.x) > 650)
                    continue;
                if (o->m_objectType != GameObjectType::Hazard &&
                    o->m_objectType != GameObjectType::AnimatedHazard)
                    continue;
                auto r = o->getObjectRect();
                hazards.push_back({r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()});
            }
            gpu.submit(s, decision.proposals, hazards);
        }
        finishTick(start, error);
    }
    void finishTick(Clock::time_point start, float error) {
        controller.advance();
        ++ticks;
        totalMs = std::chrono::duration<double, std::milli>(Clock::now() - start).count();
        telemetryPending = settings.telemetry;
    }
    void recordTelemetry() {
        if (!telemetryPending || !layer || !layer->m_player1)
            return;
        telemetryPending = false;
        auto *p = layer->m_player1;
        auto const &e = decision.selected;
        csv.push_back(fmt::format("{},{:.4f},{:.4f},{:.6f},{:.4f},{:.4f},{:.4f},{},{},{},{},{},{},{"
                                  "},{},{},{},{},{:.4f},{},{}",
                                  ticks, p->getPositionX(), p->getPositionY(), previousDt,
                                  timing.nativeStep(previousDt), decision.elapsedMs, totalMs,
                                  decision.candidates, decision.predictedTicks, decision.down,
                                  p->m_holdingButtons[1], decision.exhausted, e.died, e.interrupted,
                                  e.firstActionVerified, e.requiresLanding, e.landingVerified,
                                  e.boundary, predictionError, lightTicks, reflexJumps));
        if (csv.size() >= 256)
            flush();
    }
    void applyInput() {
        if (!inputPending || !controlling || !settings.enabled || !layer || layer->m_isPaused ||
            !layer->m_player1 || layer->m_player1->m_isDead)
            return;
        inputPending = false;
        bool dual = layer->m_gameState.m_isDualMode;
        bool pulseMode = cubePulseMode(layer->m_player1, layer->m_player2, dual);
        const bool orbContact =
            touchingUsableOrb(layer->m_player1) ||
            (dual && touchingUsableOrb(layer->m_player2));
        bool imminentOrb = orbImminent(layer->m_player1, travelSteps[0]);
        if (dual && layer->m_player2)
            imminentOrb |= orbImminent(layer->m_player2, travelSteps[1]);
        bool canPress = cubeCanPress(layer->m_player1) || (dual && cubeCanPress(layer->m_player2));
        bool down =
            cubePulse.command(decision.down, pulseMode, canPress, imminentOrb, orbContact);
        decision.down = down;
        const int players = layer->m_gameState.m_isDualMode ? 2 : 1;
        for (int i = 0; i < players; ++i) {
            auto *p = i == 0 ? layer->m_player1 : layer->m_player2;
            if (!p || p->m_isDead)
                continue;
            if (applyJumpState(
                    down, [&] { return p->m_holdingButtons[1]; },
                    [&] { p->pushButton(PlayerButton::Jump); },
                    [&] { p->releaseButton(PlayerButton::Jump); }))
                ++inputTransitions;
        }
        botHeld = layer->m_player1->m_holdingButtons[1];
    }
    void render() {
        if (!draw || !label)
            return;
        settings.read();
        if (Clock::now() < redraw)
            return;
        redraw = Clock::now() + std::chrono::milliseconds(1000 / std::max(5, settings.traceHz));
        label->setString(
            fmt::format(
                "AutoGDBot 0.8.0 | {}\nsearch={:.2f} total={:.2f} ms | {} candidates | {} ticks | "
                "{}\n"
                "step={} | want={} | actual={} | sent={}\n"
                "dx={:.2f} | dt={:.5f}->{:.3f}{} | UP={} DOWN={}\n"
                "error={:.2f} | guard={} | light={} | reflex={} | forecast={} | landing={}\n"
                "route: jumps={} orbs={} touch={} gap={} handoff={} | rejected={} | order={}{}\n"
                "failure: best={} deaths={} landing={} timeout={} | landings={}\n"
                "native: U={}/{} C={} saw={} mismatch={}\n{}",
                message, decision.elapsedMs, totalMs, decision.candidates, decision.predictedTicks,
                settings.gpu ? gpu.status() : "CPU", ticks, decision.down ? "DOWN" : "UP",
                botHeld ? "DOWN" : "UP", inputTransitions, travelSteps[0].x, previousDt,
                timing.nativeStep(previousDt), timing.measured() ? "*" : "",
                decision.alternatives[0].survived, decision.alternatives[1].survived,
                predictionError, geometryRejects, lightTicks, reflexJumps,
                decision.selected.died          ? "DEAD"
                : decision.selected.interrupted ? "PARTIAL"
                : decision.selected.boundary    ? "PORTAL"
                : decision.completeContinuation ? "HORIZON"
                                                : "SHORT",
                decision.selected.requiresLanding
                    ? (decision.selected.landingVerified ? "OK" : "NO")
                    : "n/a",
                routeJumpEvents, routeOrbEvents,
                touchingUsableOrb(layer->m_player1)
                    ? "ORB"
                    : (cubePulse.orbArmed() ? "ARM" : "no"),
                routeGap.found ? "YES" : "no", routeHandoff.found ? "GUARD" : "no",
                decision.rejectedUnsafe,
                physicsOrder.collisionFirst ? "C-U" : "U-C",
                physicsOrder.measured ? "*" : "?", decision.bestRejectedTicks,
                decision.deaths, decision.landingRejects, decision.interruptions,
                decision.selected.landingCount, nativeUpdates, nativeUpdatesInside, nativeCollisions,
                nativeSawDown ? "DOWN" : "UP", inputPhaseMismatches, lastAttempt)
                .c_str());
        label->setColor(decision.hasValidatedInput ? ccColor3B{120, 255, 170}
                                                   : ccColor3B{255, 180, 100});
        draw->clear();
        if (!settings.enabled)
            return;
        // Render only the decision actually used this tick. An older line
        // cannot keep looking approved after a new forecast rejects the route.
        stableTrace.clear();
        if (decision.hasValidatedInput)
            stableTrace = decision.selected.traces[0];
        auto trace = [&](std::vector<Point> const &points, ccColor4F color, float width) {
            for (size_t i = 3; i < points.size(); i += 3)
                draw->drawSegment({points[i - 2].x, points[i - 2].y}, {points[i].x, points[i].y},
                                  width, color);
            if (!points.empty()) {
                auto q = points.back();
                std::array<CCPoint, 4> v = {CCPoint{q.x - 7, q.y - 7}, CCPoint{q.x - 7, q.y + 7},
                                            CCPoint{q.x + 7, q.y + 7}, CCPoint{q.x + 7, q.y - 7}};
                draw->drawPolygon(v.data(), 4, {0, 0, 0, 0}, .5f, color);
            }
        };
        trace(decision.alternatives[0].traces[0], {1, .25f, .3f, .4f}, .4f);
        trace(decision.alternatives[1].traces[0], {.35f, .6f, 1, .4f}, .4f);
        bool complete = decision.completeContinuation;
        ccColor4F chosen = decision.selected.died       ? ccColor4F{1, .15f, .15f, .9f}
                           : decision.selected.boundary ? ccColor4F{.3f, .8f, 1, .9f}
                           : complete                   ? ccColor4F{.2f, 1, .45f, .9f}
                                                        : ccColor4F{1, .65f, .1f, .9f};
        trace(stableTrace, chosen, .85f);
        if (layer->m_player1) {
            float x = layer->m_player1->getPositionX();
            int shown = 0;
            auto begin = std::lower_bound(hintObjects.begin(), hintObjects.end(), x - 120,
                                          [](auto const &a, float q) { return a.first < q; });
            for (auto it = begin; it != hintObjects.end() && it->first <= x + 700 && shown < 96;
                 ++it) {
                auto *o = it->second;
                if (o->m_isDisabled)
                    continue;
                auto r = o->getObjectRect();
                std::array<CCPoint, 4> v = {
                    CCPoint{r.getMinX(), r.getMinY()}, CCPoint{r.getMinX(), r.getMaxY()},
                    CCPoint{r.getMaxX(), r.getMaxY()}, CCPoint{r.getMaxX(), r.getMinY()}};
                draw->drawPolygon(v.data(), 4, {0, 0, 0, 0}, .25f, {1, .3f, .3f, .18f});
                ++shown;
            }
        }

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
            // The selected route line is the only moving forecast overlay.
            // Input timing remains visible in the HUD instead of flickering as
            // a cloud of per-tick diamonds.
        }

        for (auto *p : {layer->m_player1, layer->m_player2}) {
            if (!p)
                continue;
            auto r = p->getObjectRect();
            std::array<CCPoint, 4> v = {
                CCPoint{r.getMinX(), r.getMinY()}, CCPoint{r.getMinX(), r.getMaxY()},
                CCPoint{r.getMaxX(), r.getMaxY()}, CCPoint{r.getMaxX(), r.getMinY()}};
            draw->drawPolygon(v.data(), 4, {0, 0, 0, 0}, .5f, {1, .3f, .3f, .8f});
        }
    }
};
static Runtime *active = nullptr;
} // namespace agb
class $modify(AGBPlayLayer, PlayLayer) {
    struct Fields {
        std::unique_ptr<agb::Runtime> runtime;
        ~Fields() {
            if (agb::active == runtime.get())
                agb::active = nullptr;
        }
    };
    bool init(GJGameLevel *level, bool replay, bool dontCreate) {
        if (!PlayLayer::init(level, replay, dontCreate))
            return false;
        m_fields->runtime = std::make_unique<agb::Runtime>();
        m_fields->runtime->init(this);
        agb::active = m_fields->runtime.get();
        return true;
    }
    void postUpdate(float dt) {
        PlayLayer::postUpdate(dt);
        if (m_fields->runtime)
            m_fields->runtime->render();
    }
    void resetLevel() {
        if (m_fields->runtime)
            m_fields->runtime->releaseLiveInput();
        PlayLayer::resetLevel();
        if (m_fields->runtime)
            m_fields->runtime->reset();
    }
    void onQuit() {
        if (m_fields->runtime)
            m_fields->runtime->flush();
        if (agb::active == m_fields->runtime.get())
            agb::active = nullptr;
        PlayLayer::onQuit();
    }
    void destroyPlayer(PlayerObject *p, GameObject *o) {
        if (agb::sim.isClone(p)) {
            if (o == m_anticheatSpike)
                return;
            for (int i = 0; i < 2; ++i)
                if (p == agb::sim.clones[i])
                    agb::sim.dead[i] = true;
            return;
        }
        PlayLayer::destroyPlayer(p, o);
    }
    void playEndAnimationToPos(CCPoint pos) {
        if (agb::sim.active)
            return;
        PlayLayer::playEndAnimationToPos(pos);
    }
};
class $modify(AGBBaseLayer, GJBaseGameLayer) {
    void processCommands(float dt, bool half, bool last) {
        const bool live = agb::active && !agb::sim.active &&
                          static_cast<GJBaseGameLayer *>(agb::active->layer) == this;
        if (live) {
            agb::active->tick(dt);
            agb::active->physicsOrder.begin();
            agb::active->nativeUpdates = agb::active->nativeCollisions =
                agb::active->nativeUpdatesInside = 0;
            agb::active->nativeSawDown = false;
            // The selected action belongs to this very substep. The native
            // processCommands call may already update the player and collide.
            agb::active->applyInput();
            agb::active->nativeCommandActive = true;
        }
        GJBaseGameLayer::processCommands(dt, half, last);
        if (live) {
            agb::active->nativeCommandActive = false;
            agb::active->recordTelemetry();
        }
    }
    void handleButton(bool down, int button, bool p1) {
        if (agb::active && static_cast<GJBaseGameLayer *>(agb::active->layer) == this &&
            agb::active->controlling && !agb::injected && button == 1)
            return;
        GJBaseGameLayer::handleButton(down, button, p1);
    }
    int checkCollisions(PlayerObject *p, float dt, bool ignoreDamage) {
        if (agb::active && !agb::sim.active && p == agb::active->layer->m_player1) {
            agb::active->physicsOrder.collision();
            agb::active->timing.observeNative(dt);
            ++agb::active->nativeCollisions;
        }
        return GJBaseGameLayer::checkCollisions(p, dt, ignoreDamage);
    }
    void collisionCheckObjects(PlayerObject *p, gd::vector<GameObject *> *objects, int count,
                               float dt) {
        if (!agb::sim.isClone(p)) {
            GJBaseGameLayer::collisionCheckObjects(p, objects, count, dt);
            return;
        }
        gd::vector<GameObject *> filtered;
        filtered.reserve(count);
        for (int i = 0; i < count; ++i) {
            auto *o = objects->at(i);
            if (!o)
                continue;
            if (agb::boundaryObject(o) && p->getObjectRect().intersectsRect(o->getObjectRect()))
                agb::sim.boundary = true;
            if (agb::physicsObject(o->m_objectType)) {
                agb::sim.save(o);
                filtered.push_back(o);
            }
        }
        GJBaseGameLayer::collisionCheckObjects(p, &filtered, int(filtered.size()), dt);
    }
    void flipGravity(PlayerObject *p, bool flip, bool noEffects) {
        if (agb::sim.isClone(p)) {
            p->flipGravity(flip, true);
            return;
        }
        GJBaseGameLayer::flipGravity(p, flip, noEffects);
    }
    void playerTouchedTrigger(PlayerObject *p, EffectGameObject *o) {
        if (agb::sim.active)
            return;
        GJBaseGameLayer::playerTouchedTrigger(p, o);
    }
    void gameEventTriggered(GJGameEvent event, int material, int playerID) {
        if (agb::sim.active)
            return;
        GJBaseGameLayer::gameEventTriggered(event, material, playerID);
    }
};
class $modify(AGBEffects, EffectGameObject) {
    void triggerObject(GJBaseGameLayer *l, int p, gd::vector<int> const *groups) {
        if (agb::sim.active)
            return;
        EffectGameObject::triggerObject(l, p, groups);
    }
};
class $modify(AGBObject, GameObject) {
    void playShineEffect() {
        if (agb::sim.active)
            return;
        GameObject::playShineEffect();
    }
};
class $modify(AGBPlayer, PlayerObject) {
    void update(float dt) {
        if (agb::active && !agb::sim.active && this == agb::active->layer->m_player1) {
            agb::active->timing.observeNative(dt);
            agb::active->physicsOrder.update();
            ++agb::active->nativeUpdates;
            agb::active->nativeUpdatesInside += agb::active->nativeCommandActive;
            agb::active->nativeSawDown = m_holdingButtons[1];
            if (agb::active->controlling &&
                agb::active->nativeSawDown != agb::active->decision.down)
                ++agb::active->inputPhaseMismatches;
        }
        PlayerObject::update(dt);
    }
    void incrementJumps() {
        if (agb::sim.active)
            return;
        PlayerObject::incrementJumps();
    }
    void playSpiderDashEffect(CCPoint from, CCPoint to) {
        if (agb::sim.active)
            return;
        PlayerObject::playSpiderDashEffect(from, to);
    }
};
class $modify(AGBStreak, HardStreak) {
    void addPoint(CCPoint p) {
        if (agb::sim.active)
            return;
        HardStreak::addPoint(p);
    }
};

class $modify(AGBRing, RingObject) {
    void spawnCircle() {
        if (agb::sim.active)
            return;
        RingObject::spawnCircle();
    }
};
