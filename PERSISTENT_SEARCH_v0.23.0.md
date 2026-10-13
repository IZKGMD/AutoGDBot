# Persistent search proof policy

Proposal search, independent replay, and visual previews have separate snapshot pools. Reanchoring is per entry; geometry membership shifts alone do not erase an approved control queue. Actual geometry changes, mode/timestep disagreement and native phase changes invalidate affected snapshots.

A GroundWait contains only observed grounded input and excludes the known fatal or falling tail with a takeoff reserve. A LandedPrefix ends a completed flight at a native stable landing with reaction runway, even if the distant window timed out. A full route covers its entire requested window plus the landing/portal obligation. A responsive control prefix remains a bounded prefix, not a whole-level proof.

Independent verification may itself be sliced. Only its already proved prefix is compared and published; its interrupted/fatal tail is not part of that prefix. A disagreement retains a previously independently checked fallback.

Host validation covers contiguous launch windows (4096 hole patterns), 48 split-prefix lengths, input lineage filtering, geometry reset, phase measurements and independent numerical stair scenes. Sanitized staircase validation uses a deterministic test clock so instrumentation checks route semantics and memory independently of wall-time scheduling; the production build and Release staircase tests use the real steady clock. Earlier sanitizer runs with a real clock reached the configured deadline and failed the staircase route assertion; this is recorded rather than presented as a memory defect or an engine benchmark.

No assertion of a retail GD level completion, phone/PC FPS, maximum realtime latency or universal solvability is supported by these host tests.
