#pragma once
#include "controller.hpp"
#include <memory>
#include <string>
namespace agb {
struct Obstacle {
    float minX, minY, maxX, maxY;
};
struct GpuState {
    float x{}, y{}, vx{}, vy{}, gravitySign = 1, size = 6, acceleration = .2f, terminal = 8;
    uint32_t mode = 0, held = 0, horizon = 144, count = 0;
};
class GpuRanker {
    struct Impl;
    std::unique_ptr<Impl> impl_;

  public:
    GpuRanker();
    ~GpuRanker();
    bool init();
    bool ready() const;
    std::string status() const;
    std::vector<float> poll();
    bool submit(GpuState, std::vector<Plan> const &, std::vector<Obstacle> const &);
};
} // namespace agb

