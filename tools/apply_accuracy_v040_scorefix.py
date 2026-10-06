#!/usr/bin/env python3
from pathlib import Path
import sys
root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
p = root / "src/controller.hpp"
s = p.read_text(encoding="utf-8")
old = '''            const float safeClearance = std::clamp(e.clearance, 0.f, 120.f);
            const float safeLanding = std::clamp(e.landingMargin, 0.f, 80.f);
            const float robust = std::clamp(e.robustness, 0.f, 1.f);
            float score = e.survived * 10000.f +
                          safeClearance * 85.f + safeLanding * 55.f + robust * 18000.f -
                          changes * 18.f + continuity * 5.f;
'''
new = '''            const float safeClearance = std::clamp(e.clearance, 0.f, 120.f);
            const float safeLanding = std::clamp(e.landingMargin, 0.f, 80.f);
            const float robust = std::clamp(e.robustness, 0.f, 1.f);
            // Clearance is meaningful only when the runtime rollout populated the
            // robustness metric from the whole trajectory. This prevents an endpoint
            // or synthetic clearance value from causing an unnecessary jump on open ground.
            const float marginWeight = robust > 0.f ? 1.f : 0.f;
            float score = e.survived * 10000.f +
                          safeClearance * 85.f * marginWeight +
                          safeLanding * 55.f * marginWeight + robust * 18000.f -
                          changes * 18.f + continuity * 5.f;
'''
if new not in s:
    if old not in s:
        raise SystemExit("v0.4 scorefix anchor missing")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")
print("AutoGDBot v0.4 robustness score gate applied")
