# AutoGDBot Realtime 1.0.0-beta.1

This package drives the live classic-mode player. There is no Solve popup,
whole-level export, offline DP, or solution replay phase. Each command begins
at the current native player state and executes its input in that physics step.

## Runtime change

`RealtimeSearch` replaces `OnlinePlanner` in the mod. It evaluates both immediate
commands with one native tick each, keeps up to two branches per first command,
and extends short native prefixes. Flying modes receive finer branching near
obstacles. Cube flights extend to a later control opportunity. Native ring,
ground and support-loss events generate timing repairs. Mixed dual modes retain
a flying player's hold policy while the oracle checks both bodies.

The native oracle now saves terminal prefix states, enabling sibling branches
to reuse actual intermediate states rather than simulate every timeline from
the root. Cached snapshots are confined to one live command. Before issuing a
queue, the selected prefix is independently replayed from the live root without
memoization. Position, mode, velocity and consumed-interaction differences
reject that replay.

A completed landing/horizon route, a checked ground wait, and a responsive-mode
prefix are distinct from a one-command recovery. When only a short prefix is
confirmed, the bot can issue that checked command, displays an unproved landing,
and does not promote the speculative future to a verified queue. Search and
replay share an absolute deadline that includes scene preparation. Native calls
and snapshot copying are not preemptible.

The live command uses GD's normal button path for real events. Its chosen button
state is also enforced at the native physics boundary, where an earlier queued
UI/reset command could otherwise cancel it. Enforcement does not advance the
cube pulse or input queue twice.

## State and collision fixes

- Prefix snapshots carry the advanced level time, not the preceding tick's time.
- All 162 canonical `GJGameState` fields use typed snapshots and deep container
  copies. Android alias unions do not use implicit assignment or raw memcpy.
- Player collision dictionaries retain their contact history in snapshots.
- The private Android libc++ hash fallback uses the pinned library's actual
  Murmur2/CityHash algorithm, rather than an unrelated FNV hash.
- Native collision decides death. Coarse hazard rectangles rank available room;
  they no longer reject legal spike corners as an additional death oracle.
- A queued route's world witness keeps its membership anchored. Actual object
  transforms/activation still invalidate it. Active world motion disables long
  queues and requires new live observation every command.
- Simple gravity, size and speed portals can be forecast independently of a
  mode portal's camera-band restrictions.

## Verified here

All 11 standalone CTest programs passed in Release with assertions enabled.
The new search completed 80 live-command updates in an independently integrated
changing wave corridor, a ballistic pit crossing with landing replay, and three
independent staircase runs with repeated touchdown jumps. Other checks cover
both root actions, cold replay divergence, consumed interaction identity, dual
trajectory coverage, mandatory releases, candidate caps, expired deadlines and
unsupported boundaries. Existing controller, portal, interaction, mode-policy,
accuracy, survival, block and prefix-cache regressions also passed.

These fixtures are not Geometry Dash gameplay. The unmodified baseline's Debug
staircase test exhausted its wall-clock search allowance; the optimized suite
passed before and after the rework. Neither result establishes phone frame time.

The Android package was compiled with Geode 5.10.1
(`7e41336f68660b990644f5c3450b6da6ae944560`), GD 2.2081 bindings
(`f8aec5d1f3df76d840dfd2609f02cef30dc6efdf`), NDK r29, API 23 and arm64-v8a.
ZIP CRC, mod ID/version, ELF64 AArch64 shared-library format, defined
`geodeImplicitEntry`, packaged native-binary identity and absence of an undefined
`__hash_memory` import passed. The MIT gdsolver notice is packaged as a resource.

Package: `AutoGDBot-Realtime-v1.0.0-beta.1-Android64.geode`

Size: 351840 bytes.

SHA-256: `833b6bdb60d175b66cfeaf4224f99ae356bdaf2c67225b411094f915ec82ae75`.

## Actual limits

Android installation, native forecast fidelity on a device, sustained FPS and
level completion have not been tested here. No official or custom level is
claimed cleared. There is no all-level guarantee.

The oracle integrates native player physics and supported local interactions;
it does not run the entire trigger/action manager into the future. Moving
geometry, custom group effects and complex camera/frame changes can diverge
from the local forecast. Active motion therefore uses one-command recovery.
Dual/solo, mirror, teleport and unsupported frame transitions can require a live
re-anchor rather than a complete forecast. Platformer and independent two-player
controls are explicitly unsupported in this build. A finite beam/deadline can
also miss a viable route in ordinary geometry.

The relevant gdsolver reference is `8e8d69ada86a315c71d05cc8b8fe4d369d883aa2`:
native verification, distinct physical states and distrust of incomplete world
snapshots. This runtime is its own bounded online search, not the gdsolver mod
merged into AutoGDBot. Existing reference attribution remains under `third_party`.

## Use

Import the `.geode` on Android64 running GD 2.2081 and Geode 5.10.1, restart the
game, enable **Autopilot**, and open a classic level. There is no separate solve
step. `NO VERIFIED ROUTE` and `landing unproved` are intentional status messages,
not completion claims. Timing CSV can be enabled in settings for device diagnosis.
