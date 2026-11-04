#!/usr/bin/env python3
"""LinuxOnVita outbound HTTPS agent. Default: diagnostics only, no remote shell."""
import base64,fcntl,json,os,re,ssl,subprocess,time,tempfile,shutil
from pathlib import Path
from urllib.request import Request,build_opener,HTTPSHandler,HTTPRedirectHandler

HOME=Path("/root/vitalink")
class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):raise RuntimeError("Redirect forbidden")
def api(conf,path,data=None):
    url=conf["url"].rstrip("/")+path
    if not url.startswith("https://"):raise RuntimeError("HTTPS required")
    body=json.dumps(data).encode() if data is not None else None
    req=Request(url,body,{"Authorization":"Bearer "+conf["token"],"Content-Type":"application/json"})
    opener=build_opener(NoRedirect(),HTTPSHandler(context=ssl.create_default_context()))
    with opener.open(req,timeout=20) as r:return json.load(r)
def cmd(argv,seconds=60,env=None):
    try:
        out=subprocess.run(argv,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,
                           errors="replace",timeout=seconds,env=env)
        return {"code":out.returncode,"output":out.stdout[:27000]}
    except subprocess.TimeoutExpired:return {"code":124,"output":"Command timeout"}
    except Exception as e:return {"code":127,"output":str(e)}
def filename(value):
    if not isinstance(value,str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,70}",value) or value in (".",".."):
        raise ValueError("Invalid filename")
    return value
def capture_jpeg():
    """Capture one X11 frame after the local user has opted in.
    Never writes frames to logs, GitHub, or permanent storage.
    """
    if not shutil.which("scrot"):
        raise RuntimeError("Install Alpine package scrot before viewing screen")
    env=os.environ.copy()
    env["DISPLAY"]=env.get("DISPLAY") or ":0"
    with tempfile.TemporaryDirectory(prefix="vitalink-screen-") as temp:
        target=Path(temp)/"frame.jpg"
        finished=subprocess.run(
            ["scrot","-z","-q","25",str(target)],
            stdout=subprocess.PIPE,stderr=subprocess.PIPE,
            timeout=12,env=env)
        if finished.returncode!=0:
            raise RuntimeError("scrot cannot capture X11 (IceWM may not be running)")
        data=target.read_bytes()
        if not 500<=len(data)<=160000 or not data.startswith(b"\xff\xd8\xff") or not data.endswith(b"\xff\xd9"):
            raise RuntimeError("Invalid or oversized JPEG frame")
        return base64.b64encode(data).decode("ascii")

def job_run(job,config):
    action=job["action"];args=job.get("args",{})
    if action=="diagnose":
        info=[]
        for cmdline in (["uname","-a"],["uptime"],["free","-m"],["df","-h"],
                        ["sh","-c","command -v vita-cpu && vita-cpu status || true"],
                        ["sh","-c","command -v vita-swap && vita-swap status || true"]):
            r=cmd(cmdline,12);info.append("$ "+" ".join(cmdline)+"\n"+r["output"])
        return {"code":0,"output":"\n".join(info)[:27000]}
    if action in ("search","install"):
        names=args.get("names",[])
        if not isinstance(names,list) or not 1<=len(names)<=8 or any(not isinstance(x,str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_+.-]{0,60}",x) for x in names):
            raise ValueError("Invalid package names")
        if action=="install" and not config.get("allow_install",False):raise PermissionError("Install needs local opt-in")
        return cmd(["apk","search","-v"]+names if action=="search" else ["apk","add"]+names,120)
    if action=="game":
        name=args.get("name")
        if name not in ("neon_drift.py","orbit_defender.py","aurora_visuals.py","panda3d_probe.py"):raise ValueError("Unknown game")
        file=Path("/root/vita-games")/name
        if not file.is_file():raise FileNotFoundError(name)
        env=os.environ.copy();env["DISPLAY"]=":0";env["SDL_VIDEODRIVER"]="x11";env["SDL_AUDIODRIVER"]="dummy"
        with (HOME/"game.log").open("a") as output:
            process=subprocess.Popen(["python3",str(file)],stdin=subprocess.DEVNULL,stdout=output,stderr=subprocess.STDOUT,
                                    start_new_session=True,env=env)
        return {"code":0,"output":"Started PID "+str(process.pid)}
    if action=="upload":
        name=filename(args.get("name"))
        data=base64.b64decode(args.get("data",""),validate=True)
        if len(data)>50000 or not data:raise ValueError("Upload limit 50 KB")
        target=HOME/"inbox"/name;target.parent.mkdir(exist_ok=True)
        if target.is_symlink():raise ValueError("Symlink forbidden")
        target.write_bytes(data);target.chmod(0o600)
        return {"code":0,"output":"Saved "+name}
    if action=="download":
        name=filename(args.get("name"));target=HOME/"outbox"/name
        if target.is_symlink() or not target.is_file() or target.stat().st_size>15000:raise ValueError("Not found or too large")
        return {"code":0,"name":name,"data":base64.b64encode(target.read_bytes()).decode()}
    if action=="shell":
        if not config.get("allow_shell",False):raise PermissionError("Shell needs local opt-in")
        line=args.get("command","")
        if not isinstance(line,str) or not line.strip() or len(line)>1800:raise ValueError("Invalid shell command")
        return cmd(["/bin/sh","-c",line],min(120,max(1,int(args.get("timeout",50)))))
    raise ValueError("Unknown action")
def main():
    config=json.loads((HOME/"agent.json").read_text())
    if not config["url"].startswith("https://") or len(config.get("token",""))<32:raise RuntimeError("Bad bridge config")
    HOME.mkdir(parents=True,exist_ok=True)
    lock=(HOME/"agent.lock").open("a+")
    try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except BlockingIOError:raise SystemExit("Agent already running")
    while True:
        try:
            job=api(config,"/v1/next").get("job")
            if job:
                try:res=job_run(job,config)
                except Exception as e:res={"code":1,"output":type(e).__name__+": "+str(e)}
                api(config,"/v1/result",{"id":job["id"],"result":res})
                print("Completed "+job["action"]+" "+job["id"][:8],flush=True)
        except Exception as e:print(type(e).__name__+": "+str(e),flush=True)
        # Only capture during an explicitly started, short-lived viewer session.
        # The flag must be enabled locally on the Vita first.
        if config.get("allow_screen",False):
            try:
                need=api(config,"/v1/screen/need")
                if need.get("capture"):
                    jpeg=capture_jpeg()
                    api(config,"/v1/screen/frame",{"jpeg":jpeg})
            except Exception as e:
                print("Screen capture: "+type(e).__name__+": "+str(e),flush=True)
        time.sleep(3)
if __name__=="__main__":main()
