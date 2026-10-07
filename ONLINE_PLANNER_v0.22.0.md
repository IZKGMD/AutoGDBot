# v0.22.0: shared native search and bounded route proofs

## Problem and behavior

A visible red hitbox is not a route proof. A coast can remain grounded until the edge, then fall into that hitbox. The old evaluator could stop at an earlier landing, and separate searches repeated the same prefixes. This change makes the entire requested window part of verification, retains native support-loss events, and constructs takeoff branches before an observed edge. It applies to general local planning rather than a Fingerdash macro.

## Proofs

| Proof | Authorized input | Limit |
|---|---|---|
| Horizon / landed route | Whole checked native route | No death, interruption or unsupported boundary; landing obligations and post-portal survival must be checked |
| Ground wait | Release while continuously supported | Ends before support loss or observed death, with reaction reserve; does not authorize the falling tail |
| Control prefix | Short checked segment of a responsive mode | At least 0.10 seconds, capped around 0.18 seconds; no new unproved cube/robot launch |
| None | No certified action | The HUD reports NO VERIFIED ROUTE; release is a fail-closed input, not a survival guarantee |

The display, queued buttons, motion samples and mode samples are trimmed to one proof interval. `forecast_died` preserves an uncommitted terminal death separately from `selected_died`. Due verified edges are executed before search. Queue position tolerance is 0.25 px; native vertical-velocity tolerance is 0.02. Those bounds are rejection thresholds, not measured prediction accuracy.

## Search and replay

`OnlinePlanner` shares one bounded proposal pool and one absolute deadline. Both legal immediate actions precede adaptive repairs. Every fourth later evaluation reserves a seed opportunity so one repair family cannot starve a different mode rhythm. Native transitions and ring contacts branch at the actually observed mode/tick, including events beyond the initial horizon. A child retains its observed prefix and is re-evaluated as a complete lineage.

`PrefixCache` holds at most 32 exact requested prefixes and is reset for each live command. Snapshots include player scalar/list/link values, native game state, pulse and landing schedules, portal continuation, flight obligations, journaled object flags and camera offsets. Prefix identity includes the requested buttons and landing policy. Storage reconstructs entries rather than requiring move assignment of Android's ABI-compatible game containers.

The runtime reserves the last third of the search deadline for independent replay of a cached winner. Replay disables the cache and compares positions, velocities, modes, interaction identity and realized buttons. A mismatch or expired replay cannot authorize that winner. An earlier independently simulated proof can remain as fallback. This replay guards the implemented snapshot contract; it does not prove that the local forecast reproduces every game subsystem.

## Performance boundaries

A checked queue avoids searching every physics tick while live state agrees. Nearby world changes invalidate it. Search shares an adaptive fraction of the current physics-step budget. That deadline begins after scene preparation, and individual native calls and snapshot copies are not preemptible. Actual total time is measured separately. No device FPS result is inferred from tests or compilation. Unused GPU dispatch has been removed from runtime and the Windows binary.

CSV appends `proof_kind`, `forecast_died`, `forecast_interrupted`, `support_loss_tick`, `memo_hits`, `memo_skipped_ticks`, `cold_ticks`, and `cache_mismatches`. Proof integers are 0=None, 1=GroundWait, 2=ControlPrefix, 3=LandedRoute, 4=Horizon. Cache counters displayed as `memo(last)` describe the latest search, not fresh work on a queue-only tick.

## Evidence

The online kernel suite checks full-window termination, empty floor, pre-edge branching, a separately integrated ballistic pit crossing, a separately integrated wave corridor, falling endpoint rejection, bounded waiting, all nine control-policy contracts, an orb after a late ship portal, cache divergence and candidate/deadline bounds. The prefix suite checks exact lineage/policy isolation, bounded replacement, new-world reset and bit-exact resumed numerical integration. Existing eight suites remain regression targets.

The numerical pit/wave fixtures are deliberately independent of the proposal logic. Other evaluator fixtures test contracts and do not establish actual GD mode/orb/pad fidelity. Android64 and Windows x64 compilation and package checks are separate from gameplay validation. Fingerdash completion, success rate, native installation and device frame times remain unmeasured.

## Reference and remaining scope

Reference inspected: gdsolver/gdsolver at 8e8d69ada86a315c71d05cc8b8fe4d369d883aa2, especially docs/ARCHITECTURE.md and src/solver/secsolve.hpp. The relevant principles are keeping distinct physical states, replaying proposals with a native oracle, and distrust of player-only cached snapshots. This implementation is an online bounded frontier; it is not gdsolver's complete dynamic program or world simulator. Existing attribution and MIT notice remain in third_party/gdsolver-LICENSE.

Ordinary native rings/pads and supported single-player mode/gravity/size/speed portals use the existing game hooks. Future trigger-driven world motion and arbitrary action state are incomplete. Dual/solo, mirror/teleport and unsupported frame transitions stop the local forecast for a live re-anchor. These limitations matter to the Fingerdash goal and prevent a near-100% claim.

The historical recording was v0.4, not v0.21.0. Source-only defects reproduced in earlier controller fixtures remain valid as source findings; attribution of the recorded death to v0.21 was withdrawn.
