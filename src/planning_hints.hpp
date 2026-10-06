#pragma once
#include "controller.hpp"
#include "hazard_guard.hpp"
#include <span>

namespace agb {
// Last-resort reaction for a grounded cube. Native rollouts remain the main
// controller; this catches a nearby floor hazard when the time budget expires
// before a takeoff candidate can be certified.
inline bool shouldReflexJump(Bounds player, float dx, std::span<Bounds const> hazards) {
    if (!std::isfinite(dx) || std::abs(dx) < .0001f)
        return false;
    const float maxGap = std::clamp(std::abs(dx) * 52.f, 48.f, 84.f);
    for (auto b : hazards) {
        if (b.maxY < player.minY + 1.f || b.minY > player.maxY + 18.f)
            continue;
        float gap = dx > 0 ? b.minX - player.maxX : player.minX - b.maxX;
        if (gap >= -std::abs(dx) * 2.f && gap <= maxGap)
            return true;
    }
    return false;
}
// Event times prioritize native rollouts. They are not collision verdicts.
inline void addJumpEvent(Config &cfg, int tick) {
    if (tick < 0 || tick >= cfg.horizon || cfg.jumpEventCount >= int(cfg.jumpEvents.size()))
        return;
    for (int i = 0; i < cfg.jumpEventCount; ++i)
        if (cfg.jumpEvents[i] == tick)
            return;
    cfg.jumpEvents[cfg.jumpEventCount++] = tick;
}
inline void seedCubeEvents(Config &cfg, float x, float footY, float dx,
                           std::span<Bounds const> solids, std::span<Bounds const> hazards) {
    if (!std::isfinite(dx) || std::abs(dx) < .0001f)
        return;
    std::vector<Bounds> support;
    for (auto b : solids)
        if (std::abs(b.maxY - footY) <= 2.f)
            support.push_back(b);
    std::sort(support.begin(), support.end(), [](Bounds a, Bounds b) { return a.minX < b.minX; });
    for (size_t i = 0; i < support.size();) {
        float lo = support[i].minX, hi = support[i].maxX;
        size_t j = i + 1;
        while (j < support.size() && support[j].minX <= hi + .5f) {
            hi = std::max(hi, support[j].maxX);
            ++j;
        }
        if (x >= lo - .5f && x <= hi + .5f) {
            float edge = dx > 0 ? hi : lo;
            int edgeTick = int(std::floor((edge - x) / dx));
            for (int lead : {2, 8, 16, 24})
                addJumpEvent(cfg, std::max(0, edgeTick - lead));
            break;
        }
        i = j;
    }
    // Test takeoff times before the nearest hazard and raised landing surface.
    float nearestHazard = float(cfg.horizon + 100);
    float nearestLanding = nearestHazard;
    for (auto b : hazards) {
        float t = ((dx > 0 ? b.minX : b.maxX) - x) / dx;
        if (t > 0 && b.maxY >= footY && b.minY < footY + 100)
            nearestHazard = std::min(nearestHazard, t);
    }
    for (auto b : solids) {
        float t = ((dx > 0 ? b.minX : b.maxX) - x) / dx;
        if (t > 0 && b.maxY > footY + 2 && b.maxY < footY + 100)
            nearestLanding = std::min(nearestLanding, t);
    }
    for (int lead : {16, 24, 32})
        addJumpEvent(cfg, int(nearestHazard) - lead);
    for (int lead : {24, 40, 56})
        addJumpEvent(cfg, int(nearestLanding) - lead);
}
} // namespace agb
