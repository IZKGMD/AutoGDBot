# AutoGDBot GDS native realtime beta.5

Android64; Geode 5.10.1; GD 2.2081. Built on 12 October 2026.

## Scope of the GDSolver adaptation

The online native search now uses a reachability-cell frontier adapted from
[GDSolver search_dp.hpp](https://github.com/gdsolver/gdsolver/blob/2cddf0e25fca53105e9851d20d44f9e5e82d8314/dp/src/dp/search_dp.hpp).
The open-addressed cell table and high/low vertical-velocity representatives
come from that source under MIT. Attribution and the upstream license are
included in the source and the .geode package.

This is an adaptation of GDSolver's search mechanisms. It does not embed its
complete offline DP model, full-level repair loop, coin routing, or recorded
moving-geometry solver. It does not inherit GDSolver's official-level results.
Native player rollouts from AutoGDBot are used for proposal evaluation and
independent replay; the Windows model's physics constants are not substituted
for Android physics.

## Changes

- The old four-history prune is replaced by up to twelve spatially distinct
  native states. Cells include tick, input, position/velocity bins, both
  players, mode/gravity/size/direction, consumed interactions and sampled
  forces/contact state. Both velocity extremes survive within a cell.
- Up to sixteen input proposals persist between live commands. Their
  coordinates advance with the chosen command. Incompatible first inputs
  are removed. Only input values persist: old scene snapshots, clone state
  and certificates are not reused across roots. Up to four proposals are
  replayed from the new root per search.
- Long repair coordinates rotate between commands. Candidate and rollout
  deadlines remain bounded; no full-level pre-solving screen is introduced.
- Priority takeoffs are checked at neighbouring coordinates before optional
  distant work. Local landing times are ranked by flight clearance rather
  than rewarding a later landing simply because it occurs later. A checked
  immediate launch remains immediate when only later neighbours are safe.
- Native realized timelines retain the local landing-probe flag. Later
  neighbouring takeoffs get enough tail to finish their own flight.
- Checked paths, amber forecasts, jump-coordinate markers and hitboxes remain
  enabled by default. The trace presentation clock preserves its phase at
  120 Hz with small render jitter. Native corners and teleport breaks are
  preserved; smoothing does not invent a curve through a hazard.

Search runs on the game thread; native Cocos calls are not moved to an unsafe
worker. The runtime limits native rollout work to at most 2 ms per command.
Scene preparation, pruning and individual native calls add to the displayed
total. This is a scheduling allowance, not a hard guarantee of 120 render FPS.
Render FPS and physics TPS remain separate.

## Final validation

Production source commit: `623f407e48cd450274999a6b4604e4863214a47a`.
Evidence: `validation/beta5-release.*`, `validation/beta5-sanitize.*`,
`validation/beta5-package.json`, `validation/beta5-source-sha256.json`.

| Check | Result |
| --- | --- |
| Final Release test programs | 13/13; 5.55 seconds |
| Final ASan + UBSan test programs | 13/13; 41.71 seconds |
| Existing distinct stress-matrix cases | 9,240 in each configuration |
| Release closed-loop obstacle scenes | 96/96; zero deaths; zero missing checked commands |
| Sanitized closed-loop obstacle scenes | 95/96; zero deaths; one scene stopped without a checked command |
| Release and sanitized obstacle plans | 864/864 landed and cleared; both adjacent timings safe in all 864 |
| New frontier regression | 20,000 map cells, zero key, growth/clear, velocity extremes, ceiling dead end, dual/gravity/interaction/force separation |
| New online regression | 180 live fixture steps at 120 TPS; proposals resumed; corrupted fresh replay rejected |
| New trace clock regression | 1,200 jittered 120-Hz render opportunities; 240-to-120 limit; pause recovery |
| Static Android64 package | Passed |

The stress program asserts proof safety separately from search-quality counts.
Consequently, 13/13 test-program success does not mean every quality scenario
completed: the sanitized missing-command scene above is reported explicitly.
The final Release obstacle-search fixture measured p50 0.415 ms, p95 0.963 ms,
maximum 1.450 ms, with an 8-ms fixture allowance. These are host timings, not
Android GD timings or evidence of a phone's 120-FPS performance.

An intermediate sanitized run failed the legacy staircase-controller assertion
`every grounded stair state needs a viable route`. That assertion belongs to
the older Controller fixture with a 3.5-ms deadline, not the new native frontier.
The exact cause was not established. The final run passed all staircase tests;
stability under arbitrary CPU scheduling is not claimed. Another intermediate
regression caught a neighbouring-timing choice delaying a checked immediate pit
jump; the final code preserves that immediate command.

LeakSanitizer was disabled because this environment cannot perform its
process/thread inspection. No leak-free claim is made. No ASan/UBSan finding
was observed in the final run.

## Package and remaining limits

Package SHA256:
`ccdb46b9740704222409c622e627cfd340316f32f01ee8bb4eacf936a4f3805a`.
472,617 bytes compressed; native library 1,548,464 bytes. ZIP integrity,
manifest/source identity, ELF64 AArch64, entry export, shared libc++ dependency,
private hash-symbol compatibility, 16-KiB segment alignment and absence of
writable/executable load segments were checked. The packaged native library
matches the newly built library byte for byte.

Toolchain: Geode SDK 5.10.1 (`7e41336f68660b990644f5c3450b6da6ae944560`),
bindings `f8aec5d1f3df76d840dfd2609f02cef30dc6efdf`, CLI 3.9.0, NDK r29,
arm64-v8a, API 23, `c++_shared`.

Geometry Dash and an Android phone were not available for native gameplay
tests. Installation, hook execution, actual hitbox/trace presentation, Stereo
Madness/Deadlocked clears and stable 120 FPS have not been verified. Platformer
and separate two-player controls remain unsupported. Existing moving-world
and unsupported-transition safeguards can limit the checked prefix to one
command. A finite online frontier can miss a feasible route. Instant guaranteed
completion of every level is not established.
