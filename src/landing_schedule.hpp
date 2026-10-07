#pragma once

namespace agb {
// This policy exists only inside the predictor. Live input always comes from
// its realized, collision-checked button timeline.
class LandingSchedule {
    bool sawAir_ = false;
    int groundedTicks_ = 0;
    int delay_ = -1;
  public:
    LandingSchedule(int delay, bool initiallyGrounded) : sawAir_(!initiallyGrounded), delay_(delay) {}
    bool request(bool scheduled, bool grounded, bool mustRelease, bool enabled) {
        if (!grounded) {
            sawAir_ = true;
            groundedTicks_ = 0;
        }
        if (delay_ >= 0 && enabled && sawAir_ && grounded) {
            const bool due = groundedTicks_++ >= delay_;
            return due && !mustRelease;
        }
        return scheduled;
    }
    void sent(bool pressed, bool grounded) {
        if (pressed && grounded) {
            sawAir_ = false;
            groundedTicks_ = 0;
        }
    }
};
} // namespace agb
