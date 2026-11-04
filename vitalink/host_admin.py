#!/usr/bin/env python3
"""Named, bounded LinuxOnVita host-kernel admin operations.

The existing VitaLink agent runs as root in Alpine and sees the same /proc and
/sys kernel mounts as base LinuxOnVita. No command interpreter or chroot escape
is exposed. Only the driver-supported CPU operating points are selectable.
"""
import os
from pathlib import Path

SYS=Path("/sys/devices/system/cpu")
ALLOWED={111000,166000,222000,333000,444000}

def read(p):
    try:return Path(p).read_text().strip()
    except (OSError,ValueError):return "unavailable"

def policies():
    roots=sorted(SYS.glob("cpu[0-9]*/cpufreq"))
    return [p for p in roots if p.is_dir()]

def cpu_status():
    roots=policies()
    if not roots:return {"code":1,"output":"CPU cpufreq driver not visible in /sys"}
    rows=[]
    for p in roots:
        rows.append("%s: %s kHz, governor=%s, min=%s, max=%s" % (
            p.parent.name,read(p/"scaling_cur_freq"),
            read(p/"scaling_governor"),read(p/"scaling_min_freq"),
            read(p/"scaling_max_freq")))
    if roots:
        rows.append("available frequencies: "+read(roots[0]/"scaling_available_frequencies"))
        rows.append("available governors: "+read(roots[0]/"scaling_available_governors"))
    return {"code":0,"output":"\n".join(rows)}

def safe_write(path,value):
    p=Path(path)
    if not p.is_file() or not os.access(p,os.W_OK):
        raise OSError("CPU sysfs setting unavailable: "+p.name)
    p.write_text(str(value)+"\n")

def set_cpu(profile):
    if profile not in ("max","balanced","ondemand"):
        raise ValueError("Only stock max/balanced/ondemand CPU profiles allowed")
    cores=policies()
    if not cores:
        return {"code":1,"output":"CPU cpufreq driver not mounted"}
    # Require stock operating points from the running kernel, not a guessed clock.
    allowed={int(x) for x in read(cores[0]/"scaling_available_frequencies").split() if x.isdigit()}
    if not {333000,444000}.issubset(allowed):
        return {"code":1,"output":"Unexpected frequency table; no changes applied"}
    governors=set(read(cores[0]/"scaling_available_governors").split())
    if profile=="max":
        if "performance" not in governors:
            return {"code":1,"output":"Performance governor not supported"}
        for p in cores:
            safe_write(p/"scaling_max_freq",444000)
            safe_write(p/"scaling_governor","performance")
    elif profile=="balanced":
        if "userspace" in governors:
            for p in cores:
                safe_write(p/"scaling_max_freq",444000)
                safe_write(p/"scaling_min_freq",111000)
                safe_write(p/"scaling_governor","userspace")
                safe_write(p/"scaling_setspeed",333000)
        elif "ondemand" in governors:
            for p in cores:
                safe_write(p/"scaling_max_freq",444000)
                safe_write(p/"scaling_min_freq",111000)
                safe_write(p/"scaling_governor","ondemand")
                safe_write(p/"scaling_max_freq",333000)
        else:
            return {"code":1,"output":"Neither userspace nor ondemand governor available"}
    else:
        if "ondemand" not in governors:
            return {"code":1,"output":"Ondemand governor not supported"}
        for p in cores:
            safe_write(p/"scaling_max_freq",444000)
            safe_write(p/"scaling_min_freq",111000)
            safe_write(p/"scaling_governor","ondemand")
    result=cpu_status()
    result["output"]="Applied stock CPU profile: "+profile+"\n"+result["output"]
    return result

def snapshot():
    if os.geteuid()!=0:return {"code":1,"output":"VitaLink must run as root"}
    try:
        load=os.getloadavg()
        loadstr="load: %.2f %.2f %.2f" % load
    except OSError:loadstr="load: unknown"
    rows=[
        "kernel: "+read("/proc/version")[:220],
        "uptime: "+read("/proc/uptime"),
        loadstr,
        "memory:\n"+ "\n".join(read("/proc/meminfo").splitlines()[:12]),
        "X11 socket: "+str(Path("/tmp/.X11-unix/X0").exists()),
        "proc available: "+str(Path("/proc/1/stat").is_file()),
        "CPU:\n"+cpu_status()["output"]
    ]
    names={}
    try:
        for p in Path("/proc").iterdir():
            if not p.name.isdigit():continue
            comm=read(p/"comm")
            if comm!="unavailable":names[comm]=names.get(comm,0)+1
    except OSError:pass
    rows.append("processes (names only, no arguments):")
    for name,count in sorted(names.items(),key=lambda z:(-z[1],z[0]))[:35]:
        rows.append(" %s ×%d" %(name[:60],count))
    return {"code":0,"output":"\n".join(rows)[:14000]}

def dispatch(action,args):
    if action=="host_status":
        return snapshot()
    if action=="host_cpu":
        return set_cpu(args.get("profile"))
    raise ValueError("Unknown host admin operation")
