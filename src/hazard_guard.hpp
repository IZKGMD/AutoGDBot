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
// Continuous translated-AABB test supplements native clone collisions.
// Intersect the time intervals of all four separating-axis inequalities;
// unlike a union box this does not reject unrelated diagonal corners.
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
            if (it->maxX >= b.minX && it->maxY >= b.minY && it->minY <= b.maxY) {
                double enter = 0, leave = 1;
                auto nonnegative = [&](double start, double end) {
                    const double delta = end - start;
                    if (std::abs(delta) < 1e-12)
                        return start >= 0;
                    const double crossing = -start / delta;
                    if (delta > 0) enter = std::max(enter, crossing);
                    else leave = std::min(leave, crossing);
                    return enter <= leave;
                };
                if (nonnegative(before.maxX - it->minX, after.maxX - it->minX) &&
                    nonnegative(it->maxX - before.minX, it->maxX - after.minX) &&
                    nonnegative(before.maxY - it->minY, after.maxY - it->minY) &&
                    nonnegative(it->maxY - before.minY, it->maxY - after.minY))
                    return true;
            }
        return false;
    }
};
} // namespace agb

