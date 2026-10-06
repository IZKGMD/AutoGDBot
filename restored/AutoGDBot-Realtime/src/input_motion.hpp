#pragma once
#include "controller.hpp"

namespace agb {
// Normal cube jumps use a one-tick pulse. The next tick releases even when
// the optimizer repeats DOWN. Continuous modes and active dash keep holding.
class CubePulse {
    bool releasePending_ = false;

  public:
    void reset() {
        releasePending_ = false;
    }
    bool mustRelease() const {
        return releasePending_;
    }
    bool command(bool requested, bool pulseMode, bool canPress) {
        if (!pulseMode) {
            releasePending_ = false;
            return requested;
        }
        if (releasePending_) {
            releasePending_ = false;
            return false;
        }
        if (!requested || !canPress)
            return false;
        releasePending_ = true;
        return true;
    }
};

// processCommands receives seconds; native PlayerObject physics uses 60 Hz
// frame units (0.25 for a 240 TPS tick). Calibrate from the real update hook
// instead of feeding command seconds straight to PlayerObject::update.
class PhysicsTiming {
    float commandSeconds_ = 0;
    float unitsPerSecond_ = 60;
    bool measured_ = false;

  public:
    void reset() {
        commandSeconds_ = 0;
        unitsPerSecond_ = 60;
        measured_ = false;
    }
    void beginCommand(float seconds) {
        commandSeconds_ = std::isfinite(seconds) && seconds > 0 ? seconds : 0;
    }
    void observeNative(float units) {
        if (!commandSeconds_ || !std::isfinite(units) || units <= 0)
            return;
        float ratio = units / commandSeconds_;
        if (ratio < 1 || ratio > 240)
            return;
        unitsPerSecond_ = ratio;
        measured_ = true;
    }
    float nativeStep(float seconds) const {
        return seconds * unitsPerSecond_;
    }
    bool measured() const {
        return measured_;
    }
};

// A detached player may integrate only the control axis. Preserve native
// movement when present; complete just the missing travel-axis displacement.
inline Point completeTravelStep(Point before, Point after, Point expected, bool sideways) {
    if (sideways) {
        if (after.y == before.y && std::isfinite(expected.y))
            after.y += expected.y;
    } else if (after.x == before.x && std::isfinite(expected.x)) {
        after.x += expected.x;
    }
    return after;
}

// Read actual state each time; requested state can be stale after reset or
// command processing. Return whether a native transition was sent.
template <class ReadHeld, class Press, class Release>
bool applyJumpState(bool desired, ReadHeld readHeld, Press press, Release release) {
    if (readHeld() == desired)
        return false;
    if (desired)
        press();
    else
        release();
    return true;
}
} // namespace agb
