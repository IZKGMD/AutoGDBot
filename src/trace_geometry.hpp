#pragma once
#include "controller.hpp"

namespace agb {
struct TraceSegment { Point a, b; };
// Keep the refresh phase instead of scheduling from each slightly late frame.
// A small presentation-only slack avoids turning 120 Hz into 60 Hz when the
// render thread arrives a fraction of a millisecond before its nominal slot.
class TraceCadence {
    Clock::time_point next_ = Clock::time_point::min();
    int hz_ = 0;
  public:
    void reset() { next_ = Clock::time_point::min(); hz_ = 0; }
    bool consume(Clock::time_point now,int hz) {
        hz=std::clamp(hz,20,240);
        auto period=std::chrono::nanoseconds(1000000000/hz);
        auto slack=std::min(period/10,std::chrono::nanoseconds(350000));
        if(hz_!=hz || next_==Clock::time_point::min()){hz_=hz;next_=now;}
        if(now+slack<next_)return false;
        next_+=period;
        if(next_<=now)next_=now+period;
        return true;
    }
};
// Simplify only near-collinear native samples. Preserve corners, the last
// segment, and explicit teleports. Never interpolate a predicted path across
// a different route or turn a collision boundary into a smooth fake curve.
inline std::vector<TraceSegment> traceSegments(std::vector<Point> const &points,
        std::vector<int> const &breaks, float tolerance = .18f, size_t first = 0,
        size_t last = std::numeric_limits<size_t>::max()) {
    std::vector<TraceSegment> out;
    last = std::min(last, points.size());
    if (first + 1 >= last) return out;
    out.reserve(std::min<size_t>(192, last - first));
    size_t anchor = first;
    auto error = [&](Point p, Point a, Point b) {
        float dx = b.x - a.x, dy = b.y - a.y;
        float norm = dx * dx + dy * dy;
        float u = norm > 1e-8f ? std::clamp(((p.x - a.x) * dx + (p.y - a.y) * dy) / norm, 0.f, 1.f) : 0.f;
        return std::hypot(p.x - a.x - u * dx, p.y - a.y - u * dy);
    };
    for (size_t end = first + 1; end < last; ++end) {
        if (std::find(breaks.begin(), breaks.end(), int(end)) != breaks.end()) {
            if (end > anchor + 1) out.push_back({points[anchor], points[end - 1]});
            anchor = end; continue;
        }
        bool bend = false;
        for (size_t t = anchor + 1; t < end; ++t)
            if (error(points[t], points[anchor], points[end]) > tolerance) { bend = true; break; }
        if (bend || end - anchor >= 32) {
            if (end > anchor + 1) out.push_back({points[anchor], points[end - 1]});
            anchor = end - 1;
        }
    }
    if (anchor + 1 < last) out.push_back({points[anchor], points[last - 1]});
    return out;
}
}

