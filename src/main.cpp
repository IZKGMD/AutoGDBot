#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <Geode/modify/PlayerObject.hpp>

#include <algorithm>
#include <cmath>
#include <limits>
#include <vector>

using namespace geode::prelude;

namespace autogd {

constexpr float kInf = std::numeric_limits<float>::infinity();
constexpr float kPlayerHalfHeight = 15.0f;
constexpr float kCachePadding = 140.0f;

static float rectMinX(cocos2d::CCRect const& r) { return r.origin.x; }
static float rectMaxX(cocos2d::CCRect const& r) { return r.origin.x + r.size.width; }
static float rectMinY(cocos2d::CCRect const& r) { return r.origin.y; }
static float rectMaxY(cocos2d::CCRect const& r) { return r.origin.y + r.size.height; }
static float rectCenterY(cocos2d::CCRect const& r) { return r.origin.y + r.size.height * 0.5f; }

static bool rangesOverlap(float a0, float a1, float b0, float b1) {
    return a0 <= b1 && b0 <= a1;
}

enum class Mode {
    Cube,
    Ship,
    Ball,
    UFO,
    Wave,
    Robot,
    Spider,
    Swing,
};

static Mode modeOf(PlayerObject* p) {
    if (p->m_isShip) return Mode::Ship;
    if (p->m_isBall) return Mode::Ball;
    if (p->m_isBird) return Mode::UFO;
    if (p->m_isDart) return Mode::Wave;
    if (p->m_isRobot) return Mode::Robot;
    if (p->m_isSpider) return Mode::Spider;
    if (p->m_isSwing) return Mode::Swing;
    return Mode::Cube;
}

struct CachedObject {
    GameObject* object = nullptr;
    float initialX = 0.0f;
    bool hazard = false;
};

struct Sense {
    float nearestHazard = kInf;
    float nearestHazardWidth = 0.0f;
    float nearestHazardCenterY = 0.0f;
    bool hazardInPlayerLane = false;

    bool hasFloor = false;
    float floorY = -kInf;
    bool hasCeiling = false;
    float ceilingY = kInf;

    float targetY = 0.0f;
};

class RealtimeSolver {
public:
    void rebuild(PlayLayer* layer) {
        m_cache.clear();
        m_lastPlayerX = 0.0f;
        m_haveLastX = false;
        m_botHeld = false;
        m_pulseRemaining = 0.0f;
        m_pulseCooldown = 0.0f;
        m_debugTimer = 0.0f;
        m_lastMode = Mode::Cube;

        if (!layer || !layer->m_objects) return;

        m_cache.reserve(layer->m_objects->count());
        for (unsigned int i = 0; i < layer->m_objects->count(); ++i) {
            auto* obj = typeinfo_cast<GameObject*>(layer->m_objects->objectAtIndex(i));
            if (!obj) continue;

            bool hazard = obj->m_objectType == GameObjectType::Hazard;
            bool solid = obj->m_objectType == GameObjectType::Solid;
            if (!hazard && !solid) continue;

            m_cache.push_back({ obj, obj->getPositionX(), hazard });
        }

        std::sort(m_cache.begin(), m_cache.end(), [](CachedObject const& a, CachedObject const& b) {
            return a.initialX < b.initialX;
        });

        log::info("AutoGDBot: cached {} collision objects", m_cache.size());
    }

    void resetInput(PlayerObject* player) {
        if (player && m_botHeld) {
            player->releaseButton(PlayerButton::Jump);
        }
        m_botHeld = false;
        m_pulseRemaining = 0.0f;
        m_pulseCooldown = 0.0f;
    }

