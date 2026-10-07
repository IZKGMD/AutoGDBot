#pragma once

namespace agb {
// A native collision may consume a pad/ring on the shared level object.
// Keep base, enhanced activation and ring contact ownership in one undo entry.
// This is intentionally Geode-independent so the real rollback protocol can
// be tested with both players, failed candidates and interrupted forecasts.
template<class Base, class Enhanced, class Ring>
struct InteractionSnapshot {
    Base *object;
    Enhanced *enhanced;
    Ring *ring;
    bool scene, disabled, disabled2, ringPowered;
    bool activated = false, player1 = false, player2 = false, powered = false;
    bool claimTouch = false;
    int state = 0;
    InteractionSnapshot(Base *o, Enhanced *e, Ring *r)
        : object(o), enhanced(e), ring(r), scene(o->m_isActivated),
          disabled(o->m_isDisabled), disabled2(o->m_isDisabled2),
          ringPowered(o->m_isRingPoweredOn) {
        if (e) {
            activated = e->m_activated;
            player1 = e->m_activatedByPlayer1;
            player2 = e->m_activatedByPlayer2;
            powered = e->m_poweredOn;
            state = e->m_state;
        }
        if (r) claimTouch = r->m_claimTouch;
    }
    void restore() const {
        object->m_isActivated = scene;
        object->m_isDisabled = disabled;
        object->m_isDisabled2 = disabled2;
        object->m_isRingPoweredOn = ringPowered;
        if (enhanced) {
            enhanced->m_activated = activated;
            enhanced->m_activatedByPlayer1 = player1;
            enhanced->m_activatedByPlayer2 = player2;
            enhanced->m_poweredOn = powered;
            enhanced->m_state = state;
        }
        if (ring) ring->m_claimTouch = claimTouch;
    }
};
}
