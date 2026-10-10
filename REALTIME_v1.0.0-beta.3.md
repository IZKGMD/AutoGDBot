# AutoGDBot Realtime 1.0.0-beta.3

This hotfix addresses the reported lack of jumping and invisible overlays in
beta.2. It is an Android64 Geode 5.10.1 / GD 2.2081 build. The user runs at
120 rendered frames per second; the new trace default is a 120 Hz ceiling.
Rendering and native physics steps remain separate.

## Next action before distant work

Beta.2 could spend its work allowance on scene preparation and a distant coast
before a usable takeoff had been checked. Near a cube action, beta.3 first
probes a short coast, the retained next launch, and geometry/native-failure
takeoff coordinates. Local launch probes run native physics from the current
root with memoization disabled and stop only after the requested launch and a
verified landing with a safe native runway. Flights are ranked by their minimum hazard margin before their duration;
equally roomy candidates prefer the earlier input coordinate. Their first command must match the
separately simulated one-step root, including positions, modes, velocities,
forces and contact/interaction state.

The resulting complete local control proof is retained before optional long
rollouts. An interrupted long forecast cannot replace it with an unproved
one-step launch or an empty release decision. The longer forecast is attached
only when it agrees with the entire checked local segment. Later actions still
require their own future proof. The 768-step forecast target and finite native
repair search remain; this is not a precomputed whole-level replay.

Scene preparation and the root world snapshot now precede the bounded rollout
allowance, rather than leaving zero time for native oracle calls. Rollout work
is scheduled within at most 2 ms and a fraction of the physics-step interval.
Preparation, restoration and indivisible native calls add to the measured
total shown in the HUD; this is not a 2 ms total-frame or phone FPS guarantee.
The initial semantic rebuild is no longer repeated on the first command, and
one-step roots no longer allocate expensive cached world/player checkpoints.
Native cube press eligibility accepts either of the two contact flags; actual
flight landing still requires the native grounded state and runway checks.

When neither a control proof nor a checked recovery exists, the bot releases
its own previous input and allows normal manual input. It no longer claims
input ownership while returning no useful action. Mode changes, current-world
motion and strict live-state mismatch rules from beta.2 remain in force.

## Visible overlays and startup state

The new `show-hitboxes` and `show-trace` settings are enabled by default and
independent of `debug-traces`. Hazards, solid blocks, orbs and player rectangles
are drawn with visible outlines. Exact native path simplification, teleport
breaks and two-point traces remain. Failed native proposals can be shown in
red, independently of whether they authorize input; only checked control
segments receive the checked-path rendering.

The overlay also renders from the base layer's frame update, with throttling,
as well as PlayLayer postUpdate. Drawing no longer requires successful creation
of the text label. The initial label and compact HUD explicitly identify RT3;
the HUD shows command steps, native update/collision calls, candidate count,
forecast depth, checked length and search/total time. A missing physics hook
can therefore be distinguished from a planner that runs but rejects paths.

## Validation and limits

All 11 standalone CTest programs passed in Release with assertions enabled.
New fixtures check preservation of a complete immediate jump/landing after
optional long work expires, rejection of a divergent fresh-root replay and
retention of failed native-forecast diagnostics. An additional timed fixture
checks that the retained local route chooses a roomy takeoff rather than a late
input whose longer timeline merely ends later. The three realtime staircase
runs now exercise priority control and geometry-seeded takeoffs with repeated
touchdown decisions. Existing wave, pit, mode, portal, cache, interaction,
accuracy, survival and trace regressions pass. These fixtures use independent
test physics; they are not Geometry Dash playthroughs.

The Android64 mod compiles using pinned Geode 5.10.1, GD 2.2081 bindings and
NDK r29. The package is checked for valid ZIP CRC, matching manifest/binary,
ELF64 AArch64 identity, its exported Geode entry and absence of an undefined
private libc++ hash import.

No Geometry Dash runtime or user's phone is available here. Actual installation,
native forecast fidelity, real level completion and 120 FPS behavior are not
verified. Complete trigger/action-manager simulation, platformer and separate
two-player control are still unsupported; finite work can miss a viable route.

Replace the previous installed AutoGDBot package with this build, keep one copy
of the mod, and enable Autopilot. The on-level HUD should identify `RT3`.
Telemetry uses `timing-v1.0.0-realtime.3.csv` when enabled.

Package: `AutoGDBot-RT3.geode`

Size: 368406 bytes.

SHA-256: `58bf8b266afba496b643fa01aa57f409794df96f36d860777cad9429836569e2`.
