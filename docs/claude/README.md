# Pinux Astra — source packs for Claude / GPTunnel

These are **read-only, human-readable snapshots** of the existing CTRL+LIFE source code from branch `ctrl-life`, prepared for Claude's attachment-based analysis. The actual source of truth is under `core/src/...` and `lwjgl3/src/...`. **Do not edit the snapshots instead of the original Java files.**

Snapshot taken on **2026-11-04** from commit `e3d34ea8c63295ab9c1c657ac90badfb52b91e20`.

| File | Description |
| --- | --- |
| [Pinux_Astra_01_UI.txt](Pinux_Astra_01_UI.txt) | 11 files: high-resolution Pinux UI, desktop, native windows, Astra Code, legacy workbench, and related references |
| [Pinux_Astra_02_Gameplay.txt](Pinux_Astra_02_Gameplay.txt) | 16 files: gameplay, ByteScript, coding/contracts, state, career, data |
| [Pinux_Astra_03_Build_Tests.txt](Pinux_Astra_03_Build_Tests.txt) | 37 files: build configuration, tests, render helpers, fonts metadata, documentation |
| [Pinux_Astra_ALL_SOURCES.txt](Pinux_Astra_ALL_SOURCES.txt) | All 64 text files combined |

Each file retains `FILE: original/path` and `END FILE: original/path` separators. Files are all below **5 MB**.

## For GPTunnel

**Download and attach** `Pinux_Astra_01_UI.txt` directly to Claude first. If necessary, add the other parts when working on gameplay, ByteScript, or tests. The `ALL_SOURCES` version is for contexts that can handle a larger attachment.

**Important:** GPTunnel's Claude instance in this workflow reported that it **cannot browse GitHub links or extract ZIP files**. Posting the GitHub URL alone does not give that instance access to these sources — upload the relevant TXT attachment.

### First message

> Read the attached Pinux Astra source code. Confirm actual class/method names before proposing changes. Audit the current high-resolution Scene2D/VisUI desktop, windows and AstraCode; preserve ByteScript, coding jobs, compilation, progression and saves. Design a distinctive, Archcraft-inspired UI. First give a concise, code-grounded plan; do not generate implementation code until requested.

Generated bundles intentionally exclude binary assets and runtime dependencies. For a complete playable game, use the official repository's Windows release or build the original source project.
