#!/usr/bin/env python3
"""Adapt a pinned gdsolver checkout without rewriting its physics constants."""
import argparse,json,shutil,subprocess
from pathlib import Path

UPSTREAM='8e8d69ada86a315c71d05cc8b8fe4d369d883aa2'
VERSION='v1.0.0-alpha.1'
PROFILE='AutoGDBot:'+VERSION+':android64:GD2.2081:240Hz:'+UPSTREAM
ROOT=Path(__file__).resolve().parent

def replace(root,path,old,new):
    file=root/path;text=file.read_text()
    if text.count(old)!=1:raise RuntimeError('Patch anchor mismatch: '+path)
    file.write_text(text.replace(old,new))

def prepare(engine,check_revision=True):
    if check_revision:
        actual=subprocess.check_output(['git','-C',str(engine),'rev-parse','HEAD'],text=True).strip()
        if actual!=UPSTREAM:raise RuntimeError('Unexpected engine revision')
    manifest=json.loads((engine/'mod.json').read_text())
    manifest.update(id='autogdbot.realtime',name='AutoGDBot Verified',version=VERSION,
        developers=['AutoGDBot','gdsolver'],description='Android64 experimental solver: native verification, repaired routes, content-scoped replay and bounded verification batches.')
    manifest.pop('links',None);manifest.pop('issues',None)
    manifest['links']={'source':'https://github.com/IZKGMD/AutoGDBot'}
    manifest['settings']['verification-budget-ms']={'type':'float','name':'Verification frame budget (ms)','description':'Time between native verification batches during Solve. A single game update can exceed this budget. Replay uses normal timing.','default':6.0,'min':2.0,'max':12.0}
    (engine/'mod.json').write_text(json.dumps(manifest,indent=2)+'\n')
    shutil.copy2(ROOT/'cache_identity.hpp',engine/'src/mod/autogd_cache_identity.hpp')
    shutil.copy2(ROOT/'android_libcpp_compat.cpp',engine/'src/android_libcpp_compat.cpp')
    (engine/'src/mod/autogd_cache_runtime.hpp').write_text('''#pragma once
#include "mod/config.hpp"
#include "mod/autogd_cache_identity.hpp"
namespace p1 {
inline std::map<int,std::string> g_autogdLevelKeys;
inline bool hasCacheIdentity(int id) { return g_autogdLevelKeys.count(id) != 0; }
inline void bindCacheIdentity(int id,int kind,const std::string& data) {
    const std::string key=autogd::levelKey(data,id,kind,"'''+PROFILE+'''");
    if(key.empty())g_autogdLevelKeys.erase(id);else g_autogdLevelKeys[id]=key;
}
inline std::string cachedPath(int id,bool coins,const char* category) {
    const auto it=g_autogdLevelKeys.find(id);
    if(it==g_autogdLevelKeys.end())return std::string(DATA_DIR)+"/identity-unavailable";
    return std::string(DATA_DIR)+"/"+category+"_"+it->second+(coins?"_coins.txt":"_plain.txt");
}
}
''')
    replace(engine,'src/mod/session.hpp','#include "mod/plan_io.hpp"','#include "mod/plan_io.hpp"\n#include "mod/autogd_cache_runtime.hpp"')
    replace(engine,'src/mod/session.hpp','return std::string(DATA_DIR) + "/solution_lv" + std::to_string(levelId)\n           + (coins ? "_coins.txt" : "_dp.txt");','return cachedPath(levelId,coins,"solution");')
    replace(engine,'src/mod/session.hpp','inline bool uiConfigureSession(int levelId) {','inline bool uiConfigureSession(int levelId) {\n    if(!hasCacheIdentity(levelId)){log::warn("AutoGDBot: level identity unavailable; automatic session refused"); return false;}')
    replace(engine,'src/mod/session.hpp','g_cfg.frameBudgetMs = 100.0;','g_cfg.frameBudgetMs = autogd::frameBudget(Mod::get()->getSettingValue<double>("verification-budget-ms"));')
    replace(engine,'src/mod/repair.hpp','constexpr const char* kThreads = "8";','constexpr const char* kThreads = "2"; // Mobile profile: leave CPU headroom for GD and touch input.')
    # Bind the decompressed level, before the popup considers any stored solution.
    replace(engine,'src/mod/hooks_playmenu.cpp','if (s.empty()) {','bindCacheIdentity(level->m_levelID.value(), static_cast<int>(level->m_levelType), s);\n    if (s.empty()) {')
    replace(engine,'src/mod/hooks_playmenu.cpp','this->setTitle("GD Solver");','this->setTitle("AutoGDBot Verified");')
    old='''char name[128];
                snprintf(name, sizeof(name),
                         g_cfg.coinFiles ? "%s/solution_lv%d_coins.txt"
                                         : "%s/solution_lv%d_dp.txt",
                         DATA_DIR, g_cfg.levelId);'''
    replace(engine,'src/mod/hooks_playlayer.cpp',old,'const std::string name = uiSolutionPath(g_cfg.levelId,g_cfg.coinFiles);')
    # std::string avoids truncation with Android's long app data path and the 64-char hash.
    replace(engine,'src/mod/itermap.hpp','#pragma once','#pragma once\n#include "mod/autogd_cache_runtime.hpp"')
    old='''char name[64];
    // A coin session's map goes beside its coin solution (Config::coinFiles), so replaying the
    // plain solution never draws the coin run's rounds, or the other way round.
    snprintf(name, sizeof(name),
             g_cfg.coinFiles ? "/itermap_lv%d_coins.txt" : "/itermap_lv%d.txt", levelId);
    return std::string(DATA_DIR) + name;'''
    replace(engine,'src/mod/itermap.hpp',old,'return cachedPath(levelId,g_cfg.coinFiles,"itermap");')
    # Keep authorship and permission alongside the produced mod package.
    shutil.copy2(engine/'LICENSE',engine/'GDSOLVER-LICENSE.txt')
    (engine/'about.md').write_text((ROOT/'ABOUT.md').read_text())
    (engine/'BUILD_PROVENANCE.json').write_text(json.dumps({'version':VERSION,'engine_commit':UPSTREAM,'target':'Android64','game':'2.2081','geode':'5.10.1','gameplay_verified':False,'fingerdash_verified':False,'completion_rate_measured':False,'fps_measured':False},indent=2)+'\n')
    return engine

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('engine',type=Path);args=parser.parse_args();prepare(args.engine)
