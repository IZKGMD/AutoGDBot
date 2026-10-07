# Realtime reachability v0.19.0

v0.19 is an online controller. It does not pre-solve the complete level and does not depend on an external solver process during play.

## Why this revision exists

The visible trajectory can be correct while the executed input is wrong if prediction, selection and input buffering use different first-action semantics. A second failure mode is scalar-score inversion: two apparently valid futures are mixed into one score, allowing a tighter or less robust jump to win because of progress, continuity or timing bonuses.

## Reachability arbiter

Each native rollout is assigned to the first input the clone actually realized, not merely the input that the candidate requested. UP and DOWN therefore build separate reachable sets from one frozen live snapshot.

A candidate enters a reachable set only after the existing native collision and landing/certificate gates accept it. The best witness for each first input is selected lexicographically:

1. strong horizon/event certificate before short receding certificate;
2. wider immediate hazard clearance;
3. higher robustness;
4. wider overall and landing clearance;
5. longer certified survival;
6. the legacy score only as a final tie-breaker.

Near an actual orb, a DOWN witness may win a close safety tie so the controller does not discard a required interaction, but it cannot override a materially safer UP route.

## Re-anchor and execution shield

Every tick still seeds native PlayerObject clones from the live game. If the previous prediction differs from the observed player by more than 1.25 game units, or mode/dual state changes, v0.19 clears the warm controllers and verified queue before searching again. This is a bounded realtime adaptation of the verifier/re-anchor idea used by gdsolver.

The live CubePulse layer may still enforce releases. It may not invent a new UP->DOWN rising edge unless DOWN has its own current-snapshot reachability witness. An uncertified buffered press is vetoed.

## Realtime budget

Search remains tiered and bounded by a fraction of the current physics tick. There is no cold full-level DP. Candidate generation, orb/control-event repair, native collision verification and reachability arbitration happen only over the local rolling horizon.

## Retained accuracy fixes

v0.19 retains verified cube landings, safe grounded waiting prefixes, fair deadline reservation for opposite first inputs, native-motion queue matching, verified queued releases/airborne edges, shared tier deadlines, orb chains, pads, portals and multi-mode policies from the accuracy work.

## Validation boundary

Standalone tests and compilation can prove controller invariants and packaging, but they cannot prove a Geometry Dash level clear. Moving/triggered future geometry is not fully predicted, portals end the local forecast for live replanning, and Android/native timing still requires in-game measurement. Stereo Madness completion must be confirmed by a real run.
