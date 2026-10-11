#!/usr/bin/env python3
"""Static beta.3 package checks; cannot validate hook execution on a phone."""
import argparse
import hashlib
import json
import pathlib
import struct
import subprocess
import tempfile
import zipfile


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('package', type=pathlib.Path)
    p.add_argument('--source-manifest', type=pathlib.Path)
    args = p.parse_args()
    sha = hashlib.sha256(args.package.read_bytes()).hexdigest()
    assert sha == '58bf8b266afba496b643fa01aa57f409794df96f36d860777cad9429836569e2'
    with zipfile.ZipFile(args.package) as z:
        assert z.testzip() is None
        manifest = json.loads(z.read('mod.json'))
        assert manifest['id'] == 'autogdbot.realtime'
        assert manifest['version'] == 'v1.0.0-beta.3'
        assert manifest['geode'] == '5.10.1'
        assert manifest['gd']['android'] == '2.2081'
        for key in ['enabled', 'show-hitboxes', 'show-trace']:
            assert manifest['settings'][key]['default'] is True
        assert manifest['settings']['trace-fps']['default'] == 120
        if args.source_manifest:
            assert json.loads(args.source_manifest.read_text()) == manifest
        assert len(z.namelist()) == len(set(z.namelist()))
        assert 'resources/autogdbot.realtime/gdsolver-LICENSE' in z.namelist()
        native = z.read('autogdbot.realtime.android64.so')
        assert native[:6] == b'\x7fELF\x02\x01'
        assert struct.unpack_from('<HH', native, 16) == (3, 183)
        phoff = struct.unpack_from('<Q', native, 32)[0]
        phentsize, phnum = struct.unpack_from('<HH', native, 54)
        loads = []
        for i in range(phnum):
            fields = struct.unpack_from('<IIQQQQQQ', native, phoff + i * phentsize)
            typ, flags, offset, vaddr, _, _, _, align = fields
            if typ == 1:
                assert align >= 0x4000 and (vaddr - offset) % 0x4000 == 0
                assert flags & 3 != 3  # no writable executable segment
                loads.append(align)
        assert loads
        with tempfile.TemporaryDirectory() as temp:
            so = pathlib.Path(temp) / 'mod.so'
            so.write_bytes(native)
            symbols = subprocess.check_output(['readelf', '--wide', '--dyn-syms', str(so)], text=True)
            entries = [s for s in symbols.splitlines() if s.strip().endswith('geodeImplicitEntry')]
            assert len(entries) == 1 and 'UND' not in entries[0]
            assert not any('UND' in s and '__hash_memory' in s for s in symbols.splitlines())
            dynamic = subprocess.check_output(['readelf', '-d', str(so)], text=True)
            assert '[Geode.android64.so]' in dynamic and '[libcocos2dcpp.so]' in dynamic
    print(json.dumps({'sha256': sha, 'package_bytes': args.package.stat().st_size,
                      'native_bytes': len(native), 'load_alignments': loads,
                      'static_package_validation': 'passed'}, indent=2))


if __name__ == '__main__':
    main()
