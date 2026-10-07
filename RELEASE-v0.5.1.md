# AutoGDBot Realtime v0.5.1

Base: v0.5.0, commit 1a2f3b1954740db8a1e6ef5faadc6eab20306b0e.

## Staircase control
Predictor candidates now describe repeated jumps relative to actual native
collisions reporting the player grounded. The search tries several delays at
each touchdown rather than guessing a platform arrival from horizontal X.
The predictor materializes the resulting exact button timeline. Only this
collision-checked timeline may be selected or cached; the policy never directly
commands the real player.

The ordinary lookahead remains at most 240 ticks. If a cube is still in flight
when it ends, the predictor releases input and continues to a verified landing,
under the same time budget and a hard cap of 512 ticks. It does not treat an
arbitrary mid-flight horizon cut as a failed landing. Tail extension does not
increase candidate progress scores; unnecessary tail time carries a penalty.

New diagnostics distinguish death, failed landing certificates and timeouts,
and expose the longest rejected forecast instead of displaying only zero ticks.
Near hazards the search may try 96 candidates. Uncontrollable cube flight uses
light release ticks between 60 Hz forecasts; grounded/orb control is still
replanned every physics tick.
GPU input stride updated to match the bounded 512-tick storage.

## Validation scope
The new independent staircase fixture contains six rising narrow platforms
above floor spikes. A single fixed jump fails. Touchdown-relative candidates
complete the airborne tail; five closed-loop runs with different starting
positions reach the final stair under a 3.5 ms search budget.
This fixture uses synthetic fixed-tick physics, not the native Geometry Dash
engine, and does not verify Stereo Madness completion.

Existing decision/collision regressions and Android64/Windows builds are checked.
The environment cannot run Geometry Dash. Native hook fidelity, full Stereo
Madness completion and frame time on the user's device remain unverified.
No guarantee of arbitrary level completion is claimed.
