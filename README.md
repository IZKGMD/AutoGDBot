# AutoGDBot

Real-time Geometry Dash level solver implemented as a native Geode mod.

## Goals

- Solve while the level is running — no full pre-solve pass.
- No external Python process or UDP bridge.
- Keep gameplay control inside one `.geode` package.
- Run decision logic on the player's physics tick instead of one rendered frame late.
- Never spam `pushButton` every tick: jump input is edge-triggered and explicitly released.
- Support classic game modes incrementally, with cube / ship / wave as the first priority.

## Target

- Geometry Dash 2.2081
- Geode 5.10.1
- Windows x64

## Current controller

The first native controller uses a per-level cache of collision-relevant objects and a real-time mode-specific policy. It measures the live player's velocity and reads native object hitboxes instead of communicating with another process.

The important input rule is that the bot changes jump state only when the desired held state changes. Cube/UFO/ball/spider inputs are short pulses; ship/wave/swing may hold continuously with hysteresis.

This is intentionally different from the earlier UDP prototype, where an action could be received and applied after the frame update and repeated pushes/releases could fight the game's own held-input state.

## Roadmap

1. Native realtime controller + CI build.
2. Tune cube timing against practice runs and Deadlocked checkpoints.
3. Add engine-backed shadow-player rollouts using Geometry Dash's own collision path for candidate validation.
4. Handle moving objects/triggers and improve portal-aware mode transitions.
5. Add lightweight diagnostics/traces without stalling gameplay.

## Build

Install the Geode SDK/CLI and set `GEODE_SDK`, then run:

```sh
geode build
```

GitHub Actions also builds the Windows `.geode` artifact automatically.

## Status

Experimental. A successful CI build proves the mod compiles against the target SDK; it does **not** by itself prove that a level such as Deadlocked can already be completed end-to-end.
