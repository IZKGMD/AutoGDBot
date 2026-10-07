# AutoGDBot Realtime v0.14.0

## Gameplay modes

The native adapter identifies all eight classic vehicles: cube, ship, ball,
UFO, wave, robot, spider and swing. Dash is a separate input policy while a
dash orb is active. Native PlayerObject physics remains authoritative.

- Vehicle flag priority handles residual ship flags during specialized-mode transitions.
- Orb, mode, gravity, speed, mini, sideways, reverse and dual state changes invalidate warm plans. Both dual players are tracked.
- Ship/wave recurrent steering candidates are available in small fast-search budgets.
- Failed native trajectories can branch at valid control opportunities, preserving the observed prefix and future steering. Edge modes receive a fresh release/press; robot receives varied hold lengths; continuous modes receive varied corrections.
- Robot and cube jumps require a verified native landing and reaction runway. Shared-input mixed dual evaluates the same input on both players and requires both relevant landings. Both players contribute hazard margin and prediction error.
- Dash release can finish the underlying cube/robot flight beyond the short horizon; repairs use the observed vehicle after release.
- Mixed dual interleaves candidates from both vehicle policies. HUD names the actual vehicle(s) and active dash.
- Runway geometry uses local travel/gravity axes for inverted/sideways support and searches in both travel directions.

## Validation and limits

The standalone controller, staircase and mode tests cover nine independent
non-periodic action-window routes, all 128 vehicle-flag combinations, state
changes, mixed dual, unsafe robot flight and coordinate transforms. These are
regression tests, not an emulator of Geometry Dash or a measured game success rate.

No Geometry Dash runtime is installed in the build environment. Fingerdash and
Dash completions, portal timing, phone FPS and 99.9% success are not verified.
Forecast stops at unsupported world-changing triggers/portals and replans from
the real game's next state. Classic shared-input dual is supported; platformer
and independent two-player controls remain unsupported. Future controls past a
forecast boundary cannot be certified before that transition is observed.
