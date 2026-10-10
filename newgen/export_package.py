#!/usr/bin/env python3
"""Validate and retain the actual Android build, with byte-for-byte retrieval chunks."""
import argparse,base64,hashlib,json,os,struct,subprocess,tempfile,zipfile
from pathlib import Path

def export(package,engine,destination):
    destination.mkdir(parents=True,exist_ok=True)
    output=destination/'AutoGDBot-Verified-v1.0.0-alpha.1-Android64.geode'
    with zipfile.ZipFile(package) as source:
        if source.testzip() is not None:raise RuntimeError('Package CRC mismatch')
        manifest=json.loads(source.read('mod.json'))
        if manifest['id']!='autogdbot.realtime' or manifest['version']!='v1.0.0-alpha.1':raise RuntimeError('Wrong mod identity')
        if manifest['gd']['android']!='2.2081':raise RuntimeError('Wrong game target')
        binary=source.read('autogdbot.realtime.android64.so')
        if binary[:5]!=b'\x7fELF\x02' or binary[5]!=1 or struct.unpack_from('<H',binary,18)[0]!=183:raise RuntimeError('Not little-endian Android AArch64 ELF')
        if struct.unpack_from('<H',binary,16)[0]!=3:raise RuntimeError('Native payload is not a shared library')
        if b'geodeImplicitEntry' not in binary:raise RuntimeError('Geode entrypoint missing')
        with tempfile.NamedTemporaryFile(suffix='.so') as native:
            native.write(binary);native.flush()
            symbols=subprocess.check_output(['readelf','--dyn-syms','--wide',native.name],text=True)
        if not any('geodeImplicitEntry' in line and ' UND ' not in line for line in symbols.splitlines()):raise RuntimeError('Geode entrypoint is not exported')
        if any('_ZNSt6__ndk113__hash_memoryEPKvm' in line and ' UND ' in line for line in symbols.splitlines()):raise RuntimeError('Unresolved private libc++ hash helper')
        if any('_ZN2p1' in line and ' UND ' in line for line in symbols.splitlines()):raise RuntimeError('Unresolved internal controller symbol')
        # Rewrite instead of appending duplicate ZIP names. Original entries retain their metadata.
        provenance=json.loads((engine/'BUILD_PROVENANCE.json').read_text())
        provenance.update(native_compiled=True,binary_sha256=hashlib.sha256(binary).hexdigest(),
            source_commit=os.environ.get('GITHUB_SHA','local'),sdk_commit='7e41336f68660b990644f5c3450b6da6ae944560',
            bindings_commit='f8aec5d1f3df76d840dfd2609f02cef30dc6efdf',ndk='29.0.14206865',cli='3.9.0',
            snapshot_assignment='all 162 canonical game-state members; no raw container copies')
        profile_files=['prepare.py','cache_identity.hpp','android_libcpp_compat.cpp','game_state_members.inc']
        root=Path(__file__).resolve().parent
        provenance['adapter_sha256']=hashlib.sha256(b''.join((root/name).read_bytes() for name in profile_files)).hexdigest()
        extras={'GDSOLVER-LICENSE.txt':(engine/'LICENSE').read_bytes(),'BUILD_PROVENANCE.json':(json.dumps(provenance,indent=2)+'\n').encode()}
        with zipfile.ZipFile(output,'w') as target:
            for info in source.infolist():
                if info.filename not in extras:target.writestr(info,source.read(info.filename))
            for name,data in extras.items():target.writestr(name,data,compress_type=zipfile.ZIP_DEFLATED)
    raw=output.read_bytes();encoded=base64.b64encode(raw).decode()
    chunks=[]
    for offset in range(0,len(encoded),80000):
        name=f'package.b64.part{len(chunks):03d}';part=encoded[offset:offset+80000]
        (destination/name).write_text(part+'\n');chunks.append({'name':name,'length':len(part)})
    result={'filename':output.name,'sha256':hashlib.sha256(raw).hexdigest(),'size':len(raw),'chunks':chunks,'source_commit':os.environ.get('GITHUB_SHA','local'),'upstream_commit':'8e8d69ada86a315c71d05cc8b8fe4d369d883aa2','architecture':'Android64','gd':'2.2081','gameplay_verified':False,'fps_measured':False}
    (destination/'package.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='chunks'}))
    return output

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('package',type=Path);p.add_argument('engine',type=Path);p.add_argument('destination',type=Path);a=p.parse_args();export(a.package,a.engine,a.destination)
