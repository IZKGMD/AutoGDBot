# AutoGDBot Realtime v0.17.0

The v0.15 Android recording `594.mp4` shows native orb and pad activation, but repeated searches with no approved landing route. One forecast survived 249 steps with press and 126 with release, yet the controller released. The previous v0.16 action-starvation fixes are included in this version.

## Changes

- A first safe landing no longer truncates a route that still schedules later orb/jump actions. Orb contact windows retain ring identity and generate full chain candidates, with a 384-tick local horizon near cube orb chains.
- When complete landing verification fails, the optional Survival recovery setting compares both first actions and chooses the longest collision-checked prefix. This action is marked RECOVERY, never receives a landing certificate and never seeds the verified input queue. It is recalculated on the next live tick. This is a best-effort recovery, not a guarantee against death.
- Cached hazard bounds replace repeated native object-rectangle queries for candidate ranking. Native collision checks still run on every simulated step.
- Queued clicks require matching native velocity, ground and input eligibility states in addition to position and mode. Moving object positions invalidate cached plans.
- Android/iOS clone snapshots retain the platform-specific between-step collision flag.
- All eight classic modes retain their mode-specific native-verified planners; dash hold/release and shared dual input remain supported.

## Validation

Five standalone suites cover controllers, staircases, mode policies, interactions and survival regressions. New cases cover three-action orb chains, late orbs beyond the old horizon, labelled recovery and its rejection conditions, dual-player proof, robot recovery, native-motion queue invalidation and 400 cached-distance comparisons against exhaustive geometry.

Windows is built against Geode 5.10.1 / GD 2.2081. Android requires a separate Android64 build. No Geometry Dash session is available in this environment: Fingerdash/Dash completion, a 99.9% success rate, zero deaths and a twofold speed increase have not been established. Android CI failures before build execution must be resolved before an Android package can be delivered.
