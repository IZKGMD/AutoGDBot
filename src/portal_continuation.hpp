#pragma once
#include "mode_policy.hpp"
#include <algorithm>
#include <cmath>

namespace agb {
struct PortalBand { float floor = 0, ceiling = 0; bool active = false; };
// gdsolver's measured normal-frame band formula (GD 2.2081).
// The height comes from native getGroundHeight rather than a per-mode guess.
// Reference: gdsolver/gdsolver dp/src/dp/bands.hpp, revision 8e8d69a.
inline PortalBand portalBand(float y, float height) {
    if (!std::isfinite(y) || !std::isfinite(height) || height <= 0) return {};
    const float floor = std::max(90.f, std::floor((y - height * .5f) / 30.f) * 30.f);
    return {floor, floor + height, true};
}
class PortalContinuation {
    int latest_ = -1;
  public:
    void changed(int completedTick) { latest_ = completedTick; }
    bool required() const { return latest_ >= 0; }
    int after(int survived) const { return latest_ < 0 ? 0 : std::max(0, survived - latest_ - 1); }
    bool verified(int survived, double dt) const {
        if (!required()) return true;
        if (!std::isfinite(dt) || dt <= 0) return false;
        return after(survived) >= std::max(4, int(std::ceil(.18 / dt)));
    }
};
}
