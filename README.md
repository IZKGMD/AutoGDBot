# AutoGDBot Realtime v0.19.0

Geode mod for Geometry Dash 2.2081, Geode SDK 5.10.1 and bindings f050fd74ad531b0827377c97510cb807903ff1b3.

## Install

On Android, import the Android64 .geode in Geometry Dash's Geode local-package picker and restart the game. If the file picker hides .geode, extract the accompanying ZIP first. Windows uses its separate Windows x64 package.

## Changes

v0.19 keeps the native-physics rolling-horizon controller realtime: no whole-level pre-solve and no external process. Inspired by gdsolver's reachability/verification loop, UP and DOWN are now tracked as separate certified reachable sets. The arbiter ranks survival class, immediate clearance and robustness before secondary score terms, so an unnecessary tight jump cannot beat a wider safe wait merely on progress or continuity.

Every controllable cube tick reserves search for both first inputs. Model drift above 1.25 units invalidates the warm plan and verified queue and immediately re-seeds rollouts from the live player state. The executor may not invent an UP->DOWN rising edge: a buffered press without its own current-snapshot reachability witness is vetoed. Verified landings, grounded waits, native-motion queue matching, orb timing, shared per-tick deadlines and multi-mode control from the accuracy branch are retained.

See REACHABILITY_v0.19.0.md for the design, regressions and limits.

## Validation

Six standalone suites exercise controllers, staircases, modes, interaction restoration, orb chains, native-motion queues and accuracy regressions. Native PlayerObject physics and collision remain authoritative; there is no fixed level macro. Future trigger motion beyond the current local scene is not fully simulated, and portals stop prediction for live replanning.

Standalone tests and native compilation do not establish in-game accuracy. Installation, device FPS, Stereo Madness completion and success rates remain unmeasured. Enable Save timing CSV for diagnosis; the HUD shows prediction error, certificates, tested UP/DOWN candidates and search time.
