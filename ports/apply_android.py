#!/usr/bin/env python3
import argparse
import json
from pathlib import Path

STUB = r'''#include "gpu_ranker.hpp"

namespace agb {

struct GpuRanker::Impl {
    std::string message = "GPU unavailable: CPU fallback (Android)";
};

GpuRanker::GpuRanker() : impl_(std::make_unique<Impl>()) {}
GpuRanker::~GpuRanker() = default;

bool GpuRanker::init() {
    return false;
}

bool GpuRanker::ready() const {
    return false;
}

std::string GpuRanker::status() const {
    return impl_->message;
}

std::vector<float> GpuRanker::poll() {
    return {};
}

bool GpuRanker::submit(GpuState, std::vector<Plan> const &, std::vector<Obstacle> const &) {
    return false;
}

} // namespace agb
'''

OLD_CMAKE = ''' add_library(${PROJECT_NAME} SHARED src/main.cpp src/gpu_ranker.cpp)\n target_include_directories(${PROJECT_NAME} PRIVATE src)\n add_subdirectory($ENV{GEODE_SDK} ${CMAKE_CURRENT_BINARY_DIR}/geode)\n target_link_libraries(${PROJECT_NAME} d3d11 d3dcompiler dxgi)\n'''

NEW_CMAKE = ''' if(ANDROID)\n  add_library(${PROJECT_NAME} SHARED src/main.cpp src/gpu_ranker_stub.cpp)\n else()\n  add_library(${PROJECT_NAME} SHARED src/main.cpp src/gpu_ranker.cpp)\n endif()\n target_include_directories(${PROJECT_NAME} PRIVATE src)\n add_subdirectory($ENV{GEODE_SDK} ${CMAKE_CURRENT_BINARY_DIR}/geode)\n if(WIN32)\n  target_link_libraries(${PROJECT_NAME} d3d11 d3dcompiler dxgi)\n endif()\n'''


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("project")
    args = parser.parse_args()
    root = Path(args.project).resolve()

    cmake_path = root / "CMakeLists.txt"
    cmake = cmake_path.read_text(encoding="utf-8")
    if OLD_CMAKE not in cmake:
        raise SystemExit("unexpected CMakeLists.txt; refusing to modify canonical source")
    cmake_path.write_text(cmake.replace(OLD_CMAKE, NEW_CMAKE, 1), encoding="utf-8")

    mod_path = root / "mod.json"
    data = json.loads(mod_path.read_text(encoding="utf-8"))
    if data.get("gd", {}).get("win") != "2.2081":
        raise SystemExit("unexpected GD target in mod.json")
    data.setdefault("gd", {})["android"] = "2.2081"
    gpu = data.get("settings", {}).get("gpu", {})
    gpu["name"] = "GPU candidate ordering"
    gpu["description"] = (
        "D3D11 asynchronous candidate ordering on Windows. Android keeps the same "
        "CPU-verified planner and falls back to CPU ordering."
    )
    mod_path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    (root / "src" / "gpu_ranker_stub.cpp").write_text(STUB, encoding="utf-8")

    print("Android64 adaptation applied. Solver core files were not modified.")


if __name__ == "__main__":
    main()

