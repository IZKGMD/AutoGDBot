#pragma once
#include "controller.hpp"

namespace agb {
// A forecast already verified tomorrow's input. A slow/failed search today
// must not erase its one-substep click window. Reuse only its checked suffix,
// and only while both real players still match the predicted state/time.
class VerifiedInputQueue {
    Plan plan_;
    Evaluation evaluation_;
    std::array<ModeState, 2> states_{};
    int cursor_ = 0, checked_ = 0;
    double dt_ = 0;
    bool dual_ = false;
  public:
    void reset() { cursor_ = checked_ = 0; }
    bool seed(Decision const &d, std::array<ModeState, 2> states, bool dual, double dt) {
        reset();
        if (!d.hasValidatedInput || !d.selected.firstActionVerified ||
            d.selected.died || d.selected.interrupted || !std::isfinite(dt) || dt <= 0) return false;
        checked_ = std::min(d.certificateTicks, d.plan.length);
        for (int i = 0; i < (dual ? 2 : 1); ++i)
            checked_ = std::min(checked_, int(d.selected.traces[i].size()) - 1);
        if (checked_ <= 0) { reset(); return false; }
        plan_ = d.plan;
        evaluation_ = d.selected;
        states_ = states; dual_ = dual; dt_ = dt;
        return true;
    }
    bool matches(std::array<Point, 2> real, std::array<ModeState, 2> states,
                 bool dual, double dt) const {
        if (cursor_ >= checked_ || dual != dual_ || !std::isfinite(dt) ||
            dt <= 0 || std::abs(dt / dt_ - 1) > .05) return false;
        for (int i = 0; i < (dual ? 2 : 1); ++i) {
            if (states[i] != states_[i]) return false;
            auto expected = evaluation_.traces[i][cursor_];
            if (!std::isfinite(real[i].x) || !std::isfinite(real[i].y) ||
                std::hypot(real[i].x - expected.x, real[i].y - expected.y) > .75f) return false;
        }
        return true;
    }
    bool down() const { return cursor_ < checked_ && plan_.at(cursor_); }
    int remaining() const { return std::max(0, checked_ - cursor_); }
    void consume(bool actual) {
        if (cursor_ >= checked_ || actual != down()) reset();
        else ++cursor_;
    }
    Decision continuation() const {
        Decision d;
        if (remaining() <= 0) return d;
        d.down = down(); d.hasValidatedInput = true; d.recedingCertificate = true;
        d.certificateTicks = d.predictedTicks = remaining();
        d.plan.length = remaining();
        for (int t = 0; t < remaining(); ++t) d.plan.buttons[t] = plan_.at(cursor_ + t);
        d.selected = evaluation_;
        d.selected.survived = remaining();
        d.selected.hasRealized = true; d.selected.realized = d.plan;
        for (auto &trace : d.selected.traces) {
            if (trace.size() <= size_t(cursor_)) { trace.clear(); continue; }
            trace.erase(trace.begin(), trace.begin() + cursor_);
            if (trace.size() > size_t(remaining() + 1)) trace.resize(remaining() + 1);
        }
        // Contact/repair indices belong to the old timeline, not this suffix.
        d.selected.orbActions.clear(); d.selected.dashActions.clear();
        d.selected.groundedActions.clear(); d.selected.controlActions.clear();
        return d;
    }
};
}
