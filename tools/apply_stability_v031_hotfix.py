#!/usr/bin/env python3
from pathlib import Path
import sys
root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
p = root / "src/controller.hpp"
s = p.read_text(encoding="utf-8")
old = '''            float score = e.survived * 10000.f +
                          std::clamp(e.clearance, 0.f, 100.f) * 18.f - changes * 12.f +
                          continuity * 10.f;
            if (complete)
                score += 3000000.f;
'''
new = '''            const bool exactWarm = continuityTicks > 0 && continuity == continuityTicks;
            float score = e.survived * 10000.f +
                          std::clamp(e.clearance, 0.f, 100.f) * 18.f - changes * 12.f +
                          continuity * 10.f;
            if (exactWarm)
                score += 5000.f;
            if (complete)
                score += 3000000.f;
'''
if new not in s:
    if old not in s:
        raise SystemExit("missing warm-score anchor")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")
print("v0.3.1 warm-plan hotfix applied")
