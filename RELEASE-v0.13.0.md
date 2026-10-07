# AutoGDBot Realtime v0.13.0

Video 563 shows mapped orbs being missed and short FAST forecasts accepting
coast before the next required action. This update fixes input and continuation
coverage; it is not a claim of verified Fingerdash or Dash completion.

- Orb eligibility uses native contact and per-player ring usage, rather than
  treating scene `m_isActivated` as an already-used orb. The same rule applies
  to pre-contact input buffering; map discovery and the overlay retain active orbs.
- Native simulations temporarily install clone player pointers and preserve
  the real player's unique ID. The layer's players and game state are restored
  on every exit, including deadlines. Jump state is updated each physics step.
- Orb event expansion works across gameplay modes, preserves future steering,
  releases before a new edge, and branches through chains of native contacts.
- Dash contacts try hold/release durations; an active dash has its own input
  policy. Swing uses discrete gravity flip sequences rather than thrust/PWM.
- A mapped upcoming orb prevents a short FAST cube coast from suppressing
  the longer local continuation search.
- Horizon endpoints obey the landing runway rule too: a touchdown immediately
  before a spike cannot bypass the reaction check just because lookahead ended.
- Spider/teleport discontinuities test destination hazards without sweeping
  the player through every obstacle between endpoints.
- Custom orb world effects end the clone forecast at the interaction; the
  real game executes the effect and the next live step replans.
- HUD `orb-hit=successful/attempted` reports live native ring calls with an
  observed physical/state change, separating detection from activation.
- Fix source artifact filename mismatch in the build workflow.

Validation: controller and staircase regressions, Android64/Windows build CI.
Added regressions cover ship orb edge plus continued thrust, dash hold/release,
and paired swing flips. These tests use deterministic controller environments;
native orb timing, moving triggers, gameplay rotations and full-level completion
still require a Geometry Dash 2.2081 game run. Platformer/two-player independent
controls remain unsupported. No full-level offline solving or recorded macros.
