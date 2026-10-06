#pragma once
#include "controller.hpp"
#include "hazard_guard.hpp"
#include <span>

namespace agb {
struct GapHint {
    bool found = false;
    float edgeX = 0;
    float landingX = 0;
    float landingY = 0;
    float width = 0;
    int edgeTick = -1;
};

struct HazardClusterHint {
    bool found = false;
    float startX = 0;
    float endX = 0;
    float width = 0;
    int count = 0;
    int startTick = -1;
};

// Last-resort reaction for a grounded cube. Keep this deliberately narrow:
// native rollouts should decide large saws and tall hazards. The reflex exists
// only for low floor hazards (spikes / short teeth) when the search budget is
// exhausted before a takeoff can be certified.
inline bool shouldReflexJump(Bounds player, float dx, std::span<Bounds const> hazards) {
    if (!std::isfinite(dx) || std::abs(dx) < .0001f)
        return false;
    const float playerHeight = std::max(1.f, player.maxY - player.minY);
    const float maxHazardHeight = std::max(38.f, playerHeight * 1.35f);
    const float baseGap = std::clamp(std::abs(dx) * 52.f, 48.f, 84.f);
    const float clusterGap = std::clamp(std::abs(dx) * 70.f, 68.f, 112.f);

    struct LowHazard {
        Bounds b;
        float gap;
    };
    std::vector<LowHazard> low;
    low.reserve(hazards.size());
    for (auto b : hazards) {
        const float hazardHeight = b.maxY - b.minY;
        if (hazardHeight > maxHazardHeight)
            continue; // large saw / tall obstacle: let the rollout time it
        if (b.maxY < player.minY + 1.f || b.minY > player.maxY + 14.f)
            continue;
        float gap = dx > 0 ? b.minX - player.maxX : player.minX - b.maxX;
        if (gap < -std::abs(dx) * 2.f || gap > clusterGap + 80.f)
            continue;
        low.push_back({b, gap});
    }
    std::sort(low.begin(), low.end(), [&](LowHazard const &a, LowHazard const &b) {
        return a.gap < b.gap;
    });
    for (size_t i = 0; i < low.size(); ++i) {
        bool clustered = false;
        if (i + 1 < low.size()) {
            float between = dx > 0 ? low[i + 1].b.minX - low[i].b.maxX
                                   : low[i].b.minX - low[i + 1].b.maxX;
            clustered = between <= 24.f;
        }
        const float allowed = clustered ? clusterGap : baseGap;
        if (low[i].gap <= allowed)
            return true;
    }
    return false;
}
inline void addJumpEvent(Config &cfg, int tick) {
    if (tick < 0 || tick >= cfg.horizon || cfg.jumpEventCount >= int(cfg.jumpEvents.size()))
        return;
    for (int i = 0; i < cfg.jumpEventCount; ++i)
        if (cfg.jumpEvents[i] == tick)
            return;
    cfg.jumpEvents[cfg.jumpEventCount++] = tick;
}
inline void addOrbEvent(Config &cfg, int tick) {
    if (tick < 0 || tick >= cfg.horizon || cfg.orbEventCount >= int(cfg.orbEvents.size()))
        return;
    for (int i = 0; i < cfg.orbEventCount; ++i)
        if (cfg.orbEvents[i] == tick)
            return;
    cfg.orbEvents[cfg.orbEventCount++] = tick;
}

inline GapHint findForwardGap(float x, float footY, float dx, std::span<Bounds const> solids,
                              float lookaheadDistance = 420.f) {
    GapHint out;
    if (!std::isfinite(dx) || std::abs(dx) < .0001f)
        return out;

    struct Span { float lo, hi; };
    std::vector<Span> support;
    for (auto b : solids) {
        if (std::abs(b.maxY - footY) <= 2.5f)
            support.push_back({b.minX, b.maxX});
    }
    std::sort(support.begin(), support.end(), [](Span a, Span b) { return a.lo < b.lo; });
    std::vector<Span> merged;
    for (auto s : support) {
        if (!merged.empty() && s.lo <= merged.back().hi + .75f)
            merged.back().hi = std::max(merged.back().hi, s.hi);
        else
            merged.push_back(s);
    }

    float edge = x;
    bool onSupport = false;
    for (auto s : merged) {
        if (x >= s.lo - .75f && x <= s.hi + .75f) {
            edge = dx > 0 ? s.hi : s.lo;
            onSupport = true;
            break;
        }
    }
    if (!onSupport)
        return out;

    const float direction = dx > 0 ? 1.f : -1.f;
    float bestDistance = lookaheadDistance;
    float landingX = edge + direction * lookaheadDistance;
    float landingY = footY;
    bool foundLanding = false;
    for (auto b : solids) {
        // Allow a landing somewhat below or above the current block. This is a
        // route hint, not a collision verdict; native simulation verifies it.
        if (b.maxY < footY - 120.f || b.maxY > footY + 90.f)
            continue;
        float face = dx > 0 ? b.minX : b.maxX;
        float distance = (face - edge) * direction;
        if (distance <= .75f || distance >= bestDistance)
            continue;
        bestDistance = distance;
        landingX = face;
        landingY = b.maxY;
        foundLanding = true;
    }

    // A visible horizontal break larger than a few pixels is actionable. If no
    // landing exists in the lookahead window, treat the platform end as a gap.
    if (!foundLanding || bestDistance > std::max(8.f, std::abs(dx) * 5.f)) {
        out.found = true;
        out.edgeX = edge;
        out.landingX = landingX;
        out.landingY = landingY;
        out.width = foundLanding ? bestDistance : lookaheadDistance;
        out.edgeTick = std::max(0, int(std::floor((edge - x) / dx)));
    }
    return out;
}

inline HazardClusterHint findForwardHazardCluster(float x, float footY, float dx,
                                                   std::span<Bounds const> hazards,
                                                   float lookaheadDistance = 420.f) {
    HazardClusterHint out;
    if (!std::isfinite(dx) || std::abs(dx) < .0001f)
        return out;
    const float direction = dx > 0 ? 1.f : -1.f;

    std::vector<Bounds> floor;
    for (auto b : hazards) {
        if (b.maxY < footY - 3.f || b.minY > footY + 42.f)
            continue;
        float face = dx > 0 ? b.minX : b.maxX;
        float distance = (face - x) * direction;
        if (distance <= 0 || distance > lookaheadDistance)
            continue;
        floor.push_back(b);
    }
    std::sort(floor.begin(), floor.end(), [&](Bounds a, Bounds b) {
        float ax = dx > 0 ? a.minX : -a.maxX;
        float bx = dx > 0 ? b.minX : -b.maxX;
        return ax < bx;
    });
    if (floor.empty())
        return out;

    for (size_t i = 0; i < floor.size();) {
        float startX = dx > 0 ? floor[i].minX : floor[i].maxX;
        float endX = dx > 0 ? floor[i].maxX : floor[i].minX;
        int count = 1;
        size_t j = i + 1;
        for (; j < floor.size(); ++j) {
            float gap = dx > 0 ? floor[j].minX - endX : endX - floor[j].maxX;
            if (gap > 24.f)
                break;
            endX = dx > 0 ? std::max(endX, floor[j].maxX) : std::min(endX, floor[j].minX);
            ++count;
        }
        float width = std::abs(endX - startX);
        if (count >= 2 || width >= 44.f) {
            out.found = true;
            out.startX = startX;
            out.endX = endX;
            out.width = width;
            out.count = count;
            out.startTick = std::max(0, int(std::floor((startX - x) / dx)));
            return out;
        }
        i = j;
    }
    return out;
}

// Seed timing candidates from geometry that is farther ahead than the reflex
// window. The controller still runs native physics for every accepted plan.
inline GapHint seedCubeEvents(Config &cfg, float x, float footY, float dx,
                              std::span<Bounds const> solids,
                              std::span<Bounds const> hazards,
                              std::span<Bounds const> orbs = {}) {
    GapHint gap;
    if (!std::isfinite(dx) || std::abs(dx) < .0001f)
        return gap;

    std::vector<Bounds> support;
    for (auto b : solids)
        if (std::abs(b.maxY - footY) <= 2.5f)
            support.push_back(b);
    std::sort(support.begin(), support.end(), [](Bounds a, Bounds b) { return a.minX < b.minX; });
    for (size_t i = 0; i < support.size();) {
        float lo = support[i].minX, hi = support[i].maxX;
        size_t j = i + 1;
        while (j < support.size() && support[j].minX <= hi + .75f) {
            hi = std::max(hi, support[j].maxX);
            ++j;
        }
        if (x >= lo - .75f && x <= hi + .75f) {
            float edge = dx > 0 ? hi : lo;
            int edgeTick = int(std::floor((edge - x) / dx));
            // Keep near-edge timings, but also give the rollout several earlier
            // takeoffs so it can clear spikes below the platform lip.
            for (int lead : {2, 8, 16, 24, 32, 40, 56})
                addJumpEvent(cfg, std::max(0, edgeTick - lead));
            break;
        }
        i = j;
    }

    gap = findForwardGap(x, footY, dx, solids,
                         std::max(420.f, std::abs(dx) * float(cfg.horizon + 24)));
    if (gap.found) {
        // A gap needs commitment before the cube leaves support. These are
        // intentionally earlier than the low-hazard reflex timings.
        for (int lead : {12, 20, 28, 36, 48, 64, 80})
            addJumpEvent(cfg, std::max(0, gap.edgeTick - lead));
    }

    auto cluster = findForwardHazardCluster(
        x, footY, dx, hazards, std::max(420.f, std::abs(dx) * float(cfg.horizon + 24)));
    if (cluster.found) {
        // Adjacent spikes are less tolerant of a marginal takeoff. Seed the
        // center of the useful window first, then expand around it.
        const int widthLead =
            std::clamp(int(std::lround(cluster.width / std::max(1.f, std::abs(dx)) * .12f)),
                       0, 10);
        for (int lead : {34, 38, 30, 42, 26, 46, 22, 50})
            addJumpEvent(cfg, cluster.startTick - lead - widthLead);
    }

    float nearestHazard = float(cfg.horizon + 100);
    float nearestLanding = nearestHazard;
    for (auto b : hazards) {
        float t = ((dx > 0 ? b.minX : b.maxX) - x) / dx;
        if (t > 0 && b.maxY >= footY && b.minY < footY + 110)
            nearestHazard = std::min(nearestHazard, t);
    }
    for (auto b : solids) {
        float t = ((dx > 0 ? b.minX : b.maxX) - x) / dx;
        if (t > 0 && b.maxY > footY + 2 && b.maxY < footY + 110)
            nearestLanding = std::min(nearestLanding, t);
    }
    for (int lead : {12, 20, 28, 36, 48})
        addJumpEvent(cfg, int(nearestHazard) - lead);
    for (int lead : {20, 32, 44, 56, 72})
        addJumpEvent(cfg, int(nearestLanding) - lead);

    // Orb routing: schedule both a takeoff window and a second pulse around the
    // horizontal contact time. Two-pulse candidates are built in Controller.
    for (auto b : orbs) {
        const float centerX = (b.minX + b.maxX) * .5f;
        const float centerY = (b.minY + b.maxY) * .5f;
        const float t = (centerX - x) / dx;
        if (t <= 3 || t >= cfg.horizon)
            continue;
        if (centerY < footY - 80.f || centerY > footY + 180.f)
            continue;
        const int orbTick = int(std::lround(t));
        for (int offset : {-3, -2, -1, 0, 1, 2, 3})
            addOrbEvent(cfg, orbTick + offset);
        const int heightLead = std::clamp(int(std::max(0.f, centerY - footY) * .16f), 0, 24);
        for (int lead : {24, 32, 40, 48, 56, 64, 80})
            addJumpEvent(cfg, orbTick - lead - heightLead);
    }
    return gap;
}
} // namespace agb
