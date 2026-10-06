# Changelog

## v0.4.0

- Replaced the old external UDP/Python control path with a native Geode controller.
- Moved bot decisions onto `PlayerObject::update` so input is decided on the physics tick.
- Added explicit held-input state to prevent repeated `pushButton` spam and stuck jumps.
- Added per-level caching for hazards and solids.
- Added mode-specific control policies for cube, ship, ball, UFO, wave, robot, spider, and swing.
- Added Windows CI for Geometry Dash 2.2081 / Geode 5.10.1.
