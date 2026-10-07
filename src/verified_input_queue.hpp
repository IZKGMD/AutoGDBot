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
    bool motionProof_ = false;
    bool modeProof_ = false;
    int proofKind_ = 0;
    bool forecastDied_ = false, forecastInterrupted_ = false;
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
        motionProof_ = !d.selected.motions[0].empty();
        modeProof_ = !d.selected.modeTraces[0].empty();
        if (motionProof_) for (int i = 0; i < (dual ? 2 : 1); ++i)
            if (int(d.selected.motions[i].size()) <= checked_) { reset(); return false; }
        if (modeProof_) for (int i = 0; i < (dual ? 2 : 1); ++i)
            if (int(d.selected.modeTraces[i].size()) <= checked_) { reset(); return false; }
        if (!modeProof_ && !d.selected.transitions.empty()) { reset(); return false; }
        plan_ = d.plan;
        evaluation_ = d.selected;
        proofKind_ = d.proofKind;
        forecastDied_ = d.forecastDied;
        forecastInterrupted_ = d.forecastInterrupted;
        states_ = states; dual_ = dual; dt_ = dt;
        return true;
    }
    bool matches(std::array<Point, 2> real, std::array<ModeState, 2> states,
                 bool dual, double dt, std::array<MotionSample, 2> const *motion = nullptr) const {
        if (cursor_ >= checked_ || dual != dual_ || !std::isfinite(dt) ||
            dt <= 0 || std::abs(dt / dt_ - 1) > .05) return false;
        for (int i = 0; i < (dual ? 2 : 1); ++i) {
            if (states[i] != (modeProof_ ? evaluation_.modeTraces[i][cursor_] : states_[i]))
                return false;
            auto expected = evaluation_.traces[i][cursor_];
            if (!std::isfinite(real[i].x) || !std::isfinite(real[i].y) ||
                std::hypot(real[i].x - expected.x, real[i].y - expected.y) > .25f) return false;
            if (motionProof_ && (!motion || !evaluation_.motions[i][cursor_].matches((*motion)[i])))
                return false;
        }
        return true;
    }
    bool down() const { return cursor_ < checked_ && plan_.at(cursor_); }
    bool transitionDue(bool held) const { return remaining() > 0 && down() != held; }
    int remaining() const { return std::max(0, checked_ - cursor_); }
    void consume(bool actual) {
        if (cursor_ >= checked_ || actual != down()) reset();
        else ++cursor_;
    }
    Decision continuation() const {
        Decision d;
        if (remaining() <= 0) return d;
        d.down = down(); d.hasValidatedInput = true; d.recedingCertificate = true;
        d.proofKind = proofKind_;
        d.forecastDied = forecastDied_;
        d.forecastInterrupted = forecastInterrupted_;
        d.certificateTicks = d.predictedTicks = remaining();
        d.plan.length = remaining();
        for (int t = 0; t < remaining(); ++t) d.plan.buttons[t] = plan_.at(cursor_ + t);
        d.selected = evaluation_;
        advanceEvaluation(d.selected, cursor_);
        d.selected.survived = remaining();
        d.selected.groundedPrefixTicks = std::clamp(evaluation_.groundedPrefixTicks - cursor_, 0, remaining());
        d.selected.hasRealized = true; d.selected.realized = d.plan;
        for (auto &trace : d.selected.traces) {
            if (trace.size() > size_t(remaining() + 1)) trace.resize(remaining() + 1);
        }
        for (auto &motion : d.selected.motions) {
            if (motion.size() > size_t(remaining() + 1)) motion.resize(remaining() + 1);
        }
        for (auto &m : d.selected.modeTraces)
            if (m.size() > size_t(remaining() + 1)) m.resize(remaining() + 1);
        d.selected.routeId = routeFingerprint(d.plan);
        // Contact/repair indices belong to the old timeline, not this suffix.
        d.selected.orbActions.clear(); d.selected.dashActions.clear();
        d.selected.groundedActions.clear(); d.selected.controlActions.clear();
        return d;
    }
};
}
