# Research and attribution

- Original uploaded `main.cpp`: Windows UDP telemetry/input bridge; no solver was supplied. Preserved in `original/main.cpp` for reference.
- Geode SDK v5.10.1: https://github.com/geode-sdk/geode/tree/v5.10.1
- Geode GD 2.2081 bindings: https://github.com/geode-sdk/bindings/tree/main/bindings/2.2081
- Geode package/build documentation: https://docs.geode-sdk.org/
- GD Solver: https://github.com/gdsolver/gdsolver — studied restoration/fidelity failure modes, the distinction between a model and game verification, and physics-tick input timing. Its offline solver and saved solutions are not bundled or used.
- Eclipse Menu ShowTrajectory: https://github.com/Srivanthv/eclipsemenu/blob/main/src/hacks/Player/ShowTrajectory.cpp — studied the native clone rollout pattern and effect suppression. Eclipse binaries/resources are not bundled.
- Frame Window: https://github.com/leopconfalone-eng/frame-window-mod — studied documented collision-log allocation and frozen-world limitations; its code is not bundled.
- GDP GJBaseGameLayer update reconstruction: https://github.com/camila314/gdp/blob/2.2/GJBaseGameLayer/GJBaseGameLayer_update.cpp — examined the difference between command seconds and 60 Hz frame units for native player physics. Cross-checked with Eclipse's capture of the actual PlayerObject::update argument and GD Solver's 2.2081 timing notes. No reconstructed game code is bundled.
- User reference: https://www.youtube.com/watch?v=Jq9iYzG-GHU and supplied screenshot. Only the screenshot was visually inspected; the full video/algorithm was unavailable.

The controller, D3D11 ranker and integration code were written for this task. `player_scalars.inc` lists only scalar declaration names from the pinned Geode bindings. No commercial game executable, game data, trained model or prerecorded level solution is included.
