#pragma once

namespace agb {
// Every airborne segment, including delayed takeoffs, must end in a landing.
// Three grounded samples reject a one-frame contact immediately followed by
// falling off a platform. Portals are handled explicitly by the evaluator.
class FlightCertificate {
    bool pending_ = false, airborne_ = false;
    int groundedTicks_ = 0;
  public:
    bool required = false;
    explicit FlightCertificate(bool grounded = true) {
        pending_ = airborne_ = required = !grounded;
    }
    void observe(bool wasGrounded, bool grounded, bool pressed) {
        if (pressed && wasGrounded) {
            pending_ = required = true;
            airborne_ = false;
            groundedTicks_ = 0;
        }
        if (!grounded) {
            pending_ = airborne_ = required = true;
            groundedTicks_ = 0;
        } else if (pending_ && airborne_ && ++groundedTicks_ >= 3) {
            pending_ = false;
        }
    }
    bool verified() const { return !pending_; }
};
// Learn the native order from real calls in the previous command interval.
// The source state is captured at the same command boundary as live input.
class PhysicsOrder {
    bool seenCollision_ = false, seenUpdate_ = false;
  public:
    bool collisionFirst = true, measured = false;
    void begin() { seenCollision_ = seenUpdate_ = false; }
    void collision() {
        if (!seenCollision_ && !seenUpdate_) { collisionFirst = true; measured = true; }
        seenCollision_ = true;
    }
    void update() {
        if (!seenCollision_ && !seenUpdate_) { collisionFirst = false; measured = true; }
        seenUpdate_ = true;
    }
};
} // namespace agb
