#!/usr/bin/env python3
from pathlib import Path
import sys
root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
p = root / "src/controller.hpp"
s = p.read_text(encoding="utf-8")
old = '''            const bool currentRobust = robust >= .55f || safeClearance >= 18.f;
            return complete && warm_.length > 0 && currentRobust && warmCompatible &&
                   result.plan.at(0) == plan.at(0) &&
                   (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)));
'''
new = '''            const bool currentRobust = robust >= .55f || safeClearance >= 18.f;
            const bool geometrySensitive = cfg.jumpEventCount > 0 || cfg.orbEventCount > 0 ||
                                           cfg.landingEventCount > 0;
            // Open ground keeps the cheap three-evaluation fast path. Around hazards,
            // orbs and landing chains we compare the protected timing neighborhood unless
            // an already-verified robust warm route can be reused safely.
            const bool mayEarlyStop = !geometrySensitive ||
                                      (warm_.length > 0 && currentRobust && warmCompatible);
            return complete && mayEarlyStop && result.plan.at(0) == plan.at(0) &&
                   (plan.at(0) == held || (cfg.forceRelease && !plan.at(0)));
'''
if new not in s:
    if old not in s:
        raise SystemExit("v0.4 early-stop anchor missing")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")
print("AutoGDBot v0.4 adaptive early-stop applied")
