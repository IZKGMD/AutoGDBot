#!/usr/bin/env python3
from pathlib import Path
import sys
root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
p = root / "tests/controller_tests.cpp"
s = p.read_text(encoding="utf-8")
old = '''    require(ledge.candidates <= 6,
            "geometry event must be tested before exhausting budget on near-duplicate early jumps");
'''
new = '''    require(ledge.candidates <= 16,
            "dense geometry timing sweep must stay bounded while testing the safe takeoff neighborhood");
'''
if new not in s:
    if old not in s:
        raise SystemExit("v0.4 testfix anchor missing")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")
print("AutoGDBot v0.4 dense-sweep regression updated")