    void tick(PlayLayer* layer, PlayerObject* player, float dt) {
        if (!layer || !player || player != layer->m_player1) return;

        auto* mod = Mod::get();
        if (!mod->getSettingValue<bool>("enabled")) {
            resetInput(player);
            return;
        }

        if (player->m_isDead || layer->m_hasCompletedLevel) {
            resetInput(player);
            return;
        }

        dt = std::clamp(dt, 0.0001f, 0.05f);
        m_pulseRemaining = std::max(0.0f, m_pulseRemaining - dt);
        m_pulseCooldown = std::max(0.0f, m_pulseCooldown - dt);
        m_debugTimer += dt;

        const auto mode = modeOf(player);
        if (mode != m_lastMode) {
            m_pulseRemaining = 0.0f;
            m_pulseCooldown = 0.0f;
            m_lastMode = mode;
        }

        const float px = player->getPositionX();
        const float py = player->getPositionY();

        float measuredVx = 0.0f;
        if (m_haveLastX) measuredVx = (px - m_lastPlayerX) / dt;
        m_lastPlayerX = px;
        m_haveLastX = true;

        float nativeVx = static_cast<float>(player->getCurrentXVelocity());
        float vx = measuredVx;
        if (!std::isfinite(vx) || std::abs(vx) < 1.0f) vx = nativeVx;
        if (!std::isfinite(vx) || std::abs(vx) < 0.01f) vx = 1.0f;

        const float vy = static_cast<float>(player->getYVelocity());
        const float speed = std::max(1.0f, std::abs(vx));
        const float direction = vx >= 0.0f ? 1.0f : -1.0f;

        // Scale lookahead with observed speed, but keep a large enough floor for fast portals.
        const float lookahead = std::clamp(260.0f + speed * 1.20f, 320.0f, 1150.0f);
        Sense sense = senseWorld(player, px, py, direction, lookahead);

        bool desiredHeld = false;
        switch (mode) {
            case Mode::Cube:
                desiredHeld = cubePolicy(player, sense, speed, vy);
                break;
            case Mode::Robot:
                desiredHeld = robotPolicy(player, sense, speed);
                break;
            case Mode::UFO:
                desiredHeld = ufoPolicy(player, sense, speed, py, vy);
                break;
            case Mode::Ball:
                desiredHeld = ballPolicy(player, sense, speed);
                break;
            case Mode::Spider:
                desiredHeld = spiderPolicy(player, sense, speed);
                break;
            case Mode::Ship:
                desiredHeld = continuousFlightPolicy(player, sense, py, vy, 11.0f);
                break;
            case Mode::Wave:
                desiredHeld = continuousFlightPolicy(player, sense, py, vy, 7.0f);
                break;
            case Mode::Swing:
                desiredHeld = continuousFlightPolicy(player, sense, py, vy, 10.0f);
                break;
        }

        setHeld(player, desiredHeld);

        if (mod->getSettingValue<bool>("debug-log") && m_debugTimer >= 0.50f) {
            m_debugTimer = 0.0f;
            log::debug(
                "AutoGDBot mode={} x={:.1f} y={:.1f} vx={:.1f} vy={:.1f} hazard={:.1f} targetY={:.1f} held={}",
                static_cast<int>(mode), px, py, vx, vy,
                std::isfinite(sense.nearestHazard) ? sense.nearestHazard : -1.0f,
                sense.targetY,
                m_botHeld
            );
        }
    }

private:
    std::vector<CachedObject> m_cache;
    float m_lastPlayerX = 0.0f;
    bool m_haveLastX = false;
    bool m_botHeld = false;
    float m_pulseRemaining = 0.0f;
    float m_pulseCooldown = 0.0f;
    float m_debugTimer = 0.0f;
    Mode m_lastMode = Mode::Cube;

    void setHeld(PlayerObject* player, bool held) {
        if (held == m_botHeld) return;
        if (held) {
            player->pushButton(PlayerButton::Jump);
        } else {
            player->releaseButton(PlayerButton::Jump);
        }
        m_botHeld = held;
    }

    void pulse(float seconds, float cooldown) {
        if (m_pulseCooldown > 0.0f) return;
        m_pulseRemaining = std::max(m_pulseRemaining, seconds);
        m_pulseCooldown = cooldown;
    }

    Sense senseWorld(
        PlayerObject* player,
        float px,
        float py,
        float direction,
        float lookahead
    ) const {
        Sense out;
        out.targetY = py;

        if (m_cache.empty()) return out;

        const float searchMinX = px - (direction < 0.0f ? lookahead : kCachePadding);
        const float searchMaxX = px + (direction > 0.0f ? lookahead : kCachePadding);

        auto first = std::lower_bound(
            m_cache.begin(), m_cache.end(), searchMinX - kCachePadding,
            [](CachedObject const& entry, float x) { return entry.initialX < x; }
        );

        float nearestHazardCenter = py;
        bool foundNearestHazard = false;

        for (auto it = first; it != m_cache.end(); ++it) {
            if (it->initialX > searchMaxX + kCachePadding) break;
            GameObject* obj = it->object;
            if (!obj || obj->m_isDisabled) continue;

            auto rect = obj->getObjectRect();
            const float minX = rectMinX(rect);
            const float maxX = rectMaxX(rect);
            const float minY = rectMinY(rect);
            const float maxY = rectMaxY(rect);

            float forwardDistance = direction > 0.0f ? (minX - px) : (px - maxX);
            if (forwardDistance < -35.0f || forwardDistance > lookahead) continue;

            // Build a rough vertical corridor from collision objects in front of the player.
            if (maxY <= py + 3.0f) {
                if (!out.hasFloor || maxY > out.floorY) {
                    out.hasFloor = true;
                    out.floorY = maxY;
                }
            }
            if (minY >= py - 3.0f) {
                if (!out.hasCeiling || minY < out.ceilingY) {
                    out.hasCeiling = true;
                    out.ceilingY = minY;
                }
            }

            if (!it->hazard) continue;

            const bool laneOverlap = rangesOverlap(
                py - kPlayerHalfHeight,
                py + kPlayerHalfHeight,
                minY - 2.0f,
                maxY + 2.0f
            );

            if (laneOverlap && forwardDistance >= 0.0f && forwardDistance < out.nearestHazard) {
                out.nearestHazard = forwardDistance;
                out.nearestHazardWidth = rect.size.width;
                out.nearestHazardCenterY = rectCenterY(rect);
                out.hazardInPlayerLane = true;
                nearestHazardCenter = out.nearestHazardCenterY;
                foundNearestHazard = true;
            }
        }

        if (out.hasFloor && out.hasCeiling && out.ceilingY - out.floorY > 24.0f) {
            out.targetY = (out.floorY + out.ceilingY) * 0.5f;
        } else if (foundNearestHazard && out.nearestHazard < lookahead * 0.72f) {
            // If the direct lane is blocked and only one side is known, move away from the hazard center.
            const float escape = 70.0f;
            if (nearestHazardCenter >= py) out.targetY = py - escape;
            else out.targetY = py + escape;
        }

        // Keep the target inside a known corridor with a small margin.
        if (out.hasFloor) out.targetY = std::max(out.targetY, out.floorY + 24.0f);
        if (out.hasCeiling) out.targetY = std::min(out.targetY, out.ceilingY - 24.0f);

        (void)player;
        return out;
    }

