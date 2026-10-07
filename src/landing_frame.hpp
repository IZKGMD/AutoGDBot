#pragma once
#include "hazard_guard.hpp"

namespace agb {
// Local axes: forward travel and height above the supporting surface.
inline Bounds landingFrame(Bounds b, bool sideways, bool upsideDown) {
    if (sideways) b = {b.minY, -b.maxX, b.maxY, -b.minX};
    if (upsideDown) b = {b.minX, -b.maxY, b.maxX, -b.minY};
    return b;
}
}
