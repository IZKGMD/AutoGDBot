# AutoGDBot Realtime v0.20.0

Geode mod for Geometry Dash 2.2081, Geode SDK 5.10.1 and bindings f050fd74ad531b0827377c97510cb807903ff1b3.

## Install

On Android, import the Android64 .geode in Geometry Dash's Geode local-package picker and restart the game. If the file picker hides .geode, extract the accompanying ZIP first. Windows uses its separate Windows x64 package.

## Changes

New cube launches and orb activations require verified landings; known-fatal or interrupted trajectories cannot win merely by surviving longer. Safe native-confirmed grounded UP prefixes can certify waiting. Near obstacles, short coast predictions defer to longer landing checks. Both immediate input choices receive reserved search time, and all tiers share one absolute deadline. Queued releases and airborne clicks execute at their verified step, while native motion, position, mode and timestep must still match. Optional rings retain continuous hold candidates. Recovery is disabled by default for new installs.

See ACCURACY_v0.20.0.md for the concrete regressions and limits.

## Validation

Six standalone suites exercise controllers, staircases, modes, interaction restoration, orb chains, native-motion queues and accuracy regressions. Native PlayerObject physics and collision remain authoritative; there is no fixed level macro. Future trigger motion beyond the current local scene is not fully simulated, and portals stop prediction for live replanning.

Standalone tests and native compilation do not establish in-game accuracy. Installation, device FPS, Stereo Madness completion and success rates remain unmeasured. Enable Save timing CSV for diagnosis; the HUD shows prediction error, certificates, tested UP/DOWN candidates and search time.
