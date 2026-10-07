#pragma once
#include <Geode/Geode.hpp>
#include <algorithm>
#include <array>
#include <cstdint>
#include <utility>
#include <vector>
#include "object_semantics.hpp"

namespace agb {
using namespace geode::prelude;

enum class SemanticKind : uint8_t {
    Other,
    Solid,
    Slope,
    Hazard,
    Pad,
    Orb,
    Portal,
    SpeedPortal
};

struct SemanticStats {
    std::array<uint32_t, 8> counts{};
    uint32_t total = 0;
    uint32_t relevant = 0;
};

class SemanticMap {
    std::vector<std::pair<float, GameObject *>> hazards_;
    std::vector<std::pair<float, GameObject *>> solids_;
    std::vector<std::pair<float, GameObject *>> orbs_;
    std::vector<std::pair<float, GameObject *>> control_;
    SemanticStats stats_{};

  public:
    static SemanticKind classify(GameObject *o) {
        if (!o)
            return SemanticKind::Other;
        switch (o->m_objectType) {
        case GameObjectType::Solid:
            return SemanticKind::Solid;
        case GameObjectType::Slope:
            return SemanticKind::Slope;
        case GameObjectType::Hazard:
        case GameObjectType::AnimatedHazard:
            return SemanticKind::Hazard;
        default:
            break;
        }
        if (agb::isPadObject(o))
            return SemanticKind::Pad;
        if (agb::isOrbObject(o))
            return SemanticKind::Orb;
        if (agb::isPortalType(o->m_objectType))
            return SemanticKind::Portal;
        // GD speed portals are stable object IDs across the supported 2.2081 target.
        if (agb::isSpeedPortalObject(o))
            return SemanticKind::SpeedPortal;
        return SemanticKind::Other;
    }

    void build(PlayLayer *layer) {
        hazards_.clear();
        solids_.clear();
        orbs_.clear();
        control_.clear();
        stats_ = {};
        if (!layer || !layer->m_objects)
            return;
        hazards_.reserve(256);
        solids_.reserve(512);
        orbs_.reserve(64);
        control_.reserve(128);
        for (unsigned i = 0; i < layer->m_objects->count(); ++i) {
            auto *o = static_cast<GameObject *>(layer->m_objects->objectAtIndex(i));
            if (!o)
                continue;
            auto kind = classify(o);
            ++stats_.total;
            ++stats_.counts[static_cast<size_t>(kind)];
            const float x = o->getPositionX();
            switch (kind) {
            case SemanticKind::Hazard:
                hazards_.emplace_back(x, o);
                ++stats_.relevant;
                break;
            case SemanticKind::Solid:
            case SemanticKind::Slope:
                solids_.emplace_back(x, o);
                ++stats_.relevant;
                break;
            case SemanticKind::Orb:
                orbs_.emplace_back(x, o);
                control_.emplace_back(x, o);
                ++stats_.relevant;
                break;
            case SemanticKind::Pad:
            case SemanticKind::Portal:
            case SemanticKind::SpeedPortal:
                control_.emplace_back(x, o);
                ++stats_.relevant;
                break;
            default:
                break; // decorative/unknown objects cost nothing during gameplay
            }
        }
        auto sortX = [](auto &v) {
            std::sort(v.begin(), v.end(), [](auto const &a, auto const &b) { return a.first < b.first; });
        };
        sortX(hazards_);
        sortX(solids_);
        sortX(orbs_);
        sortX(control_);
    }

    auto const &hazards() const { return hazards_; }
    auto const &solids() const { return solids_; }
    auto const &orbs() const { return orbs_; }
    auto const &controlObjects() const { return control_; }
    SemanticStats const &stats() const { return stats_; }
};
} // namespace agb
