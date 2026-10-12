#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayerObject.hpp>
#include <Geode/modify/EffectGameObject.hpp>
#include <Geode/modify/GameObject.hpp>
#include <Geode/modify/HardStreak.hpp>
#include <Geode/modify/RingObject.hpp>
#include "controller.hpp"
#include "input_motion.hpp"
#include "hazard_guard.hpp"
#include "landing_frame.hpp"
#include "planning_hints.hpp"
#include "semantic_map.hpp"
#include "object_semantics.hpp"
#include "flight_certificate.hpp"
#include "landing_schedule.hpp"
#include "interaction_state.hpp"
#include "verified_input_queue.hpp"
#include "portal_continuation.hpp"
#include "realtime_search.hpp"
#include "game_state_snapshot.hpp"
#include "prefix_cache.hpp"
#include "trace_geometry.hpp"
#include <fstream>
#include <unordered_set>

using namespace geode::prelude;
namespace agb {
// No memcpy of a PlayerObject: containers, sprites and ownership stay on the
// clone. This list contains only numeric values from the pinned GD bindings.
struct PlayerSeed {
#define AGB_LINK(n) decltype(PlayerObject::n) saved_##n{};
    AGB_LINK(m_uniqueID) AGB_LINK(m_currentSlope) AGB_LINK(m_currentSlope2)
    AGB_LINK(m_currentPotentialSlope) AGB_LINK(m_lastGroundObject)
    AGB_LINK(m_preLastGroundObject) AGB_LINK(m_maybeLastGroundObject)
    AGB_LINK(m_collidedObject) AGB_LINK(m_objectSnappedTo)
    AGB_LINK(m_collidingWithLeft) AGB_LINK(m_collidingWithRight)
    AGB_LINK(m_lastActivatedPortal) AGB_LINK(m_dashRing)
#undef AGB_LINK
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
    struct CollisionEntry {
        intptr_t key = 0;
        std::string text;
        Ref<CCObject> value;
    };
    std::array<std::vector<CollisionEntry>, 4> collisionLogs;
    std::vector<CCObject *> touching;
    PlayerObject *source = nullptr;
    CCPoint nodePosition;
    float rotation = 0, scaleX = 1, scaleY = 1;
    void capture(PlayerObject *p) {
        source = p;
#define AGB_LINK(n) saved_##n=p->n;
        AGB_LINK(m_uniqueID) AGB_LINK(m_currentSlope) AGB_LINK(m_currentSlope2)
        AGB_LINK(m_currentPotentialSlope) AGB_LINK(m_lastGroundObject)
        AGB_LINK(m_preLastGroundObject) AGB_LINK(m_maybeLastGroundObject)
        AGB_LINK(m_collidedObject) AGB_LINK(m_objectSnappedTo)
        AGB_LINK(m_collidingWithLeft) AGB_LINK(m_collidingWithRight)
        AGB_LINK(m_lastActivatedPortal) AGB_LINK(m_dashRing)
#undef AGB_LINK
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
        std::array<CCDictionary *, 4> dictionaries{p->m_collisionLogTop, p->m_collisionLogBottom,
            p->m_collisionLogLeft, p->m_collisionLogRight};
        for (size_t i = 0; i < dictionaries.size(); ++i) {
            collisionLogs[i].clear();
            if (!dictionaries[i]) continue;
            for (auto *e = dictionaries[i]->m_pElements; e; e = static_cast<CCDictElement *>(e->hh.next))
                collisionLogs[i].push_back({e->m_iKey, e->m_szKey, Ref<CCObject>(e->m_pObject)});
        }
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
        if(p!=source)p->copyAttributes(source);
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
        // Native interaction bookkeeping is keyed by player unique ID.
        p->m_uniqueID = saved_m_uniqueID;
        p->m_currentSlope = saved_m_currentSlope;
        p->m_currentSlope2 = saved_m_currentSlope2;
        p->m_currentPotentialSlope = saved_m_currentPotentialSlope;
        p->m_lastGroundObject = saved_m_lastGroundObject;
        p->m_preLastGroundObject = saved_m_preLastGroundObject;
        p->m_maybeLastGroundObject = saved_m_maybeLastGroundObject;
        p->m_collidedObject = saved_m_collidedObject;
        p->m_objectSnappedTo = saved_m_objectSnappedTo;
        p->m_collidingWithLeft = saved_m_collidingWithLeft;
        p->m_collidingWithRight = saved_m_collidingWithRight;
        p->m_lastActivatedPortal = saved_m_lastActivatedPortal;
        p->m_dashRing = saved_m_dashRing;
        std::array<CCDictionary *, 4> dictionaries{p->m_collisionLogTop, p->m_collisionLogBottom,
            p->m_collisionLogLeft, p->m_collisionLogRight};
        for (size_t i = 0; i < dictionaries.size(); ++i) {
            auto *dict = dictionaries[i];
            if (!dict) continue;
            dict->removeAllObjects();
            for (auto const &e : collisionLogs[i]) {
                if (e.text.empty()) dict->setObject(e.value, e.key);
                else dict->setObject(e.value, gd::string(e.text.c_str()));
            }
        }
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
    bool localPortals = false;
    GameObject *currentPortal = nullptr;
    PortalBand band;
    unsigned portalSerial = 0;
    PlayLayer *layer = nullptr;
    std::array<PlayerObject *, 2> clones{};
    std::array<bool, 2> dead{};
    using Flags = InteractionSnapshot<GameObject, EnhancedGameObject, RingObject>;
    std::vector<Flags> changed;
    std::unordered_set<GameObject *> visited;
    bool isClone(PlayerObject *p) const {
        return active && (p == clones[0] || p == clones[1]);
    }
    void save(GameObject *o) {
        if (visited.insert(o).second)
            changed.emplace_back(o, typeinfo_cast<EnhancedGameObject *>(o),
                                 typeinfo_cast<RingObject *>(o));
    }
    void restoreFlags() {
        for (auto it = changed.rbegin(); it != changed.rend(); ++it) it->restore();
        changed.clear();
        visited.clear();
    }
};
static SimulationContext sim;
struct ForecastFrame {
    std::array<PlayerSeed,2> players;
    GameStateSnapshot world;
    Evaluation prefix;
    CubePulse pulse;
    LandingSchedule schedule{-1,true};
    PortalContinuation portalProof;
    std::array<FlightCertificate,2> flights;
    std::array<bool,2> safeLanding{};
    std::array<ModeState,2> previousModes{};
    std::vector<SimulationContext::Flags> interactions;
    PortalBand band;
    GameObject *portal=nullptr;
    unsigned portalSerial=0;
    bool previousButton=false;
    float height=0,targetHeight=0,width=0,unzoomedHeight=0;
    bool calculateHeight=false,freezeCamera=false,groundShadows=false;
};
static bool injected = false;
static bool physicsObject(GameObject *o) {
    return isClonePhysicsObject(o);
}
static bool padObject(GameObject *o) {
    return isPadObject(o);
}
static bool orbObject(GameObject *o) {
    return isOrbObject(o);
}
static bool boundaryObject(GameObject *o) {
    return isForecastBoundary(o);
}
static ModeState modeState(PlayerObject *p) {
    ModeState s;
    if (!p) return s;
    s.flags = uint8_t(p->m_isShip | (p->m_isBall << 1) | (p->m_isBird << 2) |
        (p->m_isDart << 3) | (p->m_isRobot << 4) | (p->m_isSpider << 5) |
        (p->m_isSwing << 6));
    s.mode = modeFromFlags(s.flags);
    s.mini = p->m_vehicleSize < .75f;
    s.upsideDown = p->m_isUpsideDown;
    s.sideways = p->m_isSideways;
    s.goingLeft = p->m_isGoingLeft;
    s.dash = p->m_isDashing;
    s.speed = int(std::round(p->m_playerSpeed * 1000.f));
    return s;
}
static int mode(PlayerObject *p) { return int(modeState(p).mode); }
static MotionSample motionSample(PlayerObject *p) {
    if (!p) return {};
    MotionSample m{p->m_yVelocity, p->m_isOnGround, p->m_isOnGround2,
                   p->m_jumpBuffered, p->m_stateRingJump};
    // Order-independent identity of consumed interactions: equal position and
    // velocity cannot make a used ring eligible again in a queued continuation.
    for (auto id : p->m_ringRelatedSet) m.usedInteractions ^= (uint64_t(id) + 1) * 0x9e3779b185ebca87ull;
    for (auto const &[id, used] : p->m_jumpPadRelated)
        m.usedInteractions ^= ((uint64_t(id) + 1) * 2 + used) * 0xc2b2ae3d27d4eb4full;
    m.nativeDetails = true;
    m.xVelocity = p->getCurrentXVelocity(); m.gravity = p->m_gravity; m.gravityMod = p->m_gravityMod;
    m.rotation = p->getRotation(); m.force = {p->m_stateForceVector.x, p->m_stateForceVector.y};
    m.contactFlags = uint32_t(p->m_isOnSlope) | (uint32_t(p->m_isCollidingWithSlope) << 1) |
        (uint32_t(p->m_collidingWithLeft != nullptr) << 2) | (uint32_t(p->m_collidingWithRight != nullptr) << 3) |
        (uint32_t(p->m_holdingButtons[1]) << 4) | (uint32_t(p->m_controlsDisabled) << 5) |
        (uint32_t(p->m_inputsLocked) << 6) | (uint32_t(p->m_stateNoAutoJump) << 7);
    return m;
}
static bool landingMode(PlayerObject *p) {
    return p && modePolicy(modeState(p).controlMode()).mustLand;
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
        // m_isActivated is scene activation, not a per-player consumed orb
        // flag. Native touching/usage sets decide ring eligibility.
        if (o && orbObject(o) && !o->m_isDisabled && !o->m_isDisabled2 &&
            p->m_ringRelatedSet.find(o->m_uniqueID) == p->m_ringRelatedSet.end())
            return true;
    }
    return false;
}
static bool cubeCanPress(PlayerObject *p) {
    return p && (p->m_isOnGround || p->m_isOnGround2 || touchingUsableOrb(p));
}
struct Settings {
    bool enabled = false, telemetry = false, debugTraces = false;
    bool showHitboxes = true, showTrace = true;
    int traceHz = 120;
    double lookaheadSeconds = 3.2;
    Config config;
    void read() {
        auto *m = Mod::get();
        enabled = m->getSettingValue<bool>("enabled");
        telemetry = m->getSettingValue<bool>("telemetry");
        debugTraces = m->getSettingValue<bool>("debug-traces");
        showHitboxes = m->getSettingValue<bool>("show-hitboxes");
        showTrace = m->getSettingValue<bool>("show-trace");
        traceHz = int(m->getSettingValue<int64_t>("trace-fps"));
        lookaheadSeconds = m->getSettingValue<double>("lookahead-seconds");
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
    RealtimeSearch controller;
    PrefixCache<ForecastFrame, 64> forecastCache;
    GameStateSnapshot simulationRoot;
    uint64_t worldSignature=0;
    bool worldSignatureValid=false;
    float worldAnchorX = 0;
    bool activeWorldMotion = false;
    PhysicsTiming timing;
    PhysicsOrder physicsOrder;
    CubePulse cubePulse;
    VerifiedInputQueue inputQueue;
    bool usingQueuedInput = false;
    HazardGuard hazardGuard;
    uint64_t geometryRejects = 0;
    float predictionError = 0;
    Settings settings;
    Decision decision;
    TraceNode *draw = nullptr;
    CCLabelBMFont *label = nullptr;
    SemanticMap semantic;
    bool semanticReady = false;
    std::vector<std::pair<float, GameObject *>> hintObjects;
    std::vector<std::pair<float, GameObject *>> hintSolids;
    std::vector<std::pair<float, GameObject *>> hintOrbs;
    std::vector<std::pair<float, GameObject *>> hintControls;
    GapHint routeGap;
    BoundaryHandoffHint routeHandoff;
    bool routeHandoffBypassed = false;
    int routeJumpEvents = 0, routeOrbEvents = 0;
    std::vector<Point> stableTrace;
    uint64_t stableTraceTick = 0;
    uint64_t ticks = 0;
    bool botHeld = false, wasEnabled = false, controlling = false, ownsInput = false;
    std::array<ModeState, 2> lastModes{};
    bool modeStateValid = false, lastDual = false;
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
    uint64_t orbAttempts = 0, orbHits = 0, padAttempts = 0, padHits = 0;
    uint64_t reflexJumps = 0;
    uint64_t lightTicks = 0;
    double totalMs = 0;
    bool telemetryPending = false;
    TraceCadence traceCadence;
    std::array<Evaluation,2> visualPreviews;
    Clock::time_point previewNext=Clock::time_point::min();
    unsigned previewParity=0;
    Clock::time_point hudRedraw = Clock::now();
    std::string message = "Autopilot off";
    std::string plannerStage = "IDLE";
    std::vector<std::string> csv;
    void refreshSemantic() {
        semantic.build(layer);
        hintObjects = semantic.hazards();
        hintSolids = semantic.solids();
        hintOrbs = semantic.orbs();
        hintControls = semantic.controlObjects();
        semanticReady = true;
    }
    void init(PlayLayer *pl) {
        layer = pl;
        settings.read();
        message = settings.enabled ? "Waiting for physics" : "Autopilot off";
        refreshSemantic();
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
        label = CCLabelBMFont::create("AutoGDBot RT6 | loading", "bigFont.fnt");
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
            if (!o || o->m_isDisabled || o->m_isDisabled2 ||
                p->m_ringRelatedSet.find(o->m_uniqueID) != p->m_ringRelatedSet.end() ||
                !orbObject(o))
                continue;
            auto r = o->getObjectRect();
            if (maxX >= r.getMinX() - 8.f && minX <= r.getMaxX() + 8.f &&
                maxY >= r.getMinY() - 8.f && minY <= r.getMaxY() + 8.f)
                return true;
        }
        return false;
    }

