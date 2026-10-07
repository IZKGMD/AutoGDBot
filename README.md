# AutoGDBot Realtime v0.22.0

Geode mod for Geometry Dash 2.2081, Geode SDK 5.10.1 and bindings f050fd74ad531b0827377c97510cb807903ff1b3.

## Install

Import the Android64 `.geode` using Geode's local-package picker, then restart Geometry Dash. If the picker hides `.geode`, extract the accompanying Android ZIP first. Windows needs the separate Windows x64 package. Remove an older copy with the same mod ID before importing if the picker reports a duplicate. The in-game HUD must show **0.22.0**.

## Route planning

The runtime now uses `OnlinePlanner`: one event-driven frontier, one absolute deadline and one proof policy. The earlier Controller remains a proposal generator and historical test target; its separate search tiers no longer control the game. Geometric and mode proposals retain evaluation slots alongside adaptive native repairs. Safe routes rank by proved progress and clearance; launch count and input changes break safety ties.

A forecast runs the entire planned window, then completes any cube/robot landing obligation and post-portal reaction window within a 512-tick maximum. The first touchdown is not a success endpoint. A native loss of floor support creates branches before the edge. An interrupted or fatal coast can commit only its contiguous grounded prefix, shortened by a takeoff reserve. The falling tail is recorded separately and never drawn as an approved route. See ONLINE_PLANNER_v0.22.0.md for proof boundaries.

Verified input timelines are reused while position, native motion, mode, interaction identity, timestep and nearby world state agree. Expected portal transitions remain on the same lineage. A missed state match discards the queue. Refresh starts before the checked suffix runs out; due clicks execute before another search.

Exact requested-input prefixes share native snapshots within one live command. Cached winners are independently replayed from the command's root; a mismatch or unfinished replay rejects that winner and retains an independently checked fallback where available. This does not merge states by rounded coordinates. HUD/CSV expose cache savings and replay mismatches. Unused D3D11 ranking and unproved-flight recovery settings have been removed.

## Validation and limits

Ten standalone suites cover existing regressions and the new kernel/cache contracts. Independent numerical fixtures exercise a cube crossing a floor gap above a red kill zone and alternating wave controls. Additional contract fixtures cover every control mode, late portals/orbs, grounded waiting and cache disagreement. These are not recordings of Geometry Dash gameplay or a complete emulator of its physics.

Native game routines remain responsible for player integration, ordinary collision, supported single-player portals, rings and pads. The local forecast does not reproduce the complete future world: arbitrary trigger-driven movement/actions are not advanced. Dual/solo, teleport/mirror and unsupported camera/frame changes remain live re-anchor boundaries. Queue invalidation observes changes after they happen; it cannot establish future trigger safety. See GDSOLVER_REVIEW.md.

Fingerdash is the general bot's target benchmark; there is no level-specific macro. Installation, native completion, device FPS and success rate have not been measured for v0.22.0. No claim of 100% completion or zero lag is made. Save timing CSV records `timing-v0.22.0.csv` in the mod save directory. The supplied recording `2026-10-07 21-28-25.mp4` showed **v0.4**, as corrected by the user; it is not evidence of a v0.21 regression.
