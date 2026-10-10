# Building this experimental generation

The project root keeps the previous controller for reference. Build **newgen/engine**, not the old root project. The workflow checks out gdsolver at `8e8d69ada86a315c71d05cc8b8fe4d369d883aa2` and applies `newgen/prepare.py` once to a clean checkout.

Requirements: Git, Python 3, CMake, Ninja, Geode CLI 3.9.0, Geode SDK 5.10.1 and Android NDK 29.0.14206865. Install the SDK's Android64 binaries with `geode sdk install-binaries --platform android64 --version 5.10.1`. `GEODE_SDK` must point to that SDK. Use the pinned bindings commit `f8aec5d1f3df76d840dfd2609f02cef30dc6efdf` for repeatability.

```sh
git clone https://github.com/gdsolver/gdsolver newgen/engine
git -C newgen/engine checkout 8e8d69ada86a315c71d05cc8b8fe4d369d883aa2
python3 newgen/prepare.py newgen/engine
cmake -S newgen/engine -B newgen/engine/build -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
  -DANDROID_STL=c++_shared -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-23 \
  -DGEODE_TARGET_PLATFORM=Android64 -DGEODE_DONT_INSTALL_MODS=ON \
  -DGDSOLVER_BUILD_DP=OFF -DCMAKE_BUILD_TYPE=Release
cmake --build newgen/engine/build --parallel 2
python3 newgen/export_package.py newgen/engine/build/autogdbot.realtime.geode newgen/engine dist-verified
```

`ANDROID_NDK_ROOT` must be the actual NDK directory. The compiler toolchain comes from the NDK; a host desktop compiler cannot produce this Android package.

The local builder validates the exact engine, SDK and bindings commits, the NDK and CLI versions, and coverage of all 162 snapshot state members. It runs the host acceptance checks, applies the profile once, and builds/packages the actual Android shared library. Start with a clean pinned engine checkout:

```sh
python3 newgen/build_android.py \
  --engine newgen/engine --sdk "$GEODE_SDK" --ndk "$ANDROID_NDK_ROOT" \
  --bindings "$GEODE_BINDINGS_REPO_PATH" --cli "$(command -v geode)" \
  --output dist-verified
```

Without a configured CLI game profile, download the official `geode-v5.10.1-android64.zip` release and place its `Geode.android64.so` in `bin/5.10.1/` of the SDK checkout. Packaging itself does not need a game profile. Leave `GEODE_DONT_UPDATE_INDEX=OFF`: CLI 3.9.0 does not accept `--dont-update-index` for `project check`. The local builder strips release debug information at link time and never installs into a game automatically.

The current code generator exposes renamed camera points as anonymous unions, so Clang deletes `GJGameState`'s implicit copy assignment. The Android adaptation assigns every canonical field explicitly from the pinned bindings; containers still deep-copy their own nodes. It does not copy container storage with `memcpy` or change physics constants. `check_snapshot_schema.py` rejects missing, duplicate or reordered state members.

GitHub Actions uses the same process and records the package bytes and SHA-256 on a separate build-output branch after successful compilation. A failed run with no steps and no artifact has **not** built a `.geode`.

Standalone acceptance checks:

```sh
g++ -std=c++20 -O2 -Wall -Wextra -Werror newgen/cache_tests.cpp -o cache-tests
./cache-tests
cmake -S newgen/engine -B build-core -DGDSOLVER_BUILD_MOD=OFF -DGDSOLVER_BUILD_DP=ON
cmake --build build-core --target dptest --parallel 2
./build-core/dp/dptest
```

The pinned upstream test expected a Move trigger to spawn a Toggle in its target group. That contradicts the measured Move-does-not-spawn rule already implemented in `dp/src/dp/triggers.hpp`. The adaptation corrects that assertion and adds separate checks for object movement and delayed on-events. It does not change the game's trigger or physics model to satisfy a stale fixture.

The native mod still requires an Android GD session to verify loading, cold solutions, touch controls, gameplay and frame-time percentiles. No upstream Windows result should be reported as a result for this package.
