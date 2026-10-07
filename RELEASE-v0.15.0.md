# AutoGDBot Realtime v0.15.0

Fixes targeting video 573.mp4: no live orb activations and missed narrow
jump/orb windows in Fingerdash and Geometrical Dominator. The user additionally
reported that automatic jump pads stopped activating.

## Shared interaction state

Clone physics reused live ring/pad objects. Restoring only GameObject scene
activation/disabled flags was insufficient: EnhancedGameObject tracks separate
activation for each player, power state and RingObject contact ownership.
Those fields could be consumed by a forecast and persist into later candidates
and live gameplay. Each touched object now has one complete undo entry, restored
on every candidate exit, including interruption. RingJump and BumpPlayer journal
the object before its earliest native mutation, including cached touching rings
processed before the next collision pass. Simulation also suppresses the
visual trigger effect. Real pad activation remains automatic native gameplay.

## Exact scheduled input

A verified input queue retains only checked, nonfatal, uninterrupted plan steps.
A scheduled orb/ground click runs before another forecast when both live players
match the prior prediction. Failed fresh searches may use the remaining checked
suffix. Queue validity requires matching modes, gravity/mini/speed/dual state,
time step, and positions (within 0.75 world units). Unexpected actual input,
state changes, divergence, or exhausted proof clears the queue. No unchecked
orb/cube reflex is added.

A freshly injected DOWN updates the two native jump snapshots immediately.
The native release delay and collision contact flags remain intact. Clone steps
also stop manually advancing jump/state variables before native update: GD
2.2081 already advances those at the end of PlayerObject::update.

## Ledge reaction runway

Support contact now uses the full native player collider. The previous inset
foot was above the actual platform surface, so edge detection could fail and
report infinite runway. Overhanging support now yields zero runway; joined
supports and reverse/inverted travel remain covered.

HUD adds `pad-hit=hits/attempts`, queue length and `QUEUED-CLICK`/`QUEUED` stages.

## Validation

Standalone controller, staircase, mode and interaction tests. Interaction tests
exercise 2048 activation-state combinations, 96 speculative pad activations,
previously consumed P1 versus unused P2, early forecast exit, two rapid orb
input windows, state/TPS/position invalidation, and ledge geometry.
These test the production rollback/queue/runway helpers; they do not emulate GD.

Geometry Dash is unavailable in this build environment. Live orb/pad recovery,
Fingerdash/Dash completion, input latency/FPS and 99.9% success remain unmeasured.
Classic shared-input dual is supported. Platformer and independent two-player
controls remain unsupported; forecasts stop at unsupported world-changing
portals/triggers and replan from the game's observed state.
