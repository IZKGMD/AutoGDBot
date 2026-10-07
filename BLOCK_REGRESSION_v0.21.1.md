# v0.21.1: early cube jumps and missing block continuation

## Historical recording (v0.4, corrected by the user)

Correction: the user identified this recording as **v0.4**. Earlier attribution to v0.21.0 was wrong. The observations below cannot establish a regression in v0.21.0 or v0.21.1. Source fixture results are separate evidence.

The recording `2026-10-07 21-28-25.mp4` lasts 18.75 seconds at 60 FPS. In its second gameplay segment, the cube makes two early jumps on open floor, lands before the first low block, then dies against that block's side at approximately 14.5 seconds. This failure precedes any portal. Rapid menu changes make the displayed level names unsuitable for identifying this segment conclusively.

| Approximate video time | Visible HUD / outcome |
|---|---|
| 13.52 s | LIGHT, certificate 82 ticks; early flight on open floor |
| 13.85 s | FAST, certificate 103 ticks; landing OK, runway 47 |
| 14.02–14.27 s | Certificate shrinks from 62 to 6 ticks |
| 14.35 s | NO VERIFIED ROUTE, RESCUE; five tested native deaths, one timeout |
| 14.50 s | Death against the first block |

The visible next-step error is 0.00 and the input mismatch counter is zero. Neither proves that a landing has a workable route past the next block. There is no same-attempt CSV or native replay; the recording alone cannot establish the complete causal chain or a device frame-time benchmark.

## Reproduced planner defects and changes

1. A successful landing earned 60,000 score points even on an empty floor. This let an unnecessary future jump outrank a safe coast. Landing now remains a safety obligation without a bonus. Within cube planning, a native input-caused ground launch has a cost; the existing survival/certificate gates continue to reject dangerous routes and favor a required safe jump over a fatal coast. Orb contacts are excluded from the native launch count. Fixture evaluators without mode traces/counts fall back to counting realized rising edges.
2. Adaptive repairs required both initial button polarities before being prioritized, even when airborne cube release was mandatory. Because every legal first action was UP, discovered touchdown continuations stayed behind the original proposal list. Repairs now follow one mandatory initial evaluation, or both first-action evaluations when either action is allowed. Opposite-action fairness remains intact.
3. Unused proposals occupied the same bounded slots needed to generate compound jumps from later native-confirmed touchdowns. Storage is now a separate pool capped at twice the configured candidate count. Actual native evaluations still stop at the configured count and shared absolute deadline. Repair counts are visible in the HUD and timing CSV.
4. Late post-portal control opportunities could exceed the configured initial horizon. The former repair code truncated the child length and used a reversed `std::fill` range. Steering ranges are now bounded by the actual extended child length and the 512-tick maximum. Transition, orb and grounded branches preserve extended lengths; late orb contacts are no longer discarded merely for occurring beyond the initial horizon.

The first three defects were independently reproduced against `8eec8da4bb7e37b81f87cb1088ec5f4ff05a14a6`, the exact v0.21.0 controller. The fixtures describe planner contracts and are not a replay of the recorded level. They support targeted fixes but do not prove that these were the only causes of the observed death.

## Validation

- Eight standalone CTest suites pass: controller, staircase, mode, interaction, survival, accuracy, portal and block regression.
- The new block suite verifies an unobstructed floor, a two-jump continuation after a native-reported touchdown, an airborne release with only two useful evaluations, the native-evaluation count cap and an extended ship correction after a late portal.
- Portal regression coverage includes an orb at tick 126 after a portal at tick 90 with an initial horizon of 72 ticks.
- AddressSanitizer and UndefinedBehaviorSanitizer cover the new block and portal fixtures; leak detection is disabled in this environment.
- Android64 and Windows x64 Release packages are built with the existing pinned GD 2.2081 / Geode 5.10.1 toolchain. Package metadata, CRC, included binary identity, architecture, loader entry and gdsolver MIT notice are checked.

Native installation and a new complete game attempt have not been run. No near-100% success rate or real-time device performance is claimed. The existing frozen-future-world and unsupported portal-frame limitations remain documented in GDSOLVER_REVIEW.md.

