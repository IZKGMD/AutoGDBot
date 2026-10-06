#!/usr/bin/env python3
import argparse
import base64
import hashlib
from pathlib import Path
import shutil
import zipfile

EXPECTED_SHA256 = "ee997badb3b8983d85f5b5b3cba9538404127b29f01e1b3521c474537b2e36d9"
PART_COUNT = 6


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="restored")
    args = parser.parse_args()

    here = Path(__file__).resolve().parent
    output = Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)

    encoded = "".join(
        (here / f"source.zip.b64.part{i:02d}").read_text(encoding="utf-8").strip()
        for i in range(PART_COUNT)
    )
    raw = base64.b64decode(encoded, validate=True)
    digest = hashlib.sha256(raw).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(f"canonical source SHA-256 mismatch: {digest}")

    zip_path = output / "AutoGDBot-Realtime-source.zip"
    zip_path.write_bytes(raw)

    project_dir = output / "AutoGDBot-Realtime"
    if project_dir.exists():
        shutil.rmtree(project_dir)
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(output)

    if not project_dir.is_dir():
        raise SystemExit("restored project directory is missing")

    print(project_dir)
    print(f"sha256={digest}")


if __name__ == "__main__":
    main()