    bool cubePolicy(PlayerObject* player, Sense const& s, float speed, float vy) {
        const float trigger = std::clamp(48.0f + speed * 0.32f, 58.0f, 150.0f);

        if (player->m_isOnGround && s.hazardInPlayerLane && s.nearestHazard <= trigger) {
            // Short edge-triggered pulse. This specifically avoids the old "hold forever after first jump" failure.
            pulse(0.030f, 0.115f);
        }

        // Small pre-landing buffer only when falling into an immediate hazard. It is bounded,
        // never an indefinite cube hold.
        const bool fallingTowardGround = player->m_isUpsideDown ? vy > 0.0f : vy < 0.0f;
        if (!player->m_isOnGround && fallingTowardGround && s.hazardInPlayerLane && s.nearestHazard < trigger * 0.42f) {
            pulse(0.026f, 0.090f);
        }

        return m_pulseRemaining > 0.0f;
    }

    bool robotPolicy(PlayerObject* player, Sense const& s, float speed) {
        const float trigger = std::clamp(52.0f + speed * 0.35f, 64.0f, 165.0f);
        if (player->m_isOnGround && s.hazardInPlayerLane && s.nearestHazard <= trigger) {
            float hold = 0.10f + std::clamp(s.nearestHazardWidth / 260.0f, 0.0f, 0.16f);
            pulse(hold, hold + 0.08f);
        }
        return m_pulseRemaining > 0.0f;
    }

    bool ufoPolicy(PlayerObject* player, Sense const& s, float speed, float py, float vy) {
        const float trigger = std::clamp(70.0f + speed * 0.30f, 78.0f, 180.0f);
        const bool needUp = s.targetY > py + 16.0f;
        const bool danger = s.hazardInPlayerLane && s.nearestHazard < trigger;
        const bool descending = player->m_isUpsideDown ? vy > 0.0f : vy < 0.0f;

        if ((needUp && descending) || danger) {
            pulse(0.025f, 0.085f);
        }
        return m_pulseRemaining > 0.0f;
    }

    bool ballPolicy(PlayerObject* player, Sense const& s, float speed) {
        const float trigger = std::clamp(58.0f + speed * 0.25f, 65.0f, 145.0f);
        if (player->m_isOnGround && s.hazardInPlayerLane && s.nearestHazard <= trigger) {
            pulse(0.022f, 0.105f);
        }
        return m_pulseRemaining > 0.0f;
    }

    bool spiderPolicy(PlayerObject* player, Sense const& s, float speed) {
        const float trigger = std::clamp(50.0f + speed * 0.22f, 58.0f, 132.0f);
        if (player->m_isOnGround && s.hazardInPlayerLane && s.nearestHazard <= trigger) {
            pulse(0.018f, 0.095f);
        }
        return m_pulseRemaining > 0.0f;
    }

    bool continuousFlightPolicy(
        PlayerObject* player,
        Sense const& s,
        float py,
        float vy,
        float hysteresis
    ) const {
        const float error = s.targetY - py;
        const bool heldMovesUp = !player->m_isUpsideDown;

        // Avoid rapid press/release chatter around the corridor center.
        if (std::abs(error) <= hysteresis) {
            // Counter excessive vertical speed while near the center line.
            if (std::abs(vy) < 1.0f) return m_botHeld;
            bool wantUp = vy < 0.0f;
            return wantUp == heldMovesUp;
        }

        bool wantUp = error > 0.0f;
        return wantUp == heldMovesUp;
    }
};

RealtimeSolver g_solver;

} // namespace autogd

class $modify(AutoGDBotPlayLayer, PlayLayer) {
    void setupHasCompleted() {
        PlayLayer::setupHasCompleted();
        autogd::g_solver.rebuild(this);
    }

    void resetLevel() {
        autogd::g_solver.resetInput(m_player1);
        PlayLayer::resetLevel();
    }

    void onQuit() {
        autogd::g_solver.resetInput(m_player1);
        PlayLayer::onQuit();
    }
};

class $modify(AutoGDBotPlayer, PlayerObject) {
    void update(float dt) {
        auto* layer = PlayLayer::get();
        if (layer && this == layer->m_player1) {
            autogd::g_solver.tick(layer, this, dt);
        }
        PlayerObject::update(dt);
    }
};
