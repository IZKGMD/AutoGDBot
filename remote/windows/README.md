# Windows bridge: controller protocol

Repository: IZKGMD/AutoGDBot (must remain private). Branch: remote/windows-bridge-v1.

Read `remote/windows/status.json` before issuing commands. Require state `ready`, a recent `updated_at` (within 90 seconds), and use its 32-hex `session` ID. Read `game.stopped` as well. Without a ready status, do not claim access to the PC. One outstanding command at a time. Read the previous result before advancing.

Write `remote/windows/command.json` using its fetched content SHA. Command schema:

```json
{
  "version": 1,
  "session": "COPY_FROM_CURRENT_READY_STATUS",
  "seq": 1,
  "expires_at": "USE_UTC_NOW_PLUS_120_SECONDS",
  "op": "capture",
  "args": {}
}
```

Sequence must exceed status.last_seq and every command previously sent in that session, including rejected or lost commands. Never retry a click/key with another sequence without checking its result and current screenshot: an upload failure can occur after successful input. Results are in `remote/windows/results/<session>/<seq>.json`; `status.last_result` also points to the latest claimed command's result.

Operations: status, launch, close_game (normal close only), focus, capture, collect_logs, install_package, stop. They take `{}`; launch can also take `{ "capture": true }`. Click takes normalized client coordinates x and y (0..1), optional capture boolean and after_ms (0..3000). Key takes key SPACE/ENTER/ESC/R/TAB/LEFT/RIGHT/UP/DOWN, optional hold_ms (10..1500), capture, after_ms. Inspect a current screenshot before choosing click coordinates. Windows can reject foreground activation; do not treat a failure as success.

`install_package` reads only `remote/windows/package.json`, validates its SHA-256, then asks the worker to check ZIP ID, GD target and x64 PE identity. Schema: id `autogdbot.realtime`, version, sha256, base64. Decoded package limit 440000 bytes. Game must be closed. Use close_game, inspect the acknowledged result, install_package, then launch as separate commands. Package currently supplied is the already-built v0.22.0 Windows binary with SHA-256 4102582987a35d4c93419528102c8aeac9c3549e28baaf9da58b51529ac7ec6e. This is a transport setup, not a new bot release.

Fetch report JSON without printing image Base64. Image object can be in report.data (capture) or report.image (optional post-action capture). It has mime image/jpeg, width, height, client_width, client_height, base64. Decode to a local JPEG and use the image tool to inspect it. collect_logs returns report.data.files (name, total_bytes, header, bounded tail).

Worker RPC tests in this branch mock native input. PowerShell parser/C# compilation and file installer tests run on Linux; they do not validate Win32 calls. Do not report native gameplay, Fingerdash completion or a working PC connection until corresponding evidence is received. Stop commands/local F8 revoke further actions for the session. No shell, arbitrary filesystem access, arbitrary downloads or other application control are exposed.
