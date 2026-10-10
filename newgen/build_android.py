#!/usr/bin/env python3
"""Build the adapted Android mod with an existing, pinned native toolchain."""
import argparse
import os
import shutil
import subprocess
from pathlib import Path

from export_package import export
from prepare import UPSTREAM, prepare
from check_snapshot_schema import check

BINDINGS = 'f8aec5d1f3df76d840dfd2609f02cef30dc6efdf'
SDK = '7e41336f68660b990644f5c3450b6da6ae944560'
NDK = '29.0.14206865'


def run(args, **kwargs):
    subprocess.run([str(arg) for arg in args], check=True, **kwargs)


def revision(path):
    return subprocess.check_output(['git', '-C', str(path), 'rev-parse', 'HEAD'], text=True).strip()


def build(args):
    engine, sdk, ndk, bindings, cli = [Path(p).resolve() for p in
        (args.engine, args.sdk, args.ndk, args.bindings, args.cli)]
    for path, expected in ((engine, UPSTREAM), (sdk, SDK), (bindings, BINDINGS)):
        if revision(path) != expected:
            raise RuntimeError('Unexpected revision: ' + str(path))
    if 'Pkg.Revision = ' + NDK not in (ndk / 'source.properties').read_text():
        raise RuntimeError('Android NDK ' + NDK + ' is required')
    version = subprocess.check_output([str(cli), '--version'], text=True).strip()
    if version != 'geode 3.9.0':
        raise RuntimeError('Geode CLI 3.9.0 is required; found ' + version)
    if not (sdk / 'bin/5.10.1/Geode.android64.so').is_file():
        raise RuntimeError('Install the Geode 5.10.1 Android64 SDK binary first')
    cmake = shutil.which('cmake')
    compiler = shutil.which('g++')
    if not cmake or not compiler or not shutil.which('ninja'):
        raise RuntimeError('CMake, Ninja and a host g++ compiler are required')
    output = Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    # Never apply the anchors twice or silently build a partially patched checkout.
    dirty = subprocess.check_output(['git', '-C', str(engine), 'status', '--porcelain'], text=True)
    if dirty.strip():
        raise RuntimeError('Use a clean pinned engine checkout; adaptation is applied once')
    check(bindings)
    prepare(engine)
    host_build = output / 'core'
    native_build = output / 'android64'
    run([compiler, '-std=c++20', '-O2', '-Wall', '-Wextra', '-Werror',
         Path(__file__).with_name('cache_tests.cpp'), '-o', output / 'cache-tests'])
    run([output / 'cache-tests'])
    run([cmake, '-S', engine, '-B', host_build, '-G', 'Ninja',
         '-DGDSOLVER_BUILD_MOD=OFF', '-DGDSOLVER_BUILD_DP=ON', '-DCMAKE_BUILD_TYPE=Release'])
    run([cmake, '--build', host_build, '--target', 'dptest', '--parallel', '2'])
    run([host_build / 'dp/dptest'])
    env = os.environ.copy()
    env.update(GEODE_SDK=str(sdk), GEODE_BINDINGS_REPO_PATH=str(bindings))
    run([cmake, '-S', engine, '-B', native_build, '-G', 'Ninja',
         '-DCMAKE_TOOLCHAIN_FILE=' + str(ndk / 'build/cmake/android.toolchain.cmake'),
         '-DANDROID_STL=c++_shared', '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-23',
         '-DGEODE_TARGET_PLATFORM=Android64', '-DGEODE_DONT_INSTALL_MODS=ON',
         '-DGEODE_DONT_UPDATE_INDEX=OFF', '-DGEODE_CLI=' + str(cli),
         '-DGDSOLVER_BUILD_DP=OFF', '-DCMAKE_BUILD_TYPE=Release',
         '-DCMAKE_SHARED_LINKER_FLAGS_RELEASE=-Wl,--strip-debug',
         '-DCPM_SOURCE_CACHE=' + str(output / 'dependencies')], env=env)
    run([cmake, '--build', native_build, '--parallel', '2'], env=env)
    result = export(native_build / 'autogdbot.realtime.geode', engine, output)
    print('Installable Android64 mod: ' + str(result))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('engine', 'sdk', 'ndk', 'bindings', 'cli', 'output'):
        parser.add_argument('--' + key, required=True)
    build(parser.parse_args())
