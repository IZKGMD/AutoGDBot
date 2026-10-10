# AutoGDBot Realtime 1.0.0-beta.2

This build addresses short lookahead, late takeoff timing and discontinuous
trace updates reported for beta.1. It controls the live classic-mode player;
there is no level-wide solve or waiting screen.

## Search and takeoff coordinates

The search starts with both one-step actions and independently checks a
non-launching fallback before spending time on future work. It then tests the
retained route and long native rollouts. Failed paths produce repairs at actual
ground/ring/control opportunities, with lead times up to 0.4 seconds before
failure. Geometry-derived takeoff coordinates are proposals and must survive
native simulation. Native touchdown policies provide additional multi-jump
proposals. The short-prefix beam remains a fallback when long rollouts fail.

The target is 768 physics steps (3.2 seconds at 240 TPS), with a bounded 1024-step
tail to complete a flight. Reaching this target depends on scene complexity and
available time; it is not a promise of 768 checked steps on every command.
The selected long future is preserved across commands rather than replaced
with the much shorter control certificate.

Completed futures outrank partial waits. Flights are ranked by their minimum
hazard clearance, grouped in four-pixel bands to avoid extra button changes for
tiny margin gains. Clearance uses the full native player rectangle as a comfort
measure; native collision remains the death oracle. The chosen cube takeoff is
tested at neighbouring input times, and the middle of a contiguous tested
window is preferred when it retains comparable clearance. The trace and HUD
mark the corresponding native position before the press. Native on-ground and
secondary contact flags are treated separately.

## Verification and work limits

The long forecast is not an input certificate. Only a separately replayed
segment is queued: up to 0.28 seconds in responsive modes, or through a landing
for a new cube flight. Replay stops before another ground takeoff where possible.
Position, velocity, modes, consumed interactions, native gravity/forces, rotation
and contact/input flags are checked. Live queue matching rejects position drift
above 0.075 pixels. An unchecked one-step launch cannot bypass landing replay.

Runtime work is scheduled under an absolute per-command deadline capped at
2 ms and 28-48% of the physics-step duration. Scene preparation shares that
deadline. Native calls, allocations and snapshot restoration cannot be
interrupted, so this cap is a scheduling target, not a device frame-time bound.
Checked queued commands continue when a search cannot improve the route.

The live world snapshot is captured once per search rather than copied twice
per candidate. A cached candidate restores its intermediate player directly,
without first restoring the root. Native checkpoints are saved every 48 steps
and at the requested endpoint. The world witness covers the expanded route.

## Trace

The trace refreshes on rendered frames, up to 240 Hz by default. Native samples
are simplified only when their chord error is at most 0.18 pixels; turns,
teleport breaks, two-point routes and the final segment are retained. Different
routes are never blended into an invented curve. Amber shows the uncommitted
forecast; the brighter line shows independently replayed controls. Dual players
are both drawn. Geometry boxes and the expanded HUD are diagnostic options;
the compact HUD updates separately at 8 Hz.

## Checks

All 11 standalone CTest programs passed in Release with assertions enabled.
New independent fixtures check a 768-step open-route forecast using only a few
oracle calls, a short replay certificate and preserved queue suffix, a takeoff
coordinate well before an obstacle with flight clearance and neighbouring
timing survival, and preservation of a checked release when future work expires.
Trace tests cover corners, teleports, short paths and the endpoint. Native force,
rotation and contact mismatches reject reuse. The changing wave corridor, pit
landing, repeated staircase jumps and existing regressions also pass.

These numerical fixtures are not Geometry Dash gameplay. Android64 compilation,
ZIP CRC, ELF64 AArch64 identity, the exported Geode entry, packaged binary
identity and absence of an undefined private libc++ hash import are checked.
Geode 5.10.1, GD 2.2081 bindings and NDK r29 remain pinned as in beta.1.

## Device limits and use

This environment has no Geometry Dash runtime. Installation, actual native
forecast fidelity, sustained phone FPS and real level completion have not been
tested here. There is no all-level or no-stutter guarantee.

The oracle does not integrate the complete trigger/action manager or every
camera/animation effect into the future. Moving worlds use live observation
and one-command recovery. Complex dual/solo, mirror, teleport and frame changes
can require re-anchoring. Platformer and independent two-player controls remain
unsupported. Finite search can miss a viable classic route.

Install the new .geode as an update to autogdbot.realtime on Android64 with
GD 2.2081 and Geode 5.10.1. Enable Autopilot and enter a classic level. The new
lookahead-seconds and trace-fps settings have defaults independent of old trace
settings. Timing CSV includes actual forecast depth and tested jump window.

Package: `AutoGDBot-Realtime-v1.0.0-beta.2-Android64.geode`

Size: 360992 bytes.

SHA-256: `111dca5e95e9bd14b1a6f407a5aa916ad3688abcbfddff0122c410ab68cb5d4f`.
