# Current version: v0.21.1

Eight standalone regression suites pass (controller, staircase, mode, interaction, survival, accuracy, portal and block regression). Android64 and Windows x64 Release packages build locally with Clang 21, Geode 5.10.1 and pinned bindings. Android uses NDK r29; Windows uses the MSVC 143 runtime and Windows SDK 10.0.26100 via xwin. Package CRC/manifest and binary identity checks pass; Android is AArch64 with the expected SONAME and locally defined libc++ hash helper. Windows is PE x86-64 with Release CRT imports and geodeImplicitEntry export. Both packages include the gdsolver MIT notice.

The portal suite checks deterministic evaluator contracts, including gap-to-portal branching, transitions in the extended landing tail, a following orb, per-step mode/mini/gravity/speed proofs and aligned suffixes. See GDSOLVER_REVIEW.md for attribution and limits. Native rollout fidelity, installation, mode-band animation/isolation, Stereo Madness completion, FPS/latency and success rates remain unverified. The supplied v0.21.0 video shows unnecessary early jumps followed by death against the first block. The new block suite reproduces three planner failures against the pinned v0.21.0 controller and passes with v0.21.1. It also checks evaluation-count limits and steering beyond the initial portal horizon; ASan/UBSan cover the new fixture. Portal tests include an orb in an extended tail. These fixtures do not replay the native level. See BLOCK_REGRESSION_v0.21.1.md. The following notes concern historical versions.

# Validation status

Version 0.2.1 is an experimental prototype. Build/controller checks are separate from actual game verification. The supplied v0.1.4 video shows death at 14% Stereo Madness. A later screenshot shows v0.2.0 loaded and dead at 22% on a floor spike; no v0.2.1 game run is available.

| Check | Result |
|---|---|
| Configure with Geode SDK 5.10.1 and GD 2.2081 bindings | PASS |
| Compile/link Windows x64 DLL using Clang 20.1.2, Microsoft CRT/SDK | PASS |
| Package with Geode CLI 3.9.0 | PASS |
| Package CRC and required DLL/manifest entries | PASS |
| PE x64 architecture and exported geodeImplicitEntry | PASS |
| Standalone controller test via CMake/CTest | PASS (1/1 test program) |
| Multi-switch test corridor and immediate-obstacle input | PASS |
| All-candidates-dead fallback, GPU cannot bypass verification | PASS |
| Zero-step interrupted rollout cannot authorize input | PASS |
| Repeatable reset and deadline stop | PASS |
| Controller AddressSanitizer / UndefinedBehaviorSanitizer | PASS, leak detection disabled because /proc is unavailable |
| Detached clone travel, native travel not doubled, sideways/reverse travel | PASS |
| Direct press/release, idempotent hold, reapply after command processing/reset | PASS |
| Forecast encounters a forward obstacle with Y-only native integration | PASS |
| Command seconds converted to native frame units; live calibration and reset | PASS |
| Grounded ballistic corridor selects jump with correct native time units | PASS |
| Equal-survival endpoint-clearance tie preserves current first action | PASS |
| Conservative hazard guard: landing, vertical clearance, tunneling and reverse motion | PASS |
| Guard rejects a jump when the synthetic native collision pass misses a hazard | PASS |
| Cube press then release on next tick despite repeated DOWN requests | PASS |
| Mock real input and forecast pulse timelines agree; no repeated flight presses | PASS |
| Continuous-mode and dash-transition holding preserved | PASS |
| Mandatory release constrains baseline/warm/random candidates and dead-end fallback | PASS |
| Grounded hazard reflex: forward/reverse, floor/ceiling and distance gates | PASS |
| Joined/reverse support edge hints, delayed takeoff priority | PASS |
| Landing verification gate rejects an unverified new cube launch | PASS |
| Unique CPU/GPU proposal list and safe-current-action early exit | PASS |
| Partial versus complete horizon and expiry of a held warm-plan prefix | PASS |
| v0.2.1 DLL loading inside Geometry Dash | NOT RUN |
| v0.2.1 D3D11 dispatch/readback on a real GPU | NOT RUN |
| Native rollout versus actual game-state fidelity | NOT RUN |
| Orb/pad/trigger-world isolation inside the game | NOT RUN |
| Deadlocked completion | NOT RUN |
| Bloodbath completion | NOT RUN |
| FPS / CPU / GPU utilization with traces enabled | NOT MEASURED |

The tests use a synthetic deterministic corridor and validate controller behavior, not Geometry Dash physics.
The observed synthetic deadline test took approximately 0.2 ms for a requested 0.2 ms deadline; this is not a game benchmark.
The native engine integration, GPU runtime and UI require a Windows GD test session.

User screenshot for v0.1.1 shows the mod loaded in Stereo Madness, a running controller (step 391, 24 candidates), D3D11 async status and a pause at 1%. This is not a successful gameplay test. Review found that v0.1.1 passed command seconds into frame-unit native physics, underestimating vertical travel by about 60x. v0.1.2 corrects the units and records the real PlayerObject update step. No v0.1.2 video or physics trace was supplied.

Subsequent user report: v0.1.2 jumps into spikes; no video or timing trace was supplied. v0.1.3 corrects the equal-survival action preference, adds per-step native collision-log resets and a conservative frozen-geometry guard for cube rollouts. Its native fidelity and successful level progress remain unverified. Minimum cube horizon does not override the search deadline and is not a landing certificate.

User reports v0.1.3 holds after the first jump and looks jerky. Its equal-survival preference for the current held button could keep a cube hold latched. v0.1.4 applies the same one-tick cube-pulse gate to forecast and live input, enforces release even on a dead-end forecast, and skips heavy search on three of four ticks during normal airborne cube flight without an orb. A subsequent user video shows v0.1.4 running and dying at 14% Stereo Madness; actual input CSV and frame-time measurements remain unavailable. See VIDEO_ANALYSIS.md.

## Minimum in-game evidence for a completion claim

1. Record the exact game/Geode versions, mod SHA256 and CPU/GPU.
2. Start from a reset attempt with no previously solved input plan.
3. Leave the game running normally with trajectories visible; capture the entire attempt.
4. Save timing-v0.2.1.csv for the same attempt, including actual_down, selected_died, landing_verified and position_error.
5. Report actual completion/death and frame-time percentiles. A compiled package alone is insufficient.

Version 0.2.1 retains the 0.2.0 planner and adds a bounded grounded-cube hazard reflex. Version 0.2.0 prioritizes geometry events, removes duplicate plans, validates a new grounded cube jump through forecast landing (or portal boundary), distinguishes fatal/partial traces, expires only the checked warm prefix, and stops search early on a fully checked unchanged action. Standalone tests validate these behaviors on synthetic scenarios; the test ledge is not the real Stereo Madness level. New native physics, landing fidelity, FPS and completion remain unverified.
