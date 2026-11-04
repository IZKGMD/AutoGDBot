#!/usr/bin/env python3
"""Private root SSH bridge for LinuxOnVita.

VitaLink already runs as root inside Alpine and polls a trusted HTTPS VPS.
A locally generated Ed25519 key authenticates only to 127.0.0.1 on the
base LinuxOnVita system. The key never leaves the Vita.
"""
import os
import shlex
import signal
import subprocess
from pathlib import Path

BASE=Path("/root/vitalink")
KEY=BASE/"host_ed25519"
KNOWN_HOSTS=BASE/"host_known_hosts"

def connected():
    if os.geteuid()!=0:
        raise PermissionError("VitaLink must run as root in Alpine")
    if not KEY.is_file() or KEY.is_symlink():
        raise PermissionError("Root bridge key not installed on Vita")
    if not KEY.stat().st_mode & 0o077 == 0:
        raise PermissionError("Root bridge key permissions must be 0600")
    if not Path("/usr/bin/ssh").is_file():
        raise RuntimeError("Alpine openssh-client is not installed")

def execute(command, timeout=45):
    connected()
    if not isinstance(command,str) or not command.strip() or len(command)>1600 or "\x00" in command:
        raise ValueError("Invalid remote command")
    if type(timeout)!=int or not 1<=timeout<=90:
        raise ValueError("Invalid timeout")
    remote="/bin/sh -lc "+shlex.quote(command)
    argv=["/usr/bin/ssh","-T","-F","/dev/null",
          "-o","BatchMode=yes","-o","IdentitiesOnly=yes",
          "-o","PasswordAuthentication=no",
          "-o","KbdInteractiveAuthentication=no",
          "-o","ConnectTimeout=6",
          "-o","StrictHostKeyChecking=accept-new",
          "-o","UserKnownHostsFile="+str(KNOWN_HOSTS),
          "-o","LogLevel=ERROR",
          "-i",str(KEY),"-p","22","root@127.0.0.1",remote]
    proc=subprocess.Popen(argv,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT,start_new_session=True)
    try:
        out,_=proc.communicate(timeout=timeout)
        return {"code":int(proc.returncode),"output":out[:18000].decode("utf-8","replace")}
    except subprocess.TimeoutExpired:
        try:os.killpg(proc.pid,signal.SIGKILL)
        except ProcessLookupError:pass
        out,_=proc.communicate()
        return {"code":124,"output":out[:18000].decode("utf-8","replace")+"\nTimeout"}

def dispatch(action,args,config):
    if action=="host_ping":
        return execute("id -u; uname -n; test -x /usr/bin/vita-cpu && echo vita-cpu:yes || echo vita-cpu:no",20)
    if action=="host_shell":
        if not config.get("allow_host_shell",False):
            raise PermissionError("Root shell requires local opt-in on PS Vita")
        return execute(args.get("command"),args.get("timeout",45))
    raise ValueError("Unknown root bridge action")
