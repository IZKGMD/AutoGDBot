#pragma once
#include <cstdint>
#include <initializer_list>

namespace agb {
enum class PlannerMode : uint8_t {
    Cube = 0, Ship = 1, Ball = 2, Ufo = 3, Wave = 4,
    Robot = 5, Spider = 6, Swing = 7, Dash = 8, Generic = 255
};

enum class InputKind : uint8_t { GroundPulse, GroundEdge, AirEdge, Thrust, JumpHold, DashHold };
struct ModePolicy { InputKind input; bool mustLand; };

// Gameplay semantics: https://www.robtopgames.com/files/GDEditor.pdf.
// Physics remains the game's native PlayerObject implementation.
constexpr ModePolicy modePolicy(PlannerMode m) {
    switch (m) {
    case PlannerMode::Cube: return {InputKind::GroundPulse, true};
    case PlannerMode::Ball: case PlannerMode::Spider: return {InputKind::GroundEdge, false};
    case PlannerMode::Ufo: case PlannerMode::Swing: return {InputKind::AirEdge, false};
    case PlannerMode::Robot: return {InputKind::JumpHold, true};
    case PlannerMode::Dash: return {InputKind::DashHold, false};
    default: return {InputKind::Thrust, false};
    }
}
constexpr char const *modeName(PlannerMode m) {
    switch (m) {
    case PlannerMode::Cube: return "CUBE";
    case PlannerMode::Ship: return "SHIP";
    case PlannerMode::Ball: return "BALL";
    case PlannerMode::Ufo: return "UFO";
    case PlannerMode::Wave: return "WAVE";
    case PlannerMode::Robot: return "ROBOT";
    case PlannerMode::Spider: return "SPIDER";
    case PlannerMode::Swing: return "SWING";
    case PlannerMode::Dash: return "DASH";
    default: return "UNKNOWN";
    }
}
struct ModeState {
    PlannerMode mode = PlannerMode::Cube;
    uint8_t flags = 0;
    bool mini = false, upsideDown = false, sideways = false, goingLeft = false;
    bool dash = false;
    int speed = 0; // quantized native velocity; stable against float noise
    bool operator==(ModeState const &) const = default;
    PlannerMode controlMode() const { return dash ? PlannerMode::Dash : mode; }
};
constexpr PlannerMode modeFromFlags(uint8_t flags) {
    // Special modes take priority over a residual general flying flag during
    // a transition. Zero flags is the real cube representation.
    for (PlannerMode m : {PlannerMode::Swing, PlannerMode::Spider, PlannerMode::Robot,
                         PlannerMode::Wave, PlannerMode::Ufo, PlannerMode::Ball, PlannerMode::Ship})
        if (flags & (1u << (int(m) - 1))) return m;
    return PlannerMode::Cube;
}
constexpr bool controlOpportunity(PlannerMode m, bool grounded, bool orb) {
    switch (modePolicy(m).input) {
    case InputKind::GroundPulse: case InputKind::GroundEdge: case InputKind::JumpHold:
        return grounded || orb;
    default: return true;
    }
}
} // namespace agb

