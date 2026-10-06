#pragma once
#include <algorithm>
#include <cmath>
#include <vector>

namespace agb {
struct Bounds {
    float minX, minY, maxX, maxY;
};
inline Bounds sweptBounds(Bounds a, Bounds b) {
    return {std::min(a.minX, b.minX), std::min(a.minY, b.minY), std::max(a.maxX, b.maxX),
            std::max(a.maxY, b.maxY)};
}
// Conservative bounding-box rejection supplements native clone collisions.
// It never declares a path safe and may reject a feasible path near a corner.
class HazardGuard {
    std::vector<Bounds> hazards_;
    float maxWidth_ = 0;

  public:
    void set(std::vector<Bounds> hazards) {
        hazards_.clear();
        maxWidth_ = 0;
        for (auto b : hazards) {
            if (!std::isfinite(b.minX) || !std::isfinite(b.minY) || !std::isfinite(b.maxX) ||
                !std::isfinite(b.maxY) || b.maxX <= b.minX || b.maxY <= b.minY)
                continue;
            maxWidth_ = std::max(maxWidth_, b.maxX - b.minX);
            hazards_.push_back(b);
        }
        std::sort(hazards_.begin(), hazards_.end(),
                  [](Bounds a, Bounds b) { return a.minX < b.minX; });
    }
    bool hit(Bounds before, Bounds after) const {
        auto b = sweptBounds(before, after);
        auto first = std::lower_bound(hazards_.begin(), hazards_.end(), b.minX - maxWidth_,
                                      [](Bounds a, float x) { return a.minX < x; });
        for (auto it = first; it != hazards_.end() && it->minX <= b.maxX; ++it)
            if (it->maxX >= b.minX && it->maxY >= b.minY && it->minY <= b.maxY)
                return true;
        return false;
    }
};
} // namespace agb
