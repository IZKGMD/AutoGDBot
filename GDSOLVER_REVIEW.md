# AutoGDBot v0.21.0: portal continuation

Reference: gdsolver/gdsolver revision 8e8d69ada86a315c71d05cc8b8fe4d369d883aa2, inspected on 2026-10-07. Reviewed README.md, docs/ARCHITECTURE.md, dp/src/dp/bands.hpp, frames.hpp, step.hpp and native layer hooks. License: MIT, Copyright (c) 2026 The gdsolver Authors; complete notice in third_party/gdsolver-LICENSE.

## Useful principles

gdsolver's reachability search retains exact continuous states while quantizing only deduplication keys, verifies candidates against GD and repairs the first discrepancy using a new anchor. Reaching a portal's horizontal coordinate does not establish portal contact. The post-contact state includes mode, gravity, size, speed, corridor band and interaction history. Player-only snapshots are insufficient when a candidate mutates the world. Its whole-level search/verification loop is an offline workflow, not an instantaneous per-frame control API.

This revision adapts those principles to AutoGDBot's bounded local native rollout. It does not embed gdsolver's entire DP engine or claim its measured completion results.

## Changes

- Standard single-player portals reach native clone collision. PlayerObject handles mode/gravity/size/speed; actual contact determines the transition. Each lineage records its resulting mode at every state sample.
- Normal-frame ship/ball/UFO/wave/spider/swing ground bands use gdsolver's measured 30-grid formula with the height returned by GD. Cube/robot portals preserve the preceding band's height. Shared live ground animations are suppressed during forecast. Game state, player pointers, journaled object flags and affected camera-offset fields are restored afterward.
- A transition requires at least 0.18 seconds of checked continuation after the latest contact/state change. A cube or robot exit still needs its landing/runway certificate. Neither portal contact nor a legacy boundary flag waives that proof.
- A rejected route branches into hold/release or edge actions at the mode actually reached. Prefix inputs are copied from native realization; a necessary pre-edge release is reevaluated from the root. A portal reached in the landing tail may extend the plan up to the existing 512-tick bound.
- Future horizontal travel is rescaled after native speed/mode changes; live motion from the preceding mode cannot override the first new-speed tick's velocity.
- Verified input matching compares the expected mode/parameters at the current cursor, not the root mode for every future tick. Consumed ring/pad identity supplements position, velocity and input-state checks. Unexpected transition, motion, timestep or consumption changes invalidate the suffix.
- Positions, motions, modes, controls, orb contacts and discontinuity indices share the same suffix advancement. LIGHT uses this queue only after a real-state match. Default drawing contains the selected certified prefix; optional UP/DOWN diagnostics are separate. Teleport/spider snaps do not draw a wall-crossing segment.
- Portal geometry remains a proposal hint. The previous mandatory virtual portal wall is no longer inserted into the runtime hazard guard.
- CPU tiers retain their common deadline and first-action fairness. Cached due clicks/releases execute before new search; native physics is not run on a background thread.

## Checks and practical limits

portal_tests checks gap-to-portal branching across seven target modes, landing-tail transitions, a following orb's release/repress, insufficient post-contact proof, expected mini/gravity/speed changes, unexpected state and consumed-orb divergence, and aligned trace suffixes. The six preceding suites remain in the test set. These are controller contract fixtures, not replay comparisons against GD.

Full world-state fidelity remains unverified. Free mode, rotated/zoomed or otherwise unsupported camera frames, dual/solo, teleport/mirror and custom-trigger handoffs do not receive a fabricated continuation. Future autonomous triggers/moving geometry, portal band animations/static-camera details and native side-effect isolation need validation in the game. No installation run, native replay comparison, FPS/latency measurement, Stereo Madness completion or success-rate measurement is available here. Near-100% completion is therefore not claimed.

For a real run, Save timing CSV writes timing-v0.21.0.csv. Its route_id, mode_transitions, post_portal_ticks, portal_verified and queue_ticks columns identify the selected input lineage and continuation proof alongside actual input, prediction error and search time. An identifier hashes an input sequence; it is not a world-state fingerprint or a completion guarantee.

