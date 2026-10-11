#!/usr/bin/env python3
"""Reproduce host tests. This does not install or run Geometry Dash."""
import argparse
import json
import os
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def run(args, **kwargs):
    subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sanitize', action='store_true')
    parser.add_argument('--jobs', type=int, default=2)
    args = parser.parse_args()
    name = 'sanitize' if args.sanitize else 'release'
    build = ROOT / ('build-' + name)
    flags = ['cmake', '-S', str(ROOT), '-B', str(build), '-G', 'Ninja',
             '-DAUTOGDBOT_BUILD_MOD=OFF', '-DAUTOGDBOT_BUILD_TESTS=ON',
             '-DCMAKE_BUILD_TYPE=' + ('Debug' if args.sanitize else 'Release')]
    if args.sanitize:
        flags += ['-DCMAKE_CXX_FLAGS=-O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer -fno-pie',
                  '-DCMAKE_EXE_LINKER_FLAGS=-fsanitize=address,undefined -no-pie']
    run(flags)
    run(['cmake', '--build', str(build), '-j', str(max(1, args.jobs))])
    # Some hosted workspaces clear execute bits when synchronizing binaries.
    if os.name == 'posix':
        for file in build.glob('*_tests'):
            file.chmod(file.stat().st_mode | 0o111)
    env = os.environ.copy()
    if args.sanitize:
        env.update(ASAN_OPTIONS='detect_leaks=1:halt_on_error=1',
                   UBSAN_OPTIONS='halt_on_error=1:print_stacktrace=1')
    run(['ctest', '--test-dir', str(build), '--output-on-failure', '-V',
         '--output-log', str(ROOT / ('validation-' + name + '.log')),
         '--output-junit', str(ROOT / ('validation-' + name + '.xml'))], env=env)
    groups = []
    for line in (ROOT / ('validation-' + name + '.log')).read_text().splitlines():
        start = line.find('{"group":')
        if start >= 0:
            groups.append(json.loads(line[start:]))
    (ROOT / ('validation-' + name + '.json')).write_text(json.dumps({
        'configuration': name,
        'scope': 'independent host fixtures, not native GD gameplay',
        'distinct_matrix_cases': sum(g.get('cases', 0) for g in groups),
        'groups': groups,
    }, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as exc:
        sys.exit(exc.returncode)
