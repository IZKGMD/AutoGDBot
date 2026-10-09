# Validation status for v1.0.0-alpha.1

Checked on 2026-10-09 in a Linux host environment. Host checks do not establish Android gameplay correctness.

| Check | Result |
| --- | --- |
| SHA-256 known vectors (empty, `abc`, million `a`) | Pass |
| Cache isolation after level content, level ID, platform or build changes | Pass |
| Empty level identity and non-finite frame budget | Pass |
| Clean pinned-source adaptation anchors | Pass |
| Adapted standalone DP boundary suite, GCC 13 C++23, `-O2 -pthread` | Pass |
| Python syntax and workflow YAML parsing | Pass |
| Package exporter rejects a `.geode` containing a fake native payload | Pass |
| Real Android64 `.geode` compilation | Blocked: Actions job failed before any steps ran |
| Android loading and touch controls | Not run |
| Cold in-game level completion, including Fingerdash | Not run |
| Android FPS and frame-time percentiles | Not measured |

The untouched pinned upstream boundary suite fails one assertion: its Move fixture expects a nested Toggle to spawn. The measured Move-does-not-spawn rule in upstream `triggers.hpp` already rejects that. `prepare.py` updates this stale assertion and separately checks movement containment and delayed event resolution; the model's trigger and physics rules are unchanged.

Initial Android CI run: https://github.com/IZKGMD/AutoGDBot/actions/runs/37945533159 . It ended in three seconds, exposed no steps or artifacts, and its logs endpoint returned 404. Those observations do not establish the underlying GitHub restriction or error. No new `.geode` was produced by that run.

Acceptance still requires the real Android toolchain to compile the full mod, inspection of the resulting native package, and cold verification in GD on the target Android device. This generation makes no 100% completion or zero-lag claim.
