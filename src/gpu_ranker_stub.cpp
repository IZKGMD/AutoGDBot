#include "gpu_ranker.hpp"

namespace agb {
struct GpuRanker::Impl {
    std::string message = "GPU unavailable: CPU route planner";
};
GpuRanker::GpuRanker() : impl_(std::make_unique<Impl>()) {}
GpuRanker::~GpuRanker() = default;
bool GpuRanker::init() { return false; }
bool GpuRanker::ready() const { return false; }
std::string GpuRanker::status() const { return impl_->message; }
std::vector<float> GpuRanker::poll() { return {}; }
bool GpuRanker::submit(GpuState, std::vector<Plan> const &, std::vector<Obstacle> const &) {
    return false;
}
} // namespace agb
