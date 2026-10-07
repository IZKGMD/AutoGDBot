# AutoGDBot Realtime v0.16.0

Video 578.mp4 confirms that v0.15.0 still misses basic ground takeoffs:
Base After Base, Cant Let Go, Jumper, Time Machine and Cycles fail early.
The video also shows one live orb and one live pad activation on Cycles.
This does not establish reliability or completion of Fingerdash/Dash.

## Search ordering

Adaptive orb, control and ground repairs were inserted immediately after a
failed cached coast. On a phone, the remaining budget could be spent entirely
on those repairs before evaluating PRESS NOW. Repairs now stay behind the
initial action basis until both first actions and the first four basis routes
have been tested. Near ground obstacles, a coast cannot end the search before
both first actions are compared. Native evaluation still decides validity.

## Keep checked results

When a slower rescue/emergency search fails, the controller now keeps a safe
checked fast result before falling back to the previous input queue. A fatal
rescue prefix also cannot replace a nonfatal fast result. An unchecked jump
never becomes a fallback. Portal certificates now end at the actual simulated
step count, instead of reporting the full configured horizon.

Queue fallback retains the latest search failure counts and alternatives.
HUD tested=UP/DOWN counts show whether both first actions were actually tried;
FAST-KEPT identifies a checked fast result retained after later search failure.

## Validation

A new deterministic work-limit regression fails on v0.15.0: with only two
route evaluations available, all three repair families starve PRESS NOW.
It passes with the new ordering. Additional tests cover the seven-tick portal
certificate, safe fast fallback selection, invalid/fatal fallback rejection,
and queue failure diagnostics. All prior tests remain part of validation.

No Geometry Dash runtime is available here. These are source-level and build
checks; the new version's live behavior, Fingerdash/Dash completion, frame rate,
input latency and 99.9% success remain unmeasured. Classic shared-input dual is
supported; platformer and independent two-player controls remain unsupported.
