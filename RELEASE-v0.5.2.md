# AutoGDBot 0.5.2 — input phase correction

The bot now injects its chosen button state before the current native
`GJBaseGameLayer::processCommands` call. In 0.5.1 it applied that state after
the call, which could postpone a takeoff until the next physics step. Search,
input, and collision now operate on the same substep boundary.

The overlay records how many native player updates occurred during and after
that command (`native U=total/inside`), the button seen by the native update
(`saw`), and mismatches against the command (`mismatch`). After a restart,
`last attempt` preserves the previous run's action and search failure counts.
Optional CSV telemetry is saved as `timing-v0.5.2.csv` and flushed on restart.

The standalone controller and staircase fixtures pass. These fixtures cannot
prove completion of Stereo Madness in the actual Geometry Dash runtime. The
native phase and in-game outcome must be checked with this build.
