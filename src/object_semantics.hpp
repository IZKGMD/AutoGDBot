#pragma once
#include <Geode/Geode.hpp>

namespace agb {
using namespace geode::prelude;

inline bool isPadType(GameObjectType t) {
    switch (t) {
    case GameObjectType::YellowJumpPad:
    case GameObjectType::PinkJumpPad:
    case GameObjectType::GravityPad:
    case GameObjectType::RedJumpPad:
    case GameObjectType::SpiderPad:
        return true;
    default:
        return false;
    }
}

inline bool isOrbType(GameObjectType t) {
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

inline bool isPortalType(GameObjectType t) {
    switch (t) {
    case GameObjectType::InverseGravityPortal:
    case GameObjectType::NormalGravityPortal:
    case GameObjectType::ShipPortal:
    case GameObjectType::CubePortal:
    case GameObjectType::BallPortal:
    case GameObjectType::RegularSizePortal:
    case GameObjectType::MiniSizePortal:
    case GameObjectType::UfoPortal:
    case GameObjectType::DualPortal:
    case GameObjectType::SoloPortal:
    case GameObjectType::WavePortal:
    case GameObjectType::RobotPortal:
    case GameObjectType::TeleportPortal:
    case GameObjectType::SpiderPortal:
    case GameObjectType::SwingPortal:
    case GameObjectType::GravityTogglePortal:
        return true;
    default:
        return false;
    }
}

inline bool isSpeedPortalObject(GameObject const *o) {
    return o && (o->m_objectID == 200 || o->m_objectID == 201 ||
                 o->m_objectID == 202 || o->m_objectID == 203 ||
                 o->m_objectID == 1334);
}

// Forecast boundaries are real portals only. Rings are actions, not hard
// forecast boundaries, even when a ring teleports or has custom behavior.
inline bool isForecastBoundary(GameObject const *o) {
    return o && (isPortalType(o->m_objectType) || isSpeedPortalObject(o));
}

// Objects allowed through clone collision filtering. Every semantic orb must
// reach native collision code; otherwise the planner can see a ring that the
// clone can never activate.
inline bool isClonePhysicsType(GameObjectType t) {
    switch (t) {
    case GameObjectType::Solid:
    case GameObjectType::Hazard:
    case GameObjectType::AnimatedHazard:
    case GameObjectType::Slope:
        return true;
    default:
        return isPadType(t) || isOrbType(t);
    }
}

} // namespace agb
