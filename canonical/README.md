# Canonical AutoGDBot Realtime source

This directory stores the exact user-supplied `AutoGDBot-Realtime-source.zip` as six base64 chunks because the GitHub connector used during the port could not upload binary files directly.

Canonical ZIP SHA-256:

`ee997badb3b8983d85f5b5b3cba9538404127b29f01e1b3521c474537b2e36d9`

The archive is the upstream Windows source and is treated as the source of truth. Android support is applied as a platform-only adaptation after restoration; solver/planner source is not rewritten for Android.

Restore locally:

```bash
python canonical/restore.py --output restored
```

The script concatenates `source.zip.b64.part00` through `part05`, verifies the SHA-256 above, writes the ZIP, and extracts it. The restored project root is `restored/AutoGDBot-Realtime`.
