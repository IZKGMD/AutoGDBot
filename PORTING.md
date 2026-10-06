# Windows / Android port

`src/main.cpp`, `controller.hpp`, hazard guard, planning hints, input motion and player scalar state are the canonical AutoGDBot Realtime v0.2.1 solver core.

Windows keeps the original D3D11 asynchronous candidate ranker in `src/gpu_ranker.cpp`.

Android64 uses `src/gpu_ranker_stub.cpp`, which disables only D3D11 candidate ordering. The native Geometry Dash rollout, landing verification, hazard guard, warm-plan logic and CPU safety checks are shared unchanged.

The exact user-supplied source ZIP is retained under `canonical/` and verified by SHA-256 in CI.