    int landingRunwayTicks(PlayerObject *p, float dx) const {
        if (!p || !std::isfinite(dx) || std::abs(dx) < .0001f) return MaxHorizon;
        auto local = [&](CCRect r) {
            return landingFrame({r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()},
                                p->m_isSideways, p->m_isUpsideDown);
        };
        // Support contact uses the full native collider. An inset foot lies
        // above the actual surface and previously made every ledge look infinite.
        const auto pr = local(p->getObjectRect());
        const float dir = dx > 0 ? 1.f : -1.f;
        const float speed = std::abs(dx);
        float best = std::numeric_limits<float>::infinity();
        auto nearby = [&](auto const &objects, auto visit) {
            const float px = p->getPositionX();
            auto first = p->m_isSideways ? objects.begin() : std::lower_bound(
                objects.begin(), objects.end(), px - 260.f,
                [](auto const &a, float x) { return a.first < x; });
            for (auto it = first; it != objects.end(); ++it) {
                if (!p->m_isSideways && it->first > px + 260.f) break;
                auto *o = it->second;
                if (!o || o->m_isDisabled || o->m_isDisabled2) continue;
                auto b = local(o->getObjectRect());
                if (b.maxX >= pr.minX - 260.f && b.minX <= pr.maxX + 260.f) visit(b);
            }
        };
        std::vector<Bounds> support;
        nearby(hintSolids, [&](Bounds b) { support.push_back(b); });
        best = supportRunway(pr, dx, support);
        auto consider = [&](Bounds b, float extraY) {
            if (b.maxY <= pr.minY + 3.f || b.minY >= pr.maxY + extraY) return;
            const float dist = ((dx > 0 ? b.minX : b.maxX) -
                                (dx > 0 ? pr.maxX : pr.minX)) * dir;
            if (dist > 2.f) best = std::min(best, dist);
        };
        nearby(hintSolids, [&](Bounds b) { consider(b, 8.f); });
        nearby(hintObjects, [&](Bounds b) { consider(b, 42.f); });
        return std::isfinite(best) ? std::clamp(int(std::floor(best / speed)), 0, MaxHorizon)
                                   : MaxHorizon;
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
        ownsInput = false;
    }
    void reset() {
        if (ticks > 0) {
            auto *p = layer ? layer->m_player1 : nullptr;
            lastAttempt = fmt::format(
                "last attempt: step={} x={:.1f} want={} held={} native={} U={}/{} C={} mismatch={} | "
                "route={} rejected={} best={} deaths={} guard={} native={} landing={} timeout={}",
                ticks, p ? p->getPositionX() : 0.f, decision.down ? "DOWN" : "UP",
                botHeld ? "DOWN" : "UP", nativeSawDown ? "DOWN" : "UP",
                nativeUpdates, nativeUpdatesInside, nativeCollisions, inputPhaseMismatches,
                decision.hasValidatedInput ? "OK" : "NONE", decision.rejectedUnsafe,
                decision.bestRejectedTicks, decision.deaths, decision.guardDeaths,
                decision.nativeDeaths, decision.landingRejects, decision.interruptions);
        }
        flush();
        releaseLiveInput();
        controller.reset();
        forecastCache.reset();worldSignatureValid=false;
        timing.reset();
        physicsOrder = {};
        cubePulse.reset();
        inputQueue.reset();
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
        orbAttempts = orbHits = padAttempts = padHits = 0;
        reflexJumps = 0;
        lightTicks = 0;
        totalMs = 0;
        telemetryPending = false;
        geometryRejects = 0;
        predictionError = 0;
        routeGap = {};
        routeHandoff = {};
        routeHandoffBypassed = false;
        routeJumpEvents = 0;
        routeOrbEvents = 0;
        plannerStage = "IDLE";
        stableTrace.clear();
        stableTraceTick = 0;
        visualPreviews = {};
        previewNext=Clock::time_point::min();previewParity=0;
        hazardGuard.set({});
        modeStateValid = false;
        decision = {};
        csv.clear();
        if (draw)
            draw->clear();
    }
    void flush() {
        if (csv.empty())
            return;
        auto path = Mod::get()->getSaveDir() / "timing-v1.0.0-realtime.6.csv";
        bool fresh = !std::filesystem::exists(path);
        std::ofstream f(path, std::ios::app);
        if (f) {
            if (fresh)
                f << "tick,x,y,command_dt,native_dt,search_ms,total_ms,candidates,predicted_ticks,"
                     "requested_down,actual_down,deadline_hit,selected_died,selected_interrupted,"
                     "first_action_verified,requires_landing,landing_verified,boundary,position_"
                     "error,light_ticks,reflex_jumps,route_id,mode_transitions,post_portal_ticks,"
                     "portal_verified,queue_ticks,repairs_created,repairs_tested,proof_kind,forecast_died,forecast_interrupted,support_loss_tick,memo_hits,memo_skipped_ticks,cold_ticks,cache_mismatches,forecast_ticks,jump_window_ticks,reachable_cells,merged_states,resumed_plans,retained_plans\n";
            for (auto const &row : csv)
                f << row << '\n';
        }
        csv.clear();
    }
    Evaluation evaluate(Plan const &plan, Clock::time_point deadline, float dt,
                        int cubeTailCap = MaxHorizon, bool allowCache=true, bool settleFlight=true) {
        Evaluation out;
        CubePulse pulse = cubePulse;
        bool previousButton = botHeld;
        PortalContinuation portalProof;
        std::array<FlightCertificate, 2> flights;
        std::array<bool, 2> groundedBefore{};
        std::array<ModeState, 2> previousModes{};
        std::array<double, 2> rootVelocity{};
        out.buttonChanges = 0;
        const float physicsDt = timing.nativeStep(dt);
        bool dual = layer->m_gameState.m_isDualMode;
        const int players = dual ? 2 : 1;
        auto const &gameState = simulationRoot;
        struct Scope {
            PlayLayer *l;
            GameStateSnapshot const &state;
            PlayerObject *p1, *p2;
            float height, targetHeight, width, unzoomedHeight;
            bool calculateHeight, freezeCamera, groundShadows;
            Scope(PlayLayer *p, GameStateSnapshot const &s)
                : l(p), state(s), p1(p->m_player1), p2(p->m_player2),
                  height(p->m_cameraHeightOffset), targetHeight(p->m_targetCameraHeightOffset),
                  width(p->m_cameraWidthOffset), unzoomedHeight(p->m_cameraUnzoomedHeightOffset),
                  calculateHeight(p->m_calculateTargetHeightOffset),
                  freezeCamera(p->m_freezeStartCamera), groundShadows(p->m_updateGroundShadows) {
                sim.active = true;
                sim.layer = p;
                sim.dead = {};
                sim.boundary = false;
                sim.band = {};
                sim.currentPortal = nullptr;
                sim.portalSerial = 0;
                sim.localPortals = !s.m_isDualMode && !p1->m_isSideways &&
                    !s.m_isFreeMode && !s.m_isFlipping && std::abs(s.m_cameraAngle) < .001f &&
                    std::abs(s.m_cameraZoom - 1.f) < .001f;
            }
            ~Scope() {
                sim.restoreFlags();
                state.restore(l->m_gameState);
                l->m_player1 = p1;
                l->m_player2 = p2;
                l->m_cameraHeightOffset = height;
                l->m_targetCameraHeightOffset = targetHeight;
                l->m_cameraWidthOffset = width;
                l->m_cameraUnzoomedHeightOffset = unzoomedHeight;
                l->m_calculateTargetHeightOffset = calculateHeight;
                l->m_freezeStartCamera = freezeCamera;
                l->m_updateGroundShadows = groundShadows;
                sim.active = false;
                sim.layer = nullptr;
                sim.clones = {};
            }
        } scope(layer, gameState);
        int resume = 0;
        auto *frame = allowCache ? forecastCache.find(plan, resume) : nullptr;
        for (int i = 0; i < 2; ++i) {
            sim.clones[i] = clones[i];
            if (i < players) {
                if (!frame) seeds[i].restore(clones[i]);
                flights[i] = FlightCertificate(clones[i]->m_isOnGround);
                out.traces[i].reserve(plan.length + 1);
                out.motions[i].reserve(plan.length + 1);
                out.modeTraces[i].reserve(plan.length + 1);
                out.traces[i].push_back({clones[i]->getPositionX(), clones[i]->getPositionY()});
                out.motions[i].push_back(motionSample(clones[i]));
                previousModes[i] = modeState(clones[i]);
                rootVelocity[i] = std::abs(seeds[i].source->getCurrentXVelocity());
                out.modeTraces[i].push_back(previousModes[i]);
            }
        }
        // Native ring/spider/dual routines resolve players through the layer.
        // Run those routines against clones and restore live pointers by RAII.
        layer->m_player1 = clones[0];
        layer->m_player2 = clones[1];
        LandingSchedule schedule(plan.landingDelay, clones[0]->m_isOnGround);
        out.hasRealized = true;
        const bool landingRollout = modePolicy(modeState(clones[0]).mode).mustLand ||
            (dual && modePolicy(modeState(clones[1]).mode).mustLand);
        std::array<bool, 2> safeLanding{};
        int limit =
            settleFlight && landingRollout ? std::clamp(std::max(plan.length, cubeTailCap), plan.length, MaxHorizon)
                        : plan.length;
        out.clearance = 120;
        out.nearClearance = 120;
        out.landingMargin = 120;
        out.robustness = 1.f;
        const int nearClearanceTicks =
            std::clamp(int(std::ceil(.15 / std::max(0.001f, dt))), 8, 64);
        const int landingReactionTicks =
            std::clamp(int(std::ceil(.11 / std::max(0.001f, dt))), 18, 40);
        auto addOrbAction = [&](int tick) {
            if (tick < 0 || tick >= plan.length)
                return;
            if (std::find(out.orbActions.begin(), out.orbActions.end(), tick) ==
                out.orbActions.end())
                out.orbActions.push_back(tick);
            for (int i = 0; i < players; ++i) {
                auto *p = clones[i].data();
                if (!p->m_touchingRings) continue;
                for (unsigned j = 0; j < p->m_touchingRings->count(); ++j) {
                    auto *o = static_cast<GameObject *>(p->m_touchingRings->objectAtIndex(j));
                    if (o && (o->m_objectType == GameObjectType::DashRing ||
                              o->m_objectType == GameObjectType::GravityDashRing ||
                              o->m_objectID == 1704 || o->m_objectID == 1751)) {
                        if (std::find(out.dashActions.begin(), out.dashActions.end(), tick) ==
                            out.dashActions.end()) out.dashActions.push_back(tick);
                    }
                }
            }
        };
        if(frame){
            for(auto const &flags:frame->interactions){sim.save(flags.object);flags.restore();}
            frame->world.restore(layer->m_gameState);
            for(int i=0;i<players;++i)frame->players[i].restore(clones[i]);
            out=frame->prefix;pulse=frame->pulse;schedule=frame->schedule;
            portalProof=frame->portalProof;flights=frame->flights;safeLanding=frame->safeLanding;
            previousModes=frame->previousModes;previousButton=frame->previousButton;
            sim.band=frame->band;sim.currentPortal=frame->portal;sim.portalSerial=frame->portalSerial;
            layer->m_cameraHeightOffset=frame->height;layer->m_targetCameraHeightOffset=frame->targetHeight;
            layer->m_cameraWidthOffset=frame->width;layer->m_cameraUnzoomedHeightOffset=frame->unzoomedHeight;
            layer->m_calculateTargetHeightOffset=frame->calculateHeight;layer->m_freezeStartCamera=frame->freezeCamera;
            layer->m_updateGroundShadows=frame->groundShadows;
        }
        out.cacheResumeTicks=resume;
        for (int t = resume; t < limit; ++t) {
            if (Clock::now() >= deadline) {
                out.interrupted = true;
                break;
            }
            bool pulseMode = cubePulseMode(clones[0], clones[1], dual);
            const unsigned portalSerialBefore = sim.portalSerial;
            const bool orbContact =
                touchingUsableOrb(clones[0]) || (dual && touchingUsableOrb(clones[1]));
            bool imminentOrb = false;
            for (int i = 0; i < players; ++i)
                imminentOrb |= orbImminent(clones[i], travelSteps[i]);
            bool canPress = cubeCanPress(clones[0]) || (dual && cubeCanPress(clones[1]));
            if (pulseMode && clones[0]->m_isOnGround && !pulse.mustRelease())
                out.groundedActions.push_back(t);
            if (orbContact && !pulse.mustRelease()) {
                // Contact is an action opportunity, not a certificate. Reaching
                // a ring does not prove that pressing/skipping it is safe.
                addOrbAction(t);
            }
            for (int i = 0; i < players; ++i) {
                auto *cp = clones[i].data();
                const auto cm = modeState(cp).controlMode();
                if (t < plan.length &&
                    controlOpportunity(cm, cp->m_isOnGround, touchingUsableOrb(cp)))
                    out.controlActions.push_back({t, cm});
            }
            // A continuous new mode keeps the last requested control while a
            // portal near the horizon receives its required reaction window.
            const bool tailHold = t >= plan.length && plan.length > 0 &&
                !pulseMode && modePolicy(modeState(clones[0]).controlMode()).input == InputKind::Thrust;
            const bool requested = schedule.request((t < plan.length && plan.at(t)) ||
                (tailHold && plan.at(plan.length - 1)),
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
                const bool inputChanged = applyJumpState(
                    down, [&] { return p->m_holdingButtons[1]; },
                    [&] { p->pushButton(PlayerButton::Jump); },
                    [&] { p->releaseButton(PlayerButton::Jump); });
                if (inputChanged && down) refreshPressedJumpSnapshot(*p);
                // Native update already advances jump snapshots and state
                // counters at its end. Do not advance them twice per substep.
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
                    out.nativeDeath = true;
                    break;
                }
                p->update(physicsDt);
                Point after{p->getPositionX(), p->getPositionY()};
                Point travel = travelSteps[i];
                if (modeState(p) != out.modeTraces[i].front() && rootVelocity[i] > .0001) {
                    const float magnitude = std::hypot(travel.x, travel.y) *
                        float(std::abs(p->getCurrentXVelocity()) / rootVelocity[i]);
                    const float signedStep = p->m_isGoingLeft ? -magnitude : magnitude;
                    travel = p->m_isSideways ? Point{0, signedStep} : Point{signedStep, 0};
                }
                auto completed = completeTravelStep(before, after, travel, p->m_isSideways);
                if (completed.x != after.x || completed.y != after.y)
                    p->setPosition({completed.x, completed.y});
                // Mirror the real collision/update order observed in this game.
                // Never run the native collision cycle twice for one step.
                if (!physicsOrder.collisionFirst)
                    layer->checkCollisions(p, physicsDt, false);
                const auto currentMode = modeState(p);
                if (currentMode != previousModes[i] || sim.portalSerial != portalSerialBefore) {
                    out.transitions.push_back({t + 1, currentMode.controlMode()});
                    portalProof.changed(t);
                    if (modePolicy(currentMode.controlMode()).mustLand &&
                        currentMode != previousModes[i]) {
                        flights[i] = FlightCertificate(p->m_isOnGround);
                        safeLanding[i] = false;
                    }
                    previousModes[i] = currentMode;
                    if (settleFlight) limit = std::max(limit, std::min(MaxHorizon,
                        t + 2 + int(std::ceil(.18 / std::max(.001f, dt)))));
                    if (settleFlight && modePolicy(currentMode.controlMode()).mustLand && !p->m_isOnGround)
                        limit = MaxHorizon;
                }
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
                    out.nativeDeath = true;
                    out.traces[i].push_back({p->getPositionX(), p->getPositionY()});
                    break;
                }
                {
                    auto rectAfter = p->getObjectRect(.35f, .35f);
                    auto bounds = [](CCRect r) {
                        return Bounds{r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()};
                    };
                    // Native collision is the death oracle. Coarse object
                    // rectangles are used only to rank room, not to kill legal
                    // near-corner spike/saw paths or discontinuous teleports.
                }
                if (!std::isfinite(p->getPositionX()) || !std::isfinite(p->getPositionY())) {
                    out.died = true;
                    break;
                }
                out.traces[i].push_back({p->getPositionX(), p->getPositionY()});
                out.motions[i].push_back(motionSample(p));
                out.modeTraces[i].push_back(currentMode);
                if (p->m_wasTeleported) out.discontinuities[i].push_back(t + 1);
                {
                    auto pr = p->getObjectRect();
                    const float margin = hazardGuard.clearance(
                        {pr.getMinX(), pr.getMinY(), pr.getMaxX(), pr.getMaxY()});
                    out.endpointClearance = i == 0 ? margin : std::min(out.endpointClearance, margin);
                    out.clearance = std::min(out.clearance, margin);
                    if (t < nearClearanceTicks) out.nearClearance = std::min(out.nearClearance, margin);
                    if (landingMode(p) && !p->m_isOnGround)
                        out.landingMargin = std::min(out.landingMargin, margin);
                }
                if (landingMode(p)) {
                    if(groundedBefore[i]&&!p->m_isOnGround&&!down&&out.supportLossTick<0)
                        out.supportLossTick=t;
                    if (down && groundedBefore[i] && !p->m_isOnGround &&
                        currentMode.mode == PlannerMode::Cube && !orbContact)
                        ++out.cubeTakeoffs;
                    if (!groundedBefore[i] && p->m_isOnGround && i == 0) {
                        ++out.landingCount;
                        if (out.firstLandingTick < 0) out.firstLandingTick = t;
                    }
                    const bool wasVerified = flights[i].verified();
                    flights[i].observe(groundedBefore[i], p->m_isOnGround, down);
                    if (!wasVerified && flights[i].verified()) {
                        const float travel = p->m_isSideways ? travelSteps[i].y : travelSteps[i].x;
                        const int runway = landingRunwayTicks(p, travel);
                        safeLanding[i] = landingDecisionSafe(runway, landingReactionTicks,
                            p->m_collidingWithLeft, p->m_collidingWithRight);
                        if (i == 0) out.landingRunwayTicks = runway;
                    }
                    if (!flights[i].verified()) safeLanding[i] = false;
                }
                // The real layer advances this timer in seconds, separately
                // from PlayerObject's frame-unit integration argument.
                p->m_totalTime += dt;
            }
            if (out.died)
                break;
            bool stayedGrounded = !down;
            for (int i = 0; i < players; ++i)
                stayedGrounded &= groundedBefore[i] && clones[i]->m_isOnGround;
            if (stayedGrounded && out.groundedPrefixTicks == t) ++out.groundedPrefixTicks;
            ++out.survived;
            out.realized.length = out.survived;
            bool safeHandoff = true, anyFlight = false;
            for (int i = 0; i < players; ++i) {
                if (!landingMode(clones[i])) continue;
                anyFlight |= flights[i].required;
                safeHandoff &= flights[i].verified() && (!flights[i].required || safeLanding[i]);
            }
            const bool afterPortalSafe = portalProof.verified(out.survived, dt);
            const bool localHandoff = plan.stopAtLanding && landingRollout && anyFlight &&
                safeHandoff && afterPortalSafe && (plan.landingDelay>=0
                    ? out.landingCount>=2 : t >= lastRequestedTick(plan));
            out.decisionPoint = safeHandoff && (anyFlight ||
                (portalProof.required() && afterPortalSafe));
            layer->m_gameState.m_levelTime += dt;
            // Store exact native prefixes only within this live command. A
            // sibling resumes before its first different request, including
            // pulse state, consumed rings, portal band and landing obligation.
            if(allowCache&&t+1>=16&&((t+1)%48==0||t+1==plan.length||localHandoff)&&t+1<=plan.length&&!sim.boundary&&Clock::now()<deadline){
                ForecastFrame frame;frame.prefix=out;frame.world.capture(layer->m_gameState);
                for(int i=0;i<players;++i)frame.players[i].capture(clones[i]);
                frame.pulse=pulse;frame.schedule=schedule;frame.portalProof=portalProof;
                frame.flights=flights;frame.safeLanding=safeLanding;frame.previousModes=previousModes;
                frame.previousButton=previousButton;frame.band=sim.band;frame.portal=sim.currentPortal;
                frame.portalSerial=sim.portalSerial;
                for(auto const &flags:sim.changed)frame.interactions.emplace_back(flags.object,flags.enhanced,flags.ring);
                frame.height=layer->m_cameraHeightOffset;frame.targetHeight=layer->m_targetCameraHeightOffset;
                frame.width=layer->m_cameraWidthOffset;frame.unzoomedHeight=layer->m_cameraUnzoomedHeightOffset;
                frame.calculateHeight=layer->m_calculateTargetHeightOffset;frame.freezeCamera=layer->m_freezeStartCamera;
                frame.groundShadows=layer->m_updateGroundShadows;
                forecastCache.save(plan,t+1,std::move(frame));
            }
            if (localHandoff || (!settleFlight && out.survived >= plan.length) ||
                routeWindowFinished(out.survived, plan.length, !landingRollout || safeHandoff, afterPortalSafe) || sim.boundary) break;
        }
        out.boundary = sim.boundary && !out.died;
        out.postPortalTicks = portalProof.after(out.survived);
        out.portalVerified = portalProof.required() && !out.died &&
            portalProof.verified(out.survived, dt);
        bool allLanded = true;
        for (int i = 0; i < players; ++i) {
            if (landingMode(clones[i])) {
                out.requiresLanding |= flights[i].required;
                allLanded &= flights[i].verified() && (!flights[i].required || safeLanding[i]);
            }
        }
        out.landingVerified = out.requiresLanding && allLanded && out.decisionPoint;
        // The horizon endpoint must obey the same runway rule as an early
        // landing handoff. Otherwise the last touchdown just before a spike
        // is incorrectly certified when the short horizon expires.
        out.firstActionVerified = flightActionSafe(
            out.requiresLanding, allLanded, out.decisionPoint, false) &&
            portalProof.verified(out.survived, dt);
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
        usingQueuedInput = false;
        if ((ticks & 15) == 0)
            settings.read();
        if (!settings.enabled) {
            if (wasEnabled) {
                releaseLiveInput();
                controller.reset();
                cubePulse.reset();
                inputQueue.reset();
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
        if (!semanticReady)
            refreshSemantic();
        else if ((ticks & 3) == 0 && semantic.refreshPositions()) {
            hintObjects = semantic.hazards(); hintSolids = semantic.solids();
            hintOrbs = semantic.orbs(); hintControls = semantic.controlObjects();
            inputQueue.reset(); controller.reset();
        }
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
            float step = std::abs(float(real->getCurrentXVelocity())) * timing.nativeStep(dt);
            Point expected = real->m_isSideways ? Point{0, real->m_isGoingLeft ? -step : step}
                                                : Point{real->m_isGoingLeft ? -step : step, 0};
            if (motionValid && previousDt > 0 && modeStateValid && modeState(real) == lastModes[i]) {
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
        float error = 0;
        for (int i = 0; i < (layer->m_gameState.m_isDualMode ? 2 : 1); ++i) {
            auto *real = i == 0 ? p : layer->m_player2;
            if (!real || decision.selected.traces[i].size() <= 1) continue;
            auto predicted = decision.selected.traces[i][1];
            error = std::max(error, std::hypot(real->getPositionX() - predicted.x,
                                             real->getPositionY() - predicted.y));
        }
        const bool liveDual = layer->m_gameState.m_isDualMode;
        std::array<ModeState, 2> liveModes{modeState(p), modeState(layer->m_player2)};
        const bool stateChanged = !modeStateValid || lastDual != liveDual ||
            lastModes[0] != liveModes[0] || (liveDual && lastModes[1] != liveModes[1]);
        if (stateChanged ||
            (positionValid && std::hypot(p->getPositionX() - lastPosition.x,
                                        p->getPositionY() - lastPosition.y) > 40) || error > 2.0f) {
            controller.reset();
            if (stateChanged) cubePulse.reset();
        }
        lastModes = liveModes;
        lastDual = liveDual;
        modeStateValid = true;
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
        std::array<Point, 2> livePositions{Point{p->getPositionX(), p->getPositionY()}, {}};
        if (liveDual && layer->m_player2)
            livePositions[1] = {layer->m_player2->getPositionX(), layer->m_player2->getPositionY()};
        std::array<MotionSample, 2> liveMotion{motionSample(p), motionSample(layer->m_player2)};
        // Keep the witness's membership fixed for the queued route. Scrolling
        // the hash window itself is not a world change.
        if (!worldSignatureValid || inputQueue.remaining() == 0) worldAnchorX = p->getPositionX();
        const float witnessRange = std::max(1200.f, MaxHorizon * std::max(std::abs(travelSteps[0].x), std::abs(travelSteps[0].y)) + 256.f);
        const auto signature=semantic.signature(worldAnchorX, witnessRange);
        const bool worldChanged=worldSignatureValid&&signature!=worldSignature;
        worldSignature=signature;worldSignatureValid=true;
        if(worldChanged){inputQueue.reset();controller.reset();}
        auto const &world = layer->m_gameState;
        activeWorldMotion = !world.m_dynamicMoveActions.empty() || !world.m_dynamicRotateActions.empty() ||
            !world.m_advanceFollowInstances.empty() || !world.m_gameObjectPhysics.empty() ||
            !world.m_moveEffectInstances.empty() || !world.m_rotateEffectInstances.empty() ||
            !world.m_scaleEffectInstances.empty();
        // The local oracle does not integrate the trigger/action manager.
        // Active motion must be reobserved on every command; a frozen-world
        // forecast cannot authorize a long queue through it.
        if (activeWorldMotion) inputQueue.reset();
        const bool queueReady = inputQueue.matches(livePositions, liveModes, liveDual, dt, &liveMotion);
        if (!queueReady) inputQueue.reset();
        // Commit a previously verified critical click before another search
        // can consume the narrow orb/takeoff window. No geometry-only reflex.
        const bool criticalClick = queueReady && inputQueue.transitionDue(botHeld) &&
            (!inputQueue.down() || !pulseMode || !cubePulse.mustRelease());
        if (criticalClick) {
            decision = inputQueue.continuation();
            usingQueuedInput = true;
            inputPending = true;
            message = "BOT | verified input transition";
            plannerStage = "QUEUED-INPUT";
            finishTick(start, error);
            return;
        }
        // A complete certified lineage is cheaper and more accurate than
        // re-solving it each tick. Re-anchor early, while reaction time remains.
        const int refreshLead=reactionTicks(dt,pulseMode?.14:.08);
        if(queueReady&&inputQueue.remaining()>refreshLead){
            decision=inputQueue.continuation();usingQueuedInput=true;
            inputPending=true;plannerStage="VERIFIED";message="BOT | checked timeline";
            ++lightTicks;finishTick(start,error);return;
        }
        // Observe game state and release input every tick. Heavy prediction is
        // 60 Hz during uncontrollable cube flight at 240 TPS, and every tick
        // on the ground, at an orb or in a continuous-control mode.
        bool nearAction = false;
        const float px = p->getPositionX();
        auto o = std::lower_bound(hintOrbs.begin(), hintOrbs.end(), px - 12.f,
                                  [](auto const &a, float x) { return a.first < x; });
        bool orbSoon = o != hintOrbs.end() && o->first <= px + 260.f;
        auto h = std::lower_bound(hintObjects.begin(), hintObjects.end(), px - 12.f,
                                  [](auto const &a, float x) { return a.first < x; });
        nearAction = (h != hintObjects.end() && h->first <= px + 260.f) || orbSoon;
        auto controlNear = std::lower_bound(
            hintControls.begin(), hintControls.end(), px - 10.f,
            [](auto const &a, float x) { return a.first < x; });
        for (auto it = controlNear;
             it != hintControls.end() && it->first <= px + 300.f; ++it) {
            auto *obj = it->second;
            if (obj && !obj->m_isDisabled && !obj->m_isDisabled2 &&
                (boundaryObject(obj) || padObject(obj) || orbObject(obj))) {
                nearAction = true;
                break;
            }
        }

        if (pulseMode) {
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
        if (pulseMode && !canPress && !nearAction && (ticks & 3) != 0 &&
            queueReady && !inputQueue.down()) {
            // Reuse only a state-matched verified suffix. A stale position-only
            // light trace cannot authorize input or remain on the display.
            decision = inputQueue.continuation();
            usingQueuedInput = true;
            decision.elapsedMs = 0;
            decision.candidates = 0;
            inputPending = true;
            message = "BOT | cube flight / verified release";
            plannerStage = "LIGHT";
            ++lightTicks;
            finishTick(start, error);
            return;
        }
        seeds[0].capture(p);
        if (layer->m_player2)
            seeds[1].capture(layer->m_player2);
        predictionError = error;
        forecastCache.reset();
        Config searchConfig = settings.config;
        searchConfig.horizon = std::clamp(std::max(searchConfig.horizon,
            int(std::ceil(settings.lookaheadSeconds / std::max(.001f, dt)))), 24, MaxPlanningHorizon);
        searchConfig.tickSeconds = dt;
        const int liveMode = mode(p);
        const bool orbOpportunity = touchingOrb || imminentOrb || cubePulse.orbArmed();
        searchConfig.forceRelease =
            pulseMode && (cubePulse.mustRelease() || (!canPress && !orbOpportunity));
        searchConfig.cubePulsePlanning = pulseMode;
        searchConfig.plannerMode = liveModes[0].controlMode();
        searchConfig.dual = liveDual;
        searchConfig.secondaryMode = liveDual ? liveModes[1].controlMode() : PlannerMode::Generic;
        searchConfig.compareFirstActions = !pulseMode ||
            (nearAction && canPress && !searchConfig.forceRelease);
        searchConfig.immediateOrb =
            orbOpportunity && (!pulseMode || !cubePulse.mustRelease());
        searchConfig.priorityControl = pulseMode && (nearAction || searchConfig.immediateOrb);
        if (liveMode == 0) {
            // Keep planning while the cube is still on safe ground. The longer
            // rolling horizon sees the edge/gap/orb before the takeoff is urgent.
            searchConfig.horizon = std::max(searchConfig.horizon, 216);
            searchConfig.candidates = std::max(searchConfig.candidates, 64);
            searchConfig.budgetMs = std::max(searchConfig.budgetMs, nearAction ? 3.5 : 1.35);
            if (nearAction)
                searchConfig.candidates = std::max(searchConfig.candidates, 96);
            if (orbSoon) {
                searchConfig.horizon = std::max(searchConfig.horizon, MaxPlanningHorizon);
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
        } else {
            // Fingerdash-class sections need full-rate mode-aware control, but
            // only dense local geometry receives the expensive 96-candidate
            // burst. Open sections stay much cheaper.
            const bool tightMode = liveMode == 1 || liveMode == 4 || liveMode == 7;
            searchConfig.horizon =
                std::max(searchConfig.horizon, tightMode ? 216 : 180);
            searchConfig.candidates =
                std::max(searchConfig.candidates, nearAction ? 96 : 64);
            searchConfig.budgetMs =
                std::max(searchConfig.budgetMs, nearAction ? 3.8 : 1.6);
            if (orbSoon || searchConfig.immediateOrb) {
                searchConfig.horizon = std::max(searchConfig.horizon, 240);
                searchConfig.candidates = 96;
                searchConfig.budgetMs =
                    std::max(searchConfig.budgetMs,
                             searchConfig.immediateOrb ? 5.0 : 4.2);
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
        routeHandoffBypassed = false;
        routeJumpEvents = 0;
        routeOrbEvents = 0;

        if (!pulseMode) {
            std::vector<Bounds> modeOrbs;
            auto firstOrb =
                std::lower_bound(hintOrbs.begin(), hintOrbs.end(), originX - range,
                                 [](auto const &a, float x) { return a.first < x; });
            for (auto it = firstOrb;
                 it != hintOrbs.end() && it->first <= originX + range; ++it) {
                auto *obj = it->second;
                if (!obj || obj->m_isDisabled || obj->m_isDisabled2)
                    continue;
                auto r = obj->getObjectRect();
                modeOrbs.push_back(
                    {r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()});
            }
            const int players = layer->m_gameState.m_isDualMode ? 2 : 1;
            for (int i = 0; i < players; ++i) {
                auto *real = i == 0 ? p : layer->m_player2;
                if (!real)
                    continue;
                auto pr = real->getObjectRect();
                seedOrbContactEvents(
                    searchConfig,
                    {pr.getMinX(), pr.getMinY(), pr.getMaxX(), pr.getMaxY()},
                    travelSteps[i].x, modeOrbs);
            }
            routeOrbEvents = searchConfig.orbEventCount;
        }

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
                if (!o || o->m_isDisabled || o->m_isDisabled2)
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

                if (!real->m_isSideways && !real->m_isUpsideDown)
                    searchConfig.landingChain |= landingChainAhead(real->getPositionX(),
                        pr.getMinY(), travelSteps[i].x, solids);
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
                        searchConfig.candidates = std::max(searchConfig.candidates, 96);
                        searchConfig.budgetMs = std::max(searchConfig.budgetMs, 3.5);
                    }
                }
            }
            routeJumpEvents = searchConfig.jumpEventCount;
            routeOrbEvents = searchConfig.orbEventCount;
        }
        hazardGuard.set(guardBounds);
        // Portal geometry seeds candidate timing, never an artificial wall.
        // Native collision decides whether that particular portal is entered.

        // Keep the controller inside a fraction of the actual physics-step
        // budget. At 240 TPS one tick is ~4.17 ms; sustained 3-5 ms searches
        // leave little time for the rest of the game thread.
        nearAction |= routeGap.found || routeHandoff.found;
        const double tickMs = std::clamp(double(dt) * 1000.0, 2.0, 20.0);
        const bool overloaded = totalMs > tickMs * .70;
        const double hardPlannerCap = std::min(2.0, tickMs * (nearAction ? .48 : overloaded ? .28 : .36));
        // Scene capture and geometry preparation precede native rollout work.
        // Beta.2 charged all preparation against the rollout deadline, which
        // could leave ZERO oracle calls on a phone. Give the next-action phase
        // its bounded work allowance, and expose total cost separately.
        simulationRoot.capture(layer->m_gameState);
        const auto rolloutStart = Clock::now();
        searchConfig.sharedDeadline = rolloutStart +
            std::chrono::duration_cast<Clock::duration>(
                std::chrono::duration<double, std::milli>(hardPlannerCap));

        // One frontier replaces independently ranked FAST/RESCUE/EMERGENCY
        // plans. The native prefix cache shares work inside this command only.
        searchConfig.budgetMs=std::min(searchConfig.budgetMs,hardPlannerCap);
        struct Oracle {
            Runtime *runtime; float dt;
            Evaluation prefix(Plan const &plan, Clock::time_point deadline, bool memo) {
                // A touchdown policy must finish its final airborne segment
                // after the active input window. Cutting at that window made
                // valid short stair chains look like unfinished flights.
                return runtime->evaluate(plan, deadline, dt, plan.length, memo, plan.landingDelay>=0);
            }
        };
        decision = controller.decide(searchConfig, botHeld, Oracle{this, dt});
        plannerStage = "GDS-NATIVE-DP";
        if (activeWorldMotion && decision.hasValidatedInput) {
            decision.hasValidatedInput = false;
            decision.completeContinuation = decision.recedingCertificate = false;
            decision.recoveryInput = true; decision.recoveryTicks = 1;
            decision.certificateTicks = 0;
            trimProof(decision.selected, 1); decision.plan = decision.selected.realized;
            decision.selected.firstActionVerified = false;
            plannerStage = "LIVE-WORLD";
        }
        if(!decision.hasValidatedInput&&queueReady){
            auto queued=inputQueue.continuation();retainSearchDiagnostics(queued,decision);
            decision=std::move(queued);usingQueuedInput=true;plannerStage="CHECKED-KEPT";
        }
        if (usingQueuedInput && decision.forecastTicks > 0)
            controller.seedVerifiedPrefix(decision.forecastPlan, decision.forecastTicks);

        if (decision.hasValidatedInput || decision.recoveryInput || searchConfig.forceRelease) {
            inputPending = true;
            message = decision.recoveryInput            ? "BOT | local control / landing unproved"
                      : !decision.hasValidatedInput     ? "BOT | forced release"
                      : decision.completeContinuation   ? "BOT | online"
                      : decision.recedingCertificate   ? "BOT | receding certificate"
                      : decision.selected.interrupted  ? "BOT | partial forecast"
                                                        : "BOT | short forecast";
        } else
            message = "BOT | no surviving candidate";
        // If neither a verified route nor a checked recovery prefix exists,
        // release and expose the failure in the HUD.
        if (!decision.hasValidatedInput && !decision.recoveryInput) {
            if (ownsInput) releaseLiveInput();
            inputPending = false;
            message = "BOT | NO VERIFIED ROUTE / manual input";
            stableTrace.clear();
        }
        if (!usingQueuedInput) {
            if (decision.hasValidatedInput) inputQueue.seed(decision, liveModes, liveDual, dt);
            else inputQueue.reset();
        }
        finishTick(start, error);
    }
    void finishTick(Clock::time_point start, float error) {
        controller.advanceWithInput(decision.down);
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
                                  e.boundary, predictionError, lightTicks, reflexJumps)
            .append(fmt::format(",{:016x},{},{},{},{}", e.routeId, e.transitions.size(),
                e.postPortalTicks, e.portalVerified, inputQueue.remaining()))
            .append(fmt::format(",{},{},{},{},{},{},{},{},{},{}", decision.repairsCreated, decision.repairsTested,
                decision.proofKind, decision.forecastDied, decision.forecastInterrupted, e.supportLossTick,
                forecastCache.hits, forecastCache.skippedTicks, decision.coldVerificationTicks, decision.cacheMismatches))
            .append(fmt::format(",{},{},{},{},{},{}", decision.forecastTicks, decision.jumpWindowTicks,
                decision.reachableCells, decision.mergedStates, decision.resumedPlans, decision.retainedPlans)));
        if (csv.size() >= 256)
            flush();
    }
    void applyInput() {
        if (!inputPending || !controlling || !settings.enabled || !layer || layer->m_isPaused ||
            !layer->m_player1 || layer->m_player1->m_isDead)
            return;
        inputPending = false;
        ownsInput = true;
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
        if (down != botHeld) {
            // Feed the normal command path too, so real button game events
            // and triggers run in the live step. The physics boundary below
            // only enforces the resulting button state.
            injected = true;
            layer->handleButton(down, 1, true);
            injected = false;
        }
        const int players = layer->m_gameState.m_isDualMode ? 2 : 1;
        for (int i = 0; i < players; ++i) {
            auto *p = i == 0 ? layer->m_player1 : layer->m_player2;
            if (!p || p->m_isDead)
                continue;
            if (applyJumpState(
                    down, [&] { return p->m_holdingButtons[1]; },
                    [&] { p->pushButton(PlayerButton::Jump); },
                    [&] { p->releaseButton(PlayerButton::Jump); })) {
                ++inputTransitions;
                if (down) refreshPressedJumpSnapshot(*p);
            }
        }
        botHeld = layer->m_player1->m_holdingButtons[1];
        bool actualMatches = botHeld == down;
        if (dual && layer->m_player2) actualMatches &= layer->m_player2->m_holdingButtons[1] == down;
        if (actualMatches) inputQueue.consume(down);
        else inputQueue.reset();
    }
    void enforceInputAtPhysics(PlayerObject *p) {
        if (!nativeCommandActive || !controlling || !ownsInput || !settings.enabled || !p || p->m_isDead)
            return;
        if (p != layer->m_player1 && p != layer->m_player2) return;
        // processCommands can drain a queued UI/reset release after the
        // planner's command. Reassert the SAME command at the physics boundary,
        // without consuming the queue or advancing the cube pulse twice.
        if (p->m_holdingButtons[1] != decision.down) ++inputPhaseMismatches;
        if (applyJumpState(decision.down, [&] { return p->m_holdingButtons[1]; },
                [&] { p->pushButton(PlayerButton::Jump); },
                [&] { p->releaseButton(PlayerButton::Jump); }) && decision.down)
            refreshPressedJumpSnapshot(*p);
    }
    void refreshVisualPreviews(Clock::time_point now) {
        // Use spare queued-control frames. Presentation work cannot starve
        // a new landing search or issue input; every preview is a fresh native
        // rollout from the player currently on screen, capped including capture.
        if(!usingQueuedInput || now<previewNext || previousDt<=0 || totalMs>2.5 ||
           !layer->m_player1 || layer->m_player1->m_isDead || layer->m_isPaused)return;
        previewNext=now+std::chrono::milliseconds(17);
        auto start=Clock::now(),end=start+std::chrono::microseconds(800);
        simulationRoot.capture(layer->m_gameState);
        seeds[0].capture(layer->m_player1);
        if(layer->m_gameState.m_isDualMode)seeds[1].capture(layer->m_player2);
        for(int j=0;j<2;++j) {
            int index=(j+previewParity)%2;
            auto deadline=j==0?start+std::chrono::microseconds(400):end;
            if(Clock::now()>=deadline)continue;
            Plan p;p.length=std::min(144,reactionTicks(previousDt,.65));
            p.buttons.fill(index!=0);
            auto e=evaluate(p,deadline,previousDt,p.length,false,false);
            if(e.survived>1)visualPreviews[index]=std::move(e);
        }
        ++previewParity;
    }
    void render() {
        if (!draw || !layer || sim.active)
            return;
        settings.read();
        const auto now = Clock::now();
        if (!traceCadence.consume(now, settings.traceHz))
            return;
        if (label && now >= hudRedraw) {
        hudRedraw = now + std::chrono::milliseconds(125);
        if (!settings.debugTraces) {
            std::string jump = "";
            for (int t = 0; t < decision.forecastPlan.length &&
                    t < int(decision.forecast.traces[0].size()); ++t) {
                if (decision.forecastPlan.at(t) && decision.forecast.motions[0].size() > size_t(t) &&
                    (decision.forecast.motions[0][t].canJump || decision.forecast.motions[0][t].grounded)) {
                    jump = fmt::format(" | jump x={:.1f} samples={}", decision.forecast.traces[0][t].x,
                                       decision.jumpWindowTicks);
                    break;
                }
            }
            label->setString(fmt::format("AutoGDBot RT6 | {}\nchecked={} forecast={:.2f}s{}",
                message, decision.certificateTicks, decision.forecastTicks * previousDt, jump).c_str());
        } else {
        label->setString(
            fmt::format(
                "AutoGDBot 1.0 RT | {} | stage={}\nsearch={:.2f} total={:.2f} ms | {} candidates | {} ticks | cert={} | "
                "{}\n"
                "step={} | want={} | actual={} | sent={}\n"
                "dx={:.2f} | dt={:.5f}->{:.3f}{} | UP={} DOWN={} | tested={}/{}\n"
                "error={:.2f} | guard={} | light={} | reflex={} | forecast={} | landing={} runway={}\n"
                "route: mode={} jumps={} orbs={} map-orbs={} touch={} gap={} handoff={} | rejected={} | order={}{}\n"
                "failure: best={} deaths={} guard={} native={} landing={} timeout={} | landings={}\n"
                "native: U={}/{} C={} saw={} mismatch={} orb-hit={}/{} pad-hit={}/{} queue={}\n{}",
                message, plannerStage, decision.elapsedMs, totalMs, decision.candidates,
                decision.predictedTicks, decision.certificateTicks,
                "NATIVE",
                ticks, decision.down ? "DOWN" : "UP",
                botHeld ? "DOWN" : "UP", inputTransitions, travelSteps[0].x, previousDt,
                timing.nativeStep(previousDt), timing.measured() ? "*" : "",
                decision.alternatives[0].survived, decision.alternatives[1].survived,
                decision.firstActionCandidates[0], decision.firstActionCandidates[1],
                predictionError, geometryRejects, lightTicks, reflexJumps,
                decision.completeContinuation  ? "HORIZON"
                : decision.selected.boundary   ? "PORTAL"
                : decision.selected.decisionPoint ? "EVENT"
                : decision.recoveryInput       ? "RECOVERY"
                : decision.recedingCertificate ? (decision.selected.died ? "RECEDING-DEATH"
                                                                           : "RECEDING")
                : decision.selected.interrupted ? "PARTIAL"
                : decision.selected.died        ? "DEAD"
                                                : "SHORT",
                decision.selected.requiresLanding
                    ? (decision.selected.landingVerified ? "OK" : "NO")
                    : "n/a",
                decision.selected.landingRunwayTicks,
                fmt::format("{}{}", modeName(modeState(layer->m_player1).controlMode()),
                    layer->m_gameState.m_isDualMode
                        ? fmt::format("+{}", modeName(modeState(layer->m_player2).controlMode())) : ""),
                routeJumpEvents, routeOrbEvents,
                semantic.stats().counts[static_cast<size_t>(SemanticKind::Orb)],
                touchingUsableOrb(layer->m_player1)
                    ? "ORB"
                    : (cubePulse.orbArmed() ? "ARM" : "no"),
                routeGap.found ? "YES" : "no",
                routeHandoffBypassed ? "BYPASS" : (routeHandoff.found ? "HINT" : "no"),
                decision.rejectedUnsafe,
                physicsOrder.collisionFirst ? "C-U" : "U-C",
                physicsOrder.measured ? "*" : "?", decision.bestRejectedTicks,
                decision.deaths, decision.guardDeaths, decision.nativeDeaths,
                decision.landingRejects, decision.interruptions,
                decision.selected.landingCount, nativeUpdates, nativeUpdatesInside, nativeCollisions,
                nativeSawDown ? "DOWN" : "UP", inputPhaseMismatches,
                orbHits, orbAttempts, padHits, padAttempts, inputQueue.remaining(), lastAttempt)
                .append(fmt::format("\nplan={:016x} transitions={} after={} checked={}",
                    decision.selected.routeId, decision.selected.transitions.size(),
                    decision.selected.postPortalTicks, decision.selected.portalVerified ? "YES" : "no"))
                .append(fmt::format(" repairs={}/{}", decision.repairsTested, decision.repairsCreated))
                .append(fmt::format("\nproof={} tail-death={} memo(last)={}/{} cold={} mismatch={}",
                    decision.proofKind,decision.forecastDied,forecastCache.hits,forecastCache.skippedTicks,
                    decision.coldVerificationTicks,decision.cacheMismatches))
                .c_str());
        }
        label->setColor(decision.hasValidatedInput ? ccColor3B{120, 255, 170}
                                                   : ccColor3B{255, 180, 100});
        }
        draw->clear();
        if (!settings.enabled)
            return;
        if(settings.showTrace)refreshVisualPreviews(now);
        // Render only the decision actually used this tick. An older line
        // cannot keep looking approved after a new forecast rejects the route.
        stableTrace.clear();
        if (decision.hasValidatedInput || decision.recoveryInput)
            stableTrace = decision.selected.traces[0];
        if (decision.hasValidatedInput && stableTrace.size() > size_t(decision.certificateTicks + 1))
            stableTrace.resize(decision.certificateTicks + 1);
        if (decision.recoveryInput && stableTrace.size() > size_t(decision.recoveryTicks + 1))
            stableTrace.resize(decision.recoveryTicks + 1);
        auto trace = [&](std::vector<Point> const &points, std::vector<int> const &breaks,
                         ccColor4F color, float width,int player=0,size_t begin=0,bool mark=false) {
            auto *p=player?layer->m_player2:layer->m_player1;if(!p)return;
            auto view=traceView(points,breaks,{p->getPositionX(),p->getPositionY()},
                reactionTicks(previousDt,settings.debugTraces?1.6:.65),
                reactionTicks(previousDt,.18),begin);
            for (auto const &s : view.segments)
                draw->drawSegment({s.a.x, s.a.y}, {s.b.x, s.b.y}, width, color);
            if(mark && view.visible) {
                auto q=view.endpoint;std::array<CCPoint,4> box{{{q.x-5,q.y-5},{q.x-5,q.y+5},
                    {q.x+5,q.y+5},{q.x+5,q.y-5}}};
                draw->drawPolygon(box.data(),4,{0,0,0,0},.45f,color);
            }
        };
        if (settings.showTrace) {
            bool same=visualPreviews[0].traces[0].size()==visualPreviews[1].traces[0].size() &&
                std::equal(visualPreviews[0].traces[0].begin(),visualPreviews[0].traces[0].end(),
                    visualPreviews[1].traces[0].begin(),[](Point a,Point b){return std::hypot(a.x-b.x,a.y-b.y)<.12f;});
            for(int i=0;i<2;++i) {
                if(i==1&&same)continue;
                auto const &e=visualPreviews[i];
                trace(e.traces[0],e.discontinuities[0],e.died?ccColor4F{1,.3f,.3f,.35f}
                    :ccColor4F{.3f,.75f,1,.35f},.35f,0,0,true);
            }
            if(settings.debugTraces)for(auto const &e:decision.alternatives)
                trace(e.traces[0],e.discontinuities[0],{.6f,.6f,1,.2f},.25f);
        }
        bool complete = decision.completeContinuation;
        ccColor4F chosen = decision.selected.died       ? ccColor4F{1, .15f, .15f, .9f}
                           : decision.selected.boundary ? ccColor4F{.3f, .8f, 1, .9f}
                           : decision.hasValidatedInput ? ccColor4F{.2f, 1, .45f, .9f}
                                                        : ccColor4F{1, .65f, .1f, .9f};
        if (settings.showTrace && decision.forecastTicks > 0) {
            // Amber is the uncommitted native forecast; green/orange above
            // represents only the independently replayed control segment.
            for (int i = 0; i < (layer->m_gameState.m_isDualMode ? 2 : 1); ++i)
                trace(decision.forecast.traces[i], decision.forecast.discontinuities[i],
                    decision.hasValidatedInput || decision.recoveryInput
                        ? ccColor4F{1.f, .72f, .25f, .55f} : ccColor4F{1.f, .25f, .25f, .55f}, .65f,i,
                    decision.hasValidatedInput?decision.certificateTicks:decision.recoveryTicks,true);
        }
        if (settings.showTrace) trace(stableTrace, decision.selected.discontinuities[0], chosen, 1.f);
        if (settings.showTrace && layer->m_gameState.m_isDualMode)
            trace(decision.selected.traces[1], decision.selected.discontinuities[1], {.2f, .8f, 1.f, .8f}, .85f,1);
        for (int t = 0; settings.showTrace && t < decision.forecastPlan.length && t < int(decision.forecast.traces[0].size()); ++t) {
            if (!decision.forecastPlan.at(t) || decision.forecast.motions[0].size() <= size_t(t) ||
                !(decision.forecast.motions[0][t].canJump || decision.forecast.motions[0][t].grounded)) continue;
            auto q = decision.forecast.traces[0][t];
            draw->drawSegment({q.x, q.y - 8}, {q.x, q.y + 16}, .7f, {.25f, .9f, 1.f, .75f});
            break;
        }
        if (!settings.showHitboxes) return;
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
                draw->drawPolygon(v.data(), 4, {0, 0, 0, 0}, .6f, {1, .3f, .3f, .6f});
                ++shown;
            }
        }

        if (layer->m_player1) {
            const float x = layer->m_player1->getPositionX();
            int shown = 0;
            auto first = std::lower_bound(hintSolids.begin(), hintSolids.end(), x - 120,
                [](auto const &a, float q) { return a.first < q; });
            for (auto it = first; it != hintSolids.end() && it->first <= x + 700 && shown < 96; ++it) {
                auto *o = it->second;
                if (!o || o->m_isDisabled || o->m_isDisabled2) continue;
                auto r = o->getObjectRect();
                std::array<CCPoint, 4> v{{{r.getMinX(), r.getMinY()}, {r.getMinX(), r.getMaxY()},
                    {r.getMaxX(), r.getMaxY()}, {r.getMaxX(), r.getMinY()}}};
                draw->drawPolygon(v.data(), 4, {0, 0, 0, 0}, .5f, {.3f, .8f, 1.f, .5f});
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
                if (!o || o->m_isDisabled)
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
    void playGravityEffect(bool flip) {
        if (agb::sim.active) return;
        PlayLayer::playGravityEffect(flip);
    }
    void flipArt(bool flip) {
        if (agb::sim.active) return;
        PlayLayer::flipArt(flip);
    }
};
class $modify(AGBBaseLayer, GJBaseGameLayer) {
    void update(float dt) {
        GJBaseGameLayer::update(dt);
        // Keep the overlay on the render-frame entry point too. Rendering
        // must not depend on obtaining an input certificate or on the
        // PlayLayer-specific postUpdate hook being the last override.
        if (agb::active && !agb::sim.active &&
            static_cast<GJBaseGameLayer *>(agb::active->layer) == this)
            agb::active->render();
    }
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
            agb::active->controlling && agb::active->ownsInput && !agb::injected && button == 1)
            return;
        GJBaseGameLayer::handleButton(down, button, p1);
    }
    int checkCollisions(PlayerObject *p, float dt, bool ignoreDamage) {
        if (agb::active && !agb::sim.active)
            agb::active->enforceInputAtPhysics(p);
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
            if (agb::boundaryObject(o) &&
                ((!agb::sim.localPortals && !agb::isIsolatedStatePortal(o)) || !agb::isLocalPhysicsPortal(o))) {
                if (p->getObjectRect().intersectsRect(o->getObjectRect()))
                    agb::sim.boundary = true;
                continue;
            }
            if (agb::physicsObject(o)) {
                agb::sim.save(o);
                filtered.push_back(o);
            }
        }
        GJBaseGameLayer::collisionCheckObjects(p, &filtered, int(filtered.size()), dt);
    }
    void playerWillSwitchMode(PlayerObject *p, GameObject *o) {
        if (agb::sim.isClone(p)) {
            agb::sim.currentPortal = o;
            ++agb::sim.portalSerial;
        }
        GJBaseGameLayer::playerWillSwitchMode(p, o);
        if (agb::sim.isClone(p) && m_gameState.m_isFreeMode)
            agb::sim.boundary = true;
    }
    void updateDualGround(PlayerObject *p, int mode, bool instant, float duration) {
        if (!agb::sim.isClone(p)) {
            GJBaseGameLayer::updateDualGround(p, mode, instant, duration);
            return;
        }
        // Native contact and mode selection have already happened. Replace
        // shared ground-sprite animation with the measured normal-frame band;
        // never move live ground nodes during a hypothetical trajectory.
        if (m_gameState.m_isFreeMode || !agb::sim.localPortals) {
            agb::sim.boundary = true;
            return;
        }
        const float height = getGroundHeight(p, mode);
        if (height != 270.f || mode == int(GameObjectType::SpiderPortal)) {
            if (agb::sim.currentPortal)
                agb::sim.band = agb::portalBand(agb::sim.currentPortal->getPositionY(), height);
        }
    }
    float getMinPortalY() {
        if (agb::sim.active && agb::sim.band.active) return agb::sim.band.floor;
        return GJBaseGameLayer::getMinPortalY();
    }
    float getMaxPortalY() {
        if (agb::sim.active && agb::sim.band.active) return agb::sim.band.ceiling;
        return GJBaseGameLayer::getMaxPortalY();
    }
    void bumpPlayer(PlayerObject *p, EffectGameObject *o) {
        if (o && agb::sim.isClone(p)) agb::sim.save(o);
        const bool live = agb::active && !agb::sim.active && agb::active->controlling &&
            static_cast<GJBaseGameLayer *>(agb::active->layer) == this && o && agb::padObject(o);
        const double velocity = p->m_yVelocity;
        const bool gravity = p->m_isUpsideDown;
        const auto used = p->m_jumpPadRelated.size();
        const auto pos = p->getPosition();
        GJBaseGameLayer::bumpPlayer(p, o);
        if (live) {
            ++agb::active->padAttempts;
            if (velocity != p->m_yVelocity || gravity != p->m_isUpsideDown ||
                used != p->m_jumpPadRelated.size() || pos.x != p->getPositionX() || pos.y != p->getPositionY())
                ++agb::active->padHits;
        }
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
    void playTriggerEffect() {
        if (agb::sim.active) return;
        EffectGameObject::playTriggerEffect();
    }
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
    void ringJump(RingObject *o, bool skipCheck) {
        // Cached touching rings can be activated by update before the next
        // collision pass. Journal the object before that earliest mutation.
        if (o && agb::sim.isClone(this)) agb::sim.save(o);
        if (agb::sim.isClone(this) && o && o->m_objectType == GameObjectType::CustomRing) {
            // activateCustomRing is inline on Windows and cannot be hooked.
            // End this forecast before native group mutations; only the real
            // player executes those effects, followed by live replanning.
            if ((m_jumpBuffered || m_holdingButtons[1]) && !o->m_isDisabled &&
                !o->m_isDisabled2 && m_ringRelatedSet.find(o->m_uniqueID) == m_ringRelatedSet.end())
                agb::sim.boundary = true;
            return;
        }
        const auto velocity = m_yVelocity;
        const auto position = getPosition();
        const bool gravity = m_isUpsideDown, dash = m_isDashing;
        const auto used = m_ringRelatedSet.size();
        PlayerObject::ringJump(o, skipCheck);
        if (agb::active && !agb::sim.active && agb::active->controlling) {
            ++agb::active->orbAttempts;
            if (velocity != m_yVelocity || gravity != m_isUpsideDown ||
                dash != m_isDashing || used != m_ringRelatedSet.size() ||
                position.x != getPositionX() || position.y != getPositionY())
                ++agb::active->orbHits;
        }
    }
    void update(float dt) {
        if (agb::active && !agb::sim.active)
            agb::active->enforceInputAtPhysics(this);
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

