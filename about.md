# AutoGDBot Realtime v0.23.0 — Windows

This build starts from the exact v0.22.0 source commit c58621942c736fcd3219f7295589570f348c1994, rather than the later beta branches. The supplied original Windows package SHA-256 is 4102582987a35d4c93419528102c8aeac9c3549e28baaf9da58b51529ac7ec6e.

## Install

Windows x64, Geometry Dash 2.2081, Geode 5.10.1. Replace the previous mod with ID `autogdbot.realtime`, restart GD and check that the HUD says 0.23.0. This package does not contain an Android binary.

## Changed behavior

- The search retains unfinished input candidates across physics steps. Retained candidates are proposals until verified; actual input filters incompatible lineages.
- Proposal snapshots and independent verification snapshots live in separate pools. A long verification can continue over successive commands. Each carried snapshot must match the next live position within 0.075 units, native motion/interaction sample, input state, mode, timestep and geometry epoch. It is discarded on disagreement. Verification never borrows a proposal snapshot.
- Native calls stay on the game thread, with deadlines checked between calls. No worker touches Cocos objects. Individual game calls and snapshot capture can exceed a deadline; device frame times are not yet measured.
- The default horizon is 480 physics steps (two seconds at 240 TPS), adjustable up to 768, with a landing tail up to 1024. Those are limits, not a guarantee that every frame reaches the full horizon. Quiet steps work on future continuations while a checked queue runs. Refresh lead is 0.75 seconds; already checked critical transitions execute first.
- Jump timing compares neighboring checked launches. Only contiguous verified samples form an input window; failed or untested timings cannot be bridged. Timing margin is used within comparable clearance ranges, while a safe empty-floor coast remains preferred over unnecessary jumping.
- Platform support distance uses the trailing edge of the collider. Landing and future native collisions still decide whether a proposed path is usable.
- A deadline can close a completed native flight at a stable landing with reaction runway. An unfinished flight or a known fatal launched continuation remains unapproved. Short grounded waits retain the takeoff reserve while deep search starts.
- Travel phase observes the live native call boundaries before changing external travel order. Four agreeing measurements are required; a changed phase invalidates saved control/forecast state.
- The selected green trace draws native samples each render frame, including sharp wave corners and teleport breaks. Short red/blue alternatives are recalculated from the current post-update state in an isolated visual pool (700 microsecond target including capture). They never authorize control. Detailed HUD is optional. CSV telemetry is enabled by default.

## Verification boundaries

The tests use independent numerical scenes, not the retail game's executable. Neither Bloodbath nor an official level has been replayed with this build here. The old Windows bridge status is dated 2026-10-08 and is not an active game connection on 2026-10-13.

The forecast still does not advance arbitrary future trigger-driven world actions. Platformer, two-player controls and complex unsupported frame transitions are not supported. Moving geometry invalidation observes real changes; it does not establish future trigger safety. This release is a testable improvement, not proof of universal completion or a promise of zero lag at 120 FPS.

References reviewed: gdsolver/gdsolver@2cddf0e25fca53105e9851d20d44f9e5e82d8314 (native verification, snapshot failure boundaries); EclipseMenu/EclipseMenu@af89a68b5f7dc7df3b9936ea27b676b04626b537 (native trajectory rendering). No full GDSolver search engine was embedded. Its MIT notice is preserved from the base project.
