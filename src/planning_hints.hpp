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
inline void addLandingEvent(Config &cfg, int tick) {
    if (tick < 0 || tick >= cfg.horizon ||
        cfg.landingEventCount >= int(cfg.landingEvents.size()))
        return;
    for (int i = 0; i < cfg.landingEventCount; ++i)
        if (std::abs(cfg.landingEvents[i] - tick) <= 2)
            return;
    cfg.landingEvents[cfg.landingEventCount++] = tick;
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

    struct StepTarget { float tick, topY, width; };
    std::vector<StepTarget> steps;
    const float directionForSteps = dx > 0 ? 1.f : -1.f;
    for (auto b : solids) {
        const float top = b.maxY;
        const float width = b.maxX - b.minX;
        if (width < 6.f || width > 120.f)
            continue;
        if (top <= footY + 3.f || top > footY + 190.f)
            continue;
        const float center = (b.minX + b.maxX) * .5f;
        const float dist = (center - x) * directionForSteps;
        if (dist <= 8.f)
            continue;
        const float t = dist / std::abs(dx);
        if (t > 6.f && t < cfg.horizon)
            steps.push_back({t, top, width});
    }
    std::sort(steps.begin(), steps.end(), [](auto a, auto b) { return a.tick < b.tick; });
    float lastTop = footY;
    int acceptedSteps = 0;
    for (auto s : steps) {
        if (s.topY < lastTop - 8.f || s.topY > lastTop + 72.f)
            continue;
        addLandingEvent(cfg, int(std::lround(s.tick)));
        lastTop = std::max(lastTop, s.topY);
        if (++acceptedSteps >= 8)
            break;
    }

    gap = findForwardGap(x, footY, dx, solids,
                         std::max(420.f, std::abs(dx) * float(cfg.horizon + 24)));
    if (gap.found) {
        // A gap needs commitment before the cube leaves support. These are
        // intentionally earlier than the low-hazard reflex timings.
        for (int lead : {12, 20, 28, 36, 48, 64, 80})
            addJumpEvent(cfg, std::max(0, gap.edgeTick - lead));
    }

    struct FloorHazard { float nearDist, farDist; };
    std::vector<FloorHazard> floorHazards;
    const float direction = dx > 0 ? 1.f : -1.f;
    const float speed = std::abs(dx);
    for (auto b : hazards) {
        const float h = b.maxY - b.minY;
        if (h > 46.f || b.maxY < footY || b.minY > footY + 42.f)
            continue;
        float nearFace = dx > 0 ? b.minX : b.maxX;
        float farFace = dx > 0 ? b.maxX : b.minX;
        float nearDist = (nearFace - x) * direction;
        float farDist = (farFace - x) * direction;
        if (farDist > 0)
            floorHazards.push_back({nearDist, farDist});
    }
    std::sort(floorHazards.begin(), floorHazards.end(),
              [](auto a, auto b) { return a.nearDist < b.nearDist; });
    for (size_t i = 0; i < floorHazards.size();) {
        size_t j = i + 1;
        float end = floorHazards[i].farDist;
        while (j < floorHazards.size() && floorHazards[j].nearDist - end <= 38.f) {
            end = std::max(end, floorHazards[j].farDist);
            ++j;
        }
        if (j - i >= 2 && floorHazards[i].nearDist > 0) {
            int firstTick = int(std::floor(floorHazards[i].nearDist / speed));
            for (int lead : {40, 48, 56, 64, 72, 80, 88, 96})
                addJumpEvent(cfg, firstTick - lead);
        }
        i = j;
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

