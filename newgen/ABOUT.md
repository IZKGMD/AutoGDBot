# AutoGDBot Verified — Android64 experimental

A new generation of AutoGDBot based on gdsolver's MIT-licensed reachability solver and native replay/repair loop. gdsolver remains credited: this is an adaptation, not an independently invented engine.

Choose **Solve** when opening a level. The solver searches in advance, tests candidate inputs inside GD, repairs discrepancies against the game's actual state, and plays the successful plan at normal speed. **Replay** uses a previously verified plan for this exact level content and build. The on-screen Android touch controls stop or pause a session.

Compared with the previous AutoGDBot rolling-horizon controller, verification concerns a whole candidate route. Mobile adaptations use two search workers and a default 6 ms verification batch budget. A single native game update can overrun that budget; there is no zero-lag guarantee. Normal playback does not run a new rolling search every tick.

Saved plans and iteration maps are scoped to SHA-256 of the level content, level ID/type, Android64, GD 2.2081 and this engine/build. Editing a local level or upgrading this build invalidates its old replay. Unknown/empty level data cannot authorize a replay. Solve always starts cold; cached plans are only used by an explicitly selected Replay.

Required: GD **2.2081**, Geode **5.10.1+**, **Android64**. Remove/disable the older AutoGDBot or GD Solver before using this package, since their hooks should not run together.

Upstream: https://github.com/gdsolver/gdsolver
Pinned engine: 8e8d69ada86a315c71d05cc8b8fe4d369d883aa2
License: MIT. Copyright notices remain in the source and GDSOLVER-LICENSE.txt.

The upstream Android port is experimental; Windows regression results are not Android measurements. No completion rate, Fingerdash completion, runtime loading, or frame-time figures have been measured for this build. Compilation and standalone tests do not prove a successful game run. Arbitrary custom levels and platformer levels are not guaranteed to solve.

Automated sessions remain marked as bot input and preserve upstream's progress/achievement suppression. Manual play records normally.
