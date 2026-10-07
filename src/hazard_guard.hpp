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
    std::vector<Bounds> exact_;
    float maxWidth_ = 0;

    static bool valid(Bounds b) {
        return std::isfinite(b.minX) && std::isfinite(b.minY) &&
               std::isfinite(b.maxX) && std::isfinite(b.maxY) &&
               b.maxX > b.minX && b.maxY > b.minY;
    }
    static bool overlaps(Bounds a, Bounds h) {
        return a.maxX >= h.minX && a.minX <= h.maxX &&
               a.maxY >= h.minY && a.minY <= h.maxY;
    }
    static bool sweptHit(Bounds before, Bounds after, Bounds h) {
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
        return nonnegative(before.maxX - h.minX, after.maxX - h.minX) &&
               nonnegative(h.maxX - before.minX, h.maxX - after.minX) &&
               nonnegative(before.maxY - h.minY, after.maxY - h.minY) &&
               nonnegative(h.maxY - before.minY, h.maxY - after.minY);
    }

  public:
    // Physical GameObject rectangles are coarse safety supplements. If the
    // live player already overlaps one at the beginning of a tick, native GD
    // collision has already proven that state survivable, so only ENTRY into a
    // new coarse region is rejected.
    void set(std::vector<Bounds> hazards) {
        hazards_.clear();
        exact_.clear();
        maxWidth_ = 0;
        for (auto b : hazards) {
            if (!valid(b))
                continue;
            maxWidth_ = std::max(maxWidth_, b.maxX - b.minX);
            hazards_.push_back(b);
        }
        std::sort(hazards_.begin(), hazards_.end(),
                  [](Bounds a, Bounds b) { return a.minX < b.minX; });
    }

    // Mathematical guards created by the planner (portal handoff clearance
    // gates) are exact constraints, not approximated object hitboxes.
    void addExact(Bounds b) {
        if (valid(b))
            exact_.push_back(b);
    }

    // Reuse the same frozen-world bounds as collision checking. Ranking must
    // not call native getObjectRect for every hazard on every clone substep.
    float clearance(Bounds player, float range = 100.f) const {
        float best = 120.f;
        auto first = std::lower_bound(hazards_.begin(), hazards_.end(), player.minX - range - maxWidth_,
            [](Bounds a, float x) { return a.minX < x; });
        for (auto it = first; it != hazards_.end() && it->minX <= player.maxX + range; ++it) {
            if (it->maxX < player.minX - range) continue;
            const float dx = std::max({it->minX - player.maxX, player.minX - it->maxX, 0.f});
            const float dy = std::max({it->minY - player.maxY, player.minY - it->maxY, 0.f});
            best = std::min(best, std::sqrt(dx * dx + dy * dy));
        }
        return best;
    }

    bool hit(Bounds before, Bounds after) const {
        auto b = sweptBounds(before, after);
        auto first = std::lower_bound(hazards_.begin(), hazards_.end(), b.minX - maxWidth_,
                                      [](Bounds a, float x) { return a.minX < x; });
        for (auto it = first; it != hazards_.end() && it->minX <= b.maxX; ++it) {
            if (it->maxX < b.minX || it->maxY < b.minY || it->minY > b.maxY)
                continue;
            if (overlaps(before, *it))
                continue;
            if (sweptHit(before, after, *it))
                return true;
        }
        for (auto const &h : exact_) {
            auto broad = sweptBounds(before, after);
            if (h.maxX < broad.minX || h.minX > broad.maxX ||
                h.maxY < broad.minY || h.minY > broad.maxY)
                continue;
            if (sweptHit(before, after, h))
                return true;
        }
        return false;
    }
};
} // namespace agb


