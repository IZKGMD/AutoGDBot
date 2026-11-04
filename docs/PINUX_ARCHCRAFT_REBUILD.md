# Pinux Astra: Archcraft-quality UI rebuild (decision record)

Status: **NOT APPROVED / PROTOTYPE ONLY**. The existence of passing Java or smoke tests does not mean the interface is visually acceptable. This document is a quality contract for the \`ctrl-life\` branch, not a claim that the design is finished.

## Evidence: failures of the previous UI

Review the real screenshots saved by \`.github/workflows/pinux-desktop-preview.yml\` rather than concept art.

1. The 480x270 \`PixelCanvas\` workbench becomes a **three-times enlarged** older-game UI inside a crisp desktop. The hybrid must end. All Pinux applications, including jobs, ByteScript and Astra Code, need to render through one resolution-independent UI layer.
2. A library such as VisUI solves pointer handling, drag and resize, **not** visual design. Unmodified colours, procedural symbols and generic labels do not resemble Archcraft.
3. The generated city-and-sunset wallpaper dominates the composition and reads as a cartoon. Professional Archcraft "rices" use restrained, carefully composed background art.
4. Earlier desktop shortcuts were 120x113, the topbar was 52 high, the dock 415x80; Archcraft Openbox's Polybar can be as compact as 26 high. Those proportions were wrong.
5. Tiny bitmap glyphs scaled at runtime look soft; the terminal had no distinctive monospace typeface. Rendering in a native-resolution \`Stage\` is necessary but not sufficient.

## Technical decision

**Keep Java 21 and libGDX for the native game. Keep Scene2D's Stage and input handling for the OS. Replace the entire Pinux presentation, including the legacy workbench, with one coherent native-resolution design system.**

Do not migrate to Java Swing, JavaFX, Compose Desktop or a separate browser window. All of those create an additional window/runtime or require fragile off-screen compositing. Dear ImGui Java supports docking and makes sense for an editor, but its programmer-tool defaults still need a full design system and make the OS shell harder to style. A blind framework swap would repeat the same failure.

A future Dear ImGui proof-of-concept is acceptable only **inside the existing LWJGL3 window**, and only if it beats Scene2D in verified code-editing behavior and visual quality. It is not an excuse to ship default ImGui styling.

## Archcraft reference, precise design sources

- https://wiki.archcraft.io/docs/window-managers/stacking-wm/openbox/
- https://github.com/archcraft-os/archcraft-openbox/tree/main/files/themes
- https://github.com/archcraft-os/archcraft-bspwm/tree/main/files/themes/onedark
- \`files/themes/onedark/polybar/colors.ini\` and \`rofi/shared/colors.rasi\` define OneDark's actual palette.
- \`files/themes/*/polybar/config.ini\` gives panel density and hierarchy.
- \`files/themes/*/rofi/launcher.rasi\` gives an actionable launcher composition.

Archcraft's upstream package repos include GPLv3 content. **Use screenshots and style measurements as reference, do not copy GPL implementation/assets into the existing MIT game.** Keep notices for every imported asset with a compatible licence.

## Design tokens (first pass, subject to visual verification)

| Token | Value |
| --- | --- |
| OS background | #1E2128 |
| Raised surface | #292D37 |
| Chrome/window title | #292D37 |
| Primary text | #DBE0E8 |
| Muted text | #8E9BAD |
| Blue selection | #5294E2 |
| Success | #98C379 |
| Error | #E06B74 |
| Divider | #3D4350 |
| Top bar | 28–30 px @ 1440x810 |
| Desktop shortcut area | icon + label, no full-time coloured card |
| Bottom dock | 40–50 px high total, NOT 80+ |
| Window gap | 12–16 px |
| Title bar | 32–38 px, flat and simple |
| Body text | 13–15 px (system sans) |
| Console text | 13–15 px (real monospaced font) |
| UI font | Inter (OFL-1.1, with complete Cyrillic support) |
| Terminal/editor font | JetBrains Mono (OFL-1.1) |
| UI icons | Lucide (ISC; comply with the accompanying notices) |

Use libGDX FreeType to generate properly hinted font bitmaps for each pixel size; avoid stretching one texture atlas for multiple UI sizes. If using SDF fonts, test small-size legibility. Convert selected SVG icons to antialiased PNG atlases **at build time**; bundle them with the game, never require network access at runtime.

## Required architecture

\`\`\`
CtrlLife (libGDX native window)
├─ PixelCanvas — world, movement, sprites and scenery only
└─ PinuxStage — all PC UI at window pixel resolution
   ├─ Wallpaper + theme
   ├─ Polybar (workspaces, active window, system/game indicators)
   ├─ Rofi-style launcher (real search/filter/keyboard selection)
   ├─ WindowManager (drag, resize, max/min/restore, focus, snap)
   ├─ AstraCodeView — Explorer, tabs, editor, tests, diagnostics
   ├─ TerminalView — terminal model, history, input, scroll
   ├─ FilesView — game virtual file system
   ├─ MailView — real existing career/job offers
   └─ SettingsView — working theme and accessibility controls

ComputerViewModel — adapted from existing Career/Coding/GameState
VirtualFS and TerminalModel — **game sandbox only**
\`\`\`

No UI markup or model for the PC should directly call host shell/filesystem operations. Preserve ByteScript and gameplay logic. The biggest migration is to replace \`workbench.draw()\` on the low-res canvas with a native-resolution AstraCodeView, while preserving the existing game-state controller.

## Milestones, one at a time

1. **Design system + shell:** consistent fonts, icons, wallpaper, 30 px Polybar, 40 px dock, keyboard launcher. Remove oversized desktop cards. Screenshot desktop and launcher.
2. **Window chrome:** consistent titlebar, correct focus/drag/resize/max/min, scale at 720p/810p/1080p; verify title text and content do not clip.
3. **Terminal:** actual monospace font, selection and cursor, history, copy/paste within game sandbox, scrolling, run \`help\` and \`ls\`.
4. **Astra Code:** remove old 480x270 workbench UI. Reimplement functional project panel/editor/tests in Scene2D with underlying ByteScript logic unchanged.
5. **Files, TaskMail, Settings:** each uses shared components and real game models, not placeholder content. Full navigation/input tests.
6. **Packaging:** build complete Windows x64 ZIP including Temurin 21 runtime and licensed assets; clean machine launch without installed Java; link directly to playable build.

### Mandatory acceptance gates

For EVERY stage:
- Run real \`:core:test\` and \`:lwjgl3:guiSmoke\` on Xvfb.
- Save and **inspect** actual full-resolution PNG screenshots: desktop, focused window, other window, resizing, launcher. Never call an image-generation concept a screenshot.
- Check 1280x720, 1440x810, and 1920x1080 for clipping, legibility and collisions. Include visible hover, active, disabled and focus cases.
- Test mouse input, keyboard focus, ESC, minimize/restore, app open/close, resize, and Alt+Tab (or documented equivalent).
- Address visible defects and rerun before considering the stage complete.
- No release until the full Windows ZIP has been built, tested and verified to contain \`runtime/bin/java.exe\`, \`run.bat\` and all game jars.

Passing automated tests means "functional smoke passed", **never** "design approved". Visual approval requires reviewing screenshots.

## Current checkpoint

The first shell-density experiment in commit \`45040a0b\` lowered the topbar, dock and desktop icon sizes and darkened the wallpaper. CI run \`41820168198\` succeeded and produced actual screenshots. It proves the underlying Scene2D architecture can support realistic desktop dimensions **without rewriting the game engine**, but this is not a final UI: the wallpaper/art, terminal typography and full native Astra Code migration are still unresolved.
