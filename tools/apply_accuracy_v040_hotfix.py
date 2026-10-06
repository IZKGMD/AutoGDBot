#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
p = root / "src/controller.hpp"
s = p.read_text(encoding="utf-8")
old = '''            return complete && warmCompatible && result.plan.at(0) == plan.at(0) &&
                   (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)));
'''
new = '''            // Never stop at the first merely-surviving geometry route. Without a
            // previously verified warm route, compare the protected timing neighborhood
            // so a wide-margin takeoff can beat a spike-grazing one. With a warm route,
            // early-stop is allowed only when that route itself has meaningful margin.
            const bool currentRobust = robust >= .55f || safeClearance >= 18.f;
            return complete && warm_.length > 0 && currentRobust && warmCompatible &&
                   result.plan.at(0) == plan.at(0) &&
                   (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)));
'''
if new not in s:
    if old not in s:
        raise SystemExit("v0.4 hotfix anchor missing")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")
print("AutoGDBot v0.4 robust-route hotfix applied")
