# Accuracy repair v0.20.0

The user reports early jumps into spikes in Stereo Madness and the bot appearing inactive. No recording or CSV of that exact section was provided in this turn, so these code defects are reproducible regressions rather than confirmed explanations of that particular death.

The previous survival test permitted a fatal DOWN trajectory surviving 249 ticks to outrank a fatal UP trajectory surviving 126 ticks. The controller now rejects every unproved new cube activation in recovery, including interrupted checks. The regression requires UP, no landing certificate and no executable recovery queue.

Rejecting all interrupted cube predictions discarded safe ground travel. A new evaluator counter proves contiguous ground contact before and after each UP physics step for every controlled player. A collision-free prefix of at least the configured reaction interval (normally 0.12 seconds) may certify waiting. Airborne, fatal, missing and inconsistent prefixes cannot qualify. A verified later jump and safe landing still outrank this short wait.

Opposite first input is now second and the first forecast receives at most half the remaining local deadline. Tiers and guard retries share one deadline. Near geometry the runtime cap is 85% of one physics tick, with no overload scaling of that cap; open sections retain smaller caps. A short coast near obstacles cannot terminate the longer cube landing search. This trades some local search time for complete checks; device performance is unmeasured.

All due verified queue transitions now commit, including UFO/Swing airborne edges and UP required to rearm a ring. An existing verified queue outranks unproved recovery. The queue still checks both players, native motion/control flags, position within 0.75 units and timestep within 5%. Failed/interrupted rollouts and recovery cannot seed it. Optional orbs no longer force a release in continuous modes. Recovery is off by default for new installs, while the cube launch restriction also applies to existing enabled settings.

Six standalone suites cover the selection, timing and queue regressions along with prior controller, staircase, mode, interaction and orb-chain cases. These tests do not execute Geometry Dash.

Native physics fidelity remains dependent on clone state, collision order, native timing and current object state. Position error is displayed and stale plans reset. Moving coordinates invalidate queues at map refresh; future trigger animation is not modeled. Portals end local prediction. Installation, FPS, completion and success rates require in-game testing and are not established by compilation or these tests.
