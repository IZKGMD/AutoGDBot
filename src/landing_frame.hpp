#pragma once
#include "hazard_guard.hpp"
#include <span>
#include <limits>
#include <utility>

namespace agb {
// Local axes: forward travel and height above the supporting surface.
inline Bounds landingFrame(Bounds b, bool sideways, bool upsideDown) {
    if (sideways) b = {b.minY, -b.maxX, b.maxY, -b.minX};
    if (upsideDown) b = {b.minX, -b.maxY, b.maxX, -b.minY};
    return b;
}

inline float supportRunway(Bounds foot, float dx, std::span<Bounds const> solids) {
    float best = std::numeric_limits<float>::infinity();
    std::vector<std::pair<float, float>> support;
    for (auto b : solids)
        if (std::abs(b.maxY - foot.minY) <= 5.f) support.emplace_back(b.minX, b.maxX);
    std::sort(support.begin(), support.end());
    std::vector<std::pair<float, float>> merged;
    for (auto span : support) {
        if (!merged.empty() && span.first <= merged.back().second + 1.5f)
            merged.back().second = std::max(merged.back().second, span.second);
        else merged.push_back(span);
    }
    for (auto span : merged) {
        if (foot.maxX < span.first || foot.minX > span.second) continue;
        const float runway = dx > 0 ? span.second - foot.maxX : foot.minX - span.first;
        best = std::min(best, std::max(0.f, runway));
    }
    return best;
}
}
