# Validation status for v1.0.0-alpha.1

Checked on 2026-10-10 in a Linux host environment. Host checks do not establish Android gameplay correctness.

| Check | Result |
| --- | --- |
| SHA-256 known vectors (empty, `abc`, million `a`) | Pass |
| Cache isolation after level content, level ID, platform or build changes | Pass |
| Empty level identity and non-finite frame budget | Pass |
| Clean pinned-source adaptation anchors | Pass |
| Adapted standalone DP boundary suite, GCC 13 C++23, `-O2 -pthread` | Pass |
| Python syntax and workflow YAML parsing | Pass |
| Package exporter rejects a `.geode` containing a fake native payload | Pass |
| Real Android64 `.geode` compilation | Pass: local Android NDK 29.0.14206865, Geode SDK 5.10.1, pinned bindings |
| ARM64 ELF, exported Geode entrypoint, ZIP CRC and private libc++ hash dependency | Pass |
| GD native snapshot assignment symbol resolves against Android SDK | Pass |
| Android loading and touch controls | Not run |
| Cold in-game level completion, including Fingerdash | Not run |
| Android FPS and frame-time percentiles | Not measured |

The untouched pinned upstream boundary suite fails one assertion: its Move fixture expects a nested Toggle to spawn. The measured Move-does-not-spawn rule in upstream `triggers.hpp` already rejects that. `prepare.py` updates this stale assertion and separately checks movement containment and delayed event resolution; the model's trigger and physics rules are unchanged.

Initial Android CI run: https://github.com/IZKGMD/AutoGDBot/actions/runs/37945533159 . It ended in three seconds, exposed no steps or artifacts, and its logs endpoint returned 404. Those observations do not establish the underlying GitHub restriction or error. No new `.geode` was produced by that run.

The Android build exposed a deleted implicit `GJGameState` assignment: the pinned bindings generate a renamed `CCPoint` union. Android snapshots now use the game's exported `_ZN11GJGameStateaSERKS_` copy-assignment, confirmed in the pinned SDK's `libcocos2dcpp.so`, instead of assigning the binding class or copying container bytes. The full mod then compiled and linked.

Produced package: `AutoGDBot-Verified-v1.0.0-alpha.1-Android64.geode`, 2,051,791 bytes, SHA-256 `f15b018d4c7e2503352e7f550ebde68b71997c0316da939116583e1fbffadcc3`. Adaptation source: `dd16c7993753d15bad157a8e048582ea052148aa`. The embedded provenance records the upstream, bindings and NDK versions.

Acceptance still requires loading, touch-control testing and cold verification in GD on the target Android device. This generation makes no 100% completion or zero-lag claim.

