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

// Main-level fallback IDs. Old official levels occasionally surface objects
// through a generic/legacy type path on some builds, while their object IDs are
// stable. Keep this fallback intentionally small and limited to interaction
// objects whose IDs are well established.
inline bool isLegacyOrbId(int id) {
    switch (id) {
    case 36:   // yellow orb
    case 84:   // blue/gravity orb
    case 141:  // pink orb
    case 1022: // green orb
    case 1330: // drop/black orb
    case 1333: // red/black ring family
    case 1704: // dash orb
    case 1751: // gravity dash orb
        return true;
    default:
        return false;
    }
}
inline bool isLegacyPadId(int id) {
    switch (id) {
    case 35:   // yellow pad
    case 67:   // blue/gravity pad
    case 140:  // pink pad
    case 1332: // red pad
        return true;
    default:
        return false;
    }
}
inline bool isOrbObject(GameObject const *o) {
    return o && (isOrbType(o->m_objectType) || isLegacyOrbId(o->m_objectID));
}
inline bool isPadObject(GameObject const *o) {
    return o && (isPadType(o->m_objectType) || isLegacyPadId(o->m_objectID));
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
inline bool isClonePhysicsObject(GameObject const *o) {
    if (!o)
        return false;
    switch (o->m_objectType) {
    case GameObjectType::Solid:
    case GameObjectType::Hazard:
    case GameObjectType::AnimatedHazard:
    case GameObjectType::Slope:
        return true;
    default:
        return isPadObject(o) || isOrbObject(o);
    }
}

} // namespace agb
