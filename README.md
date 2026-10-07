# AutoGDBot Realtime v0.21.0

Geode mod for Geometry Dash 2.2081, Geode SDK 5.10.1 and bindings f050fd74ad531b0827377c97510cb807903ff1b3.

## Install

On Android, import the Android64 .geode in Geometry Dash's Geode local-package picker and restart the game. If the file picker hides .geode, extract the accompanying ZIP first. Windows uses its separate Windows x64 package.

## Changes

The local search now continues through normal-frame single-player mode, gravity, size and speed portals. Native collision chooses actual contact and the game's PlayerObject changes physics; a portal is not a success endpoint. The continuation must survive at least 0.18 seconds after its last state transition, and cube/robot flights still need their safe landing. Failed continuations branch at the mode actually reached, preserving the observed input prefix and reevaluating each complete lineage. Portals discovered in a landing tail can extend the bounded horizon.

Verified input queues carry per-step position, motion, mode parameters and consumed-interaction identity. They accept expected portals and invalidate unexpected states. Due clicks/releases execute before another search; lightweight flight ticks reuse only a state-matched suffix. All tiers share one absolute deadline. The normal display shows only the chosen certified prefix, with an input-plan identifier; diagnostic red UP/blue DOWN alternatives require Show candidate traces. Spider/teleport discontinuities are not connected across walls.

See GDSOLVER_REVIEW.md for attribution, concrete changes and limits. The accuracy protections described in ACCURACY_v0.20.0.md remain active.

## Validation

Seven standalone suites exercise controllers, staircases, modes, interaction restoration, orb chains, native-motion queues, accuracy regressions and portal continuation. Tests use deterministic evaluator fixtures and do not establish native GD physics fidelity. Dual/solo, teleport/mirror and unsupported camera-frame portal transitions remain live re-anchor boundaries. Future trigger-driven world motion is not fully simulated. There is no fixed level macro or downloaded saved solution.

Standalone tests and native compilation do not establish in-game accuracy. Installation, device FPS, Stereo Madness completion and success rates remain unmeasured. Enable Save timing CSV for diagnosis; the HUD shows prediction error, certificates, tested UP/DOWN candidates and search time.
