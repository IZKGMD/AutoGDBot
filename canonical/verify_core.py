#!/usr/bin/env python3
import argparse
import hashlib
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("project")
    args = parser.parse_args()

    root = Path(args.project).resolve()
    manifest = Path(__file__).resolve().parent / "core-sha256.txt"
    failed = False

    for raw in manifest.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        expected, rel = line.split(None, 1)
        path = root / rel.strip()
        actual = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else "MISSING"
        ok = actual == expected
        print(f"{rel.strip()}: {'OK' if ok else 'FAILED'}")
        if not ok:
            print(f"  expected {expected}")
            print(f"  actual   {actual}")
            failed = True

    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
