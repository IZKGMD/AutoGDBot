# AutoGDBot Realtime v0.5.0

Based on v0.4.0 commit ad906c11024077f99d96bdeb85b567d145f2a560.

## Changes
- Removed unverified hazard-reflex input overrides.
- Known-fatal candidates are rejected before scoring. Cube input requires a full,
  uninterrupted forecast or an explicit portal boundary.
- Every cube flight, including delayed takeoffs and subsequent jumps, needs a
  landing certificate. Three consecutive grounded observations confirm contact.
- Revalidate the warm route first. Short probes no longer authorize input or
  consume the budget before the full rollout.
- Expand rejected search prefixes at native grounded states to find subsequent
  jumps. Rejected prefixes never become live input.
- Test all geometry event centers before dense timing refinements.
- Continuous slab intersection replaces union-box collision rejection. Hazard
  hitboxes are no longer silently inset.
- Observe live collision/update ordering and mirror it in clone rollouts.
- Correct the grounded near-hazard budget gate (previously always false).
- Render the current selected trajectory rather than a stale cached route.
- Build checked-in sources directly; remove build-time historical patch replay.

## Scope and validation
Standalone regressions cover decision safety, chained jumps, landing gates,
continuous collision geometry, timing, input pulses and search deadlines.
Synthetic test scenes do not reproduce the complete Geometry Dash engine.
Android64 and Windows builds target GD 2.2081 / Geode 5.10.1.
No in-game run was performed in the build environment. Stereo Madness 100%,
other level completions, Android frame time and native hook behavior require
runtime verification. This version does not claim guaranteed completion.

The planner remains online, with no whole-level solve before playback. Moving
objects and triggers are still evaluated against a frozen-world snapshot;
portal crossings end the forecast and the next tick replans from real state.
The HUD reports NO VERIFIED ROUTE when the search cannot certify an action;
this prevents an invented jump but cannot prevent an unavoidable collision.
