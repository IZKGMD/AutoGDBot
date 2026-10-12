# AutoGDBot realtime beta.6: landing chains and trace presentation

Android64, Geode 5.10.1, GD 2.2081. 12 October 2026.

The supplied `2228.mp4` is a visual reference: short continuous branches from
the current icon, smooth ballistic arcs and small endpoint boxes. It is not a
recording of this mod's failed staircase attempt. Its 59.94-FPS recording rate
does not establish the user's actual game render rate; the user's 120-FPS target
is retained. The original upload was not modified.

## Corrections

1. The support-runway calculation used the leading contact edge. This returned
   zero while the trailing edge still overlapped a narrow platform. Runway now
   ends when the trailing contact edge leaves, in forward, reverse and flipped
   frames. When multiple spans overlap the contact footprint, the surviving
   support span is retained. Native collision remains the authority.
2. Raised-support geometry requests a landing-chain search. Priority work
   tests complete native touchdown policies before polishing an isolated hop.
   A chain ends only at a safe handoff after at least two landings. The active
   policy's final flight is allowed to settle beyond its input window.
3. Warm chain inputs are preserved. Independently replayed chains are not
   clipped at the first landing or before the second requested jump.
4. The middle hint of the first local timing triplet is checked first. This
   prevents a short deadline from spending its only delayed-flight proposal
   on an edge of the suggested timing window. Native replay is still required.
5. Presentation uses a 0.65-second native window by default. It projects onto
   the same current route, hides diverged roots and preserves teleport breaks.
   The amber uncommitted tail starts after the checked prefix; it is not drawn
   over the green line. Checked queued suffixes retain their green colour.
6. Quiet queued-control frames can supply two fresh native visual probes,
   with an 800-microsecond scheduling allowance including state capture.
   Individual native calls cannot be preempted. These probes are display-only:
   they do not authorize input or replace a certificate. Identical probes are
   not drawn twice. Stale probes are hidden by route-root matching.
7. Endpoint boxes, a compact two-line HUD and debug-only tall gap guides replace
   the cluttered default presentation. Trace cadence remains configured for
   120 Hz. The final guide-visibility change is included in the evidence commit.

The GDSolver-derived state frontier remains in use. This adaptation still does
not include GDSolver's entire offline full-level repair and moving-geometry
solver, and does not inherit its published completion results.

## Validation and its limits

Core changes: commits `d1fecfda0d8b6a0695f4e76d200293810c4e1fb8` and
`b1c98cba4446588acc37b83ce09d5d1de40a0988` on
`dev/stairs-trace-v1.0.0-beta.6`. Exact final source hashes and package bytes
are recorded in `validation/beta6-source-sha256.json` and
`validation/beta6-package.json`.

- Release: all 13 test programs passed. The existing 9,240 distinct stress
  cases include 96/96 completed closed-loop scenes, zero deaths and zero
  missing checked commands, plus 864 independently replayed obstacle flights.
- Final sanitized batch: 12 programs passed; the staircase executable did not
  start (`BAD_COMMAND`, execute bit cleared in this workspace). After restoring
  that bit, direct execution of the same ASan/UBSan binary passed all staircase
  cases, including the new strict chain. The batch XML is preserved unchanged;
  the successful retry is recorded separately. All 13 programs therefore ran
  successfully under ASan/UBSan, with this execution retry explicitly recorded.
- The strict contact-runway staircase fixture independently replayed eight
  landings over 840 checked steps. The former search order returned one landing
  in that fixture. These are synthetic geometry/physics values, not a claim
  that Stereo Madness has been completed in GD.
- A deterministic one-flight-proposal test verifies that the chosen route
  retains more than eight fixture units of flight clearance when additional
  proposals are interrupted. Adjacent timing replay checks are retained.
- New trace tests cover current-root anchoring, the short view window,
  rejection of a diverged route, separation of the checked tail, and teleport
  discontinuities. Existing corner/error-bound and 120-Hz cadence tests remain.

Intermediate sanitized runs exposed both the old legacy controller's
3.5-ms staircase deadline sensitivity and a grazing takeoff chosen before
better alternatives were tested. The central-hint scheduling correction adds
a deterministic regression for the latter. Stability under arbitrary host or
phone CPU scheduling is not asserted. LeakSanitizer remained disabled because
this environment cannot perform its required process/thread inspection.

The package passed ZIP integrity, exact manifest identity, ELF64 AArch64,
entry export, shared-libc++ linkage, private hash-symbol compatibility,
16-KiB segment alignment and writable/executable-segment checks. Its native
library matches the final build byte for byte. The toolchain remains Geode
SDK 5.10.1, pinned GD 2.2081 bindings, CLI 3.9.0, NDK r29, API 23 and
`c++_shared`.

Geometry Dash and an Android phone were not available here. Actual installation,
native hook fidelity, visual appearance on a device, Stereo Madness or other
level completions, and sustained 120 FPS remain unverified. Native visual probes
add bounded optional work, so performance needs device measurement. Existing
platformer/two-player and moving-world restrictions remain.
