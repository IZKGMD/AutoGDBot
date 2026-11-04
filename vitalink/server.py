#!/usr/bin/env python3
"""VitaLink: HTTPS reverse-proxied, authenticated job queue on VPS loopback."""
import json, sqlite3, threading, time, secrets, hmac, uuid, argparse, base64
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path


class GitHubOIDCVerifier:
    """Verify signed tokens from exactly one permitted GitHub Actions workflow."""
    ISSUER = "https://token.actions.githubusercontent.com"
    AUDIENCE = "vitalink.muchogdps.space"
    REPO = "IZKGMD/AutoGDBot"
    REPO_ID = "1407579678"
    OWNER_ID = "112145870"
    REF = "refs/heads/vitabridge-android"
    WORKFLOW = ("IZKGMD/AutoGDBot/.github/workflows/"
                "vitalink-requests.yml@refs/heads/vitabridge-android")
    JWKS = "https://token.actions.githubusercontent.com/.well-known/jwks"

    def __init__(self):
        import jwt
        self.jwt = jwt
        # Ubuntu 22.04 ships PyJWT 2.3.0. Its PyJWKClient only supports
        # (uri, cache_keys, max_cached_keys), not cache_jwk_set/lifespan.
        # cache_keys=True avoids repeated GitHub JWKS requests during polling,
        # while a new key id is still fetched as needed.
        self.keys = jwt.PyJWKClient(self.JWKS, cache_keys=True)

    def verify(self, token):
        if not isinstance(token,str) or len(token)>8192 or token.count(".")!=2:
            return False
        try:
            header=self.jwt.get_unverified_header(token)
            if header.get("alg")!="RS256" or not isinstance(header.get("kid"),str):
                return False
            key=self.keys.get_signing_key_from_jwt(token).key
            claims=self.jwt.decode(
                token,key,algorithms=["RS256"],
                audience=self.AUDIENCE,issuer=self.ISSUER,
                leeway=30,options={"require":["exp","iat","iss","aud","sub"]})
            return (
                claims.get("sub") in (
                    "repo:"+self.REPO+":ref:"+self.REF,
                    "repo:IZKGMD@"+self.OWNER_ID+"/AutoGDBot@"+self.REPO_ID
                    +":ref:"+self.REF)
                and claims.get("repository")==self.REPO
                and str(claims.get("repository_id"))==self.REPO_ID
                and claims.get("ref")==self.REF
                and claims.get("workflow_ref")==self.WORKFLOW
                and claims.get("event_name") in ("push","workflow_dispatch")
                and 0<int(claims["exp"])-int(claims["iat"])<=600)
        except Exception:
            return False

class Store:
    def __init__(self, config):
        self.config=config; self.lock=threading.RLock()
        self.github_oidc=(GitHubOIDCVerifier() if config.get("enable_github_oidc",False) else None)
        # Screen data is ephemeral RAM-only. No screenshots in SQLite or logs.
        self.screen_frame=None
        self.screen_frame_at=0.0
        self.screen_seq=0
        self.screen_active_until=0.0
        self.screen_last_capture_request=0.0
        self.db=sqlite3.connect(config["database"],check_same_thread=False)
        self.db.row_factory=sqlite3.Row
        with self.db:
            self.db.execute("CREATE TABLE IF NOT EXISTS jobs (id TEXT PRIMARY KEY, action TEXT, args TEXT, state TEXT, created INTEGER, result TEXT)")
    def screen_start(self):
        with self.lock:
            now=time.time()
            if now>=self.screen_active_until:
                self.screen_frame=None
                self.screen_frame_at=0.0
            self.screen_active_until=now+18.0
            return {"ok":True,"active":True,"expires_in":18}

    def screen_stop(self):
        with self.lock:
            self.screen_active_until=0.0
            self.screen_frame=None
            self.screen_frame_at=0.0
            return {"ok":True,"active":False}

    def screen_need(self):
        with self.lock:
            now=time.time()
            due=(now<self.screen_active_until
                 and now-self.screen_last_capture_request>=2.5)
            if due:
                self.screen_last_capture_request=now
            return {"capture":due,"active":now<self.screen_active_until}

    def screen_publish(self,body):
        encoded=body.get("jpeg")
        if not isinstance(encoded,str) or len(encoded)>230000:
            raise ValueError("Invalid JPEG payload")
        try:
            raw=base64.b64decode(encoded,validate=True)
        except Exception:
            raise ValueError("Invalid base64 JPEG")
        if not (500<=len(raw)<=160000 and raw[:3]==b"\xff\xd8\xff" and raw[-2:]==b"\xff\xd9"):
            raise ValueError("JPEG invalid or too large")
        with self.lock:
            if time.time()>=self.screen_active_until:
                raise ValueError("Screen session expired")
            self.screen_seq+=1
            self.screen_frame=encoded
            self.screen_frame_at=time.time()
            return {"ok":True,"seq":self.screen_seq}

    def screen_latest(self):
        with self.lock:
            now=time.time()
            if now>=self.screen_active_until or self.screen_frame is None or now-self.screen_frame_at>15:
                return {"available":False,"seq":self.screen_seq}
            return {"available":True,"seq":self.screen_seq,
                    "age_seconds":round(now-self.screen_frame_at,1),
                    "jpeg":self.screen_frame}

    def add(self, body):
        action=body.get("action"); args=body.get("args",{})
        if action not in ("diagnose","search","install","game","upload","download","shell","host_status","host_cpu","host_ping","host_shell") or not isinstance(args,dict):
            raise ValueError("Unknown action or invalid args")
        if action in ("host_status","host_cpu") and not self.config.get("enable_host_admin",False):
            raise ValueError("Host admin disabled by VPS policy")
        if action in ("host_ping","host_shell") and not self.config.get("enable_host_shell",False):
            raise ValueError("Root bridge disabled by VPS policy")
        if action=="host_ping" and args:
            raise ValueError("Host ping does not accept parameters")
        if action=="host_shell":
            command=args.get("command")
            timeout=args.get("timeout",45)
            if (not isinstance(command,str) or not command.strip()
                    or len(command)>1600 or "\x00" in command or
                    type(timeout)!=int or not 1<=timeout<=90 or
                    set(args)-{"command","timeout"}):
                raise ValueError("Invalid root command or timeout")
        if action=="host_cpu" and (args.get("profile") not in ("max","balanced","ondemand") or len(args)!=1):
            raise ValueError("Invalid stock CPU profile")
        if action=="host_status" and args:
            raise ValueError("Host status does not accept parameters")
        if action=="shell" and not self.config.get("enable_shell",False):
            raise ValueError("Shell disabled by VPS policy")
        encoded=json.dumps(args,ensure_ascii=False)
        if len(encoded)>100000: raise ValueError("Request too large")
        ident=uuid.uuid4().hex
        with self.lock,self.db:
            self.db.execute("INSERT INTO jobs VALUES (?,?,?,?,?,?)",(ident,action,encoded,"queued",int(time.time()),None))
            self.db.execute("DELETE FROM jobs WHERE created<?",(int(time.time())-7*86400,))
        return ident
    def take(self):
        with self.lock,self.db:
            row=self.db.execute("SELECT * FROM jobs WHERE state='queued' ORDER BY created LIMIT 1").fetchone()
            if row is None:return None
            self.db.execute("UPDATE jobs SET state='running' WHERE id=?",(row["id"],))
        return {"id":row["id"],"action":row["action"],"args":json.loads(row["args"])}
    def finish(self,body):
        ident=body.get("id"); result=body.get("result")
        if not isinstance(ident,str) or not isinstance(result,dict):raise ValueError("Invalid result")
        result=json.dumps(result,ensure_ascii=False)
        if len(result)>46000:raise ValueError("Result too large")
        with self.lock,self.db:
            row=self.db.execute("SELECT state FROM jobs WHERE id=?",(ident,)).fetchone()
            if not row or row["state"]!="running":raise ValueError("Job not running")
            self.db.execute("UPDATE jobs SET state='done', result=? WHERE id=?",(result,ident))
    def get(self,ident):
        with self.lock:
            row=self.db.execute("SELECT * FROM jobs WHERE id=?",(ident,)).fetchone()
        return None if not row else {"id":row["id"],"action":row["action"],"state":row["state"],"result":json.loads(row["result"]) if row["result"] else None}

def make_handler(store):
    class Handler(BaseHTTPRequestHandler):
        server_version="VitaLink/1"
        sys_version=""
        def log_message(self,*args):pass
        def send(self,code,body):
            raw=json.dumps(body,ensure_ascii=False).encode()
            self.send_response(code);self.send_header("Content-Type","application/json")
            self.send_header("Cache-Control","no-store")
            self.send_header("Content-Length",str(len(raw)))
            self.end_headers();self.wfile.write(raw)
        def authorized(self,role):
            authorization=self.headers.get("Authorization","")
            if hmac.compare_digest(authorization,"Bearer "+store.config[role+"_token"]):
                return True
            if role=="operator" and store.github_oidc and authorization.startswith("Bearer "):
                return store.github_oidc.verify(authorization[7:])
            return False
        def viewer_auth(self):
            token=store.config.get("screen_view_token","")
            authorization=self.headers.get("Authorization","")
            return (isinstance(token,str) and len(token)>=32 and
                    hmac.compare_digest(authorization,"Bearer "+token))
        def html(self):
            if not store.config.get("screen_view_token"):
                return self.send(404,{"error":"Screen viewer not enabled"})
            try:
                data=Path(__file__).with_name("viewer.html").read_bytes()
            except FileNotFoundError:
                return self.send(503,{"error":"Screen viewer needs installation"})
            self.send_response(200)
            self.send_header("Content-Type","text/html; charset=utf-8")
            self.send_header("Content-Length",str(len(data)))
            self.send_header("Cache-Control","no-store")
            self.send_header("X-Frame-Options","DENY")
            self.send_header("X-Content-Type-Options","nosniff")
            self.send_header("Referrer-Policy","no-referrer")
            self.send_header("Content-Security-Policy","default-src 'none'; img-src data:; connect-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'")
            self.end_headers()
            self.wfile.write(data)
        def json(self):
            n=int(self.headers.get("Content-Length","0"))
            if n<1 or n>245000:raise ValueError("Invalid Content-Length")
            body=json.loads(self.rfile.read(n))
            if not isinstance(body,dict):raise ValueError("Expected object")
            return body
        def do_GET(self):
            path=self.path.split("?",1)[0]
            if path=="/health":return self.send(200,{"ok":True,"service":"VitaLink"})
            if path in ("/viewer","/viewer/"):return self.html()
            if path=="/v1/screen/need":
                if not self.authorized("agent"):return self.send(401,{"error":"Unauthorized"})
                return self.send(200,store.screen_need())
            if path=="/v1/screen/frame":
                if not self.viewer_auth():return self.send(401,{"error":"Unauthorized"})
                return self.send(200,store.screen_latest())
            if path=="/v1/auth":
                if not self.authorized("operator"):return self.send(401,{"error":"Unauthorized"})
                return self.send(200,{"ok":True,"service":"VitaLink","auth":"operator"})
            if path=="/v1/next":
                if not self.authorized("agent"):return self.send(401,{"error":"Unauthorized"})
                return self.send(200,{"job":store.take()})
            if path.startswith("/v1/jobs/"):
                if not self.authorized("operator"):return self.send(401,{"error":"Unauthorized"})
                result=store.get(path.rsplit("/",1)[-1]);return self.send(200 if result else 404,result or {"error":"Missing"})
            return self.send(404,{"error":"Missing"})
        def do_POST(self):
            path=self.path.split("?",1)[0]
            try:
                if path in ("/v1/screen/session","/v1/screen/stop"):
                    if not self.viewer_auth():return self.send(401,{"error":"Unauthorized"})
                    if path=="/v1/screen/stop":return self.send(200,store.screen_stop())
                    return self.send(200,store.screen_start())
                if path=="/v1/screen/frame":
                    if not self.authorized("agent"):return self.send(401,{"error":"Unauthorized"})
                    return self.send(200,store.screen_publish(self.json()))
                if path=="/v1/jobs":
                    if not self.authorized("operator"):return self.send(401,{"error":"Unauthorized"})
                    request=self.json()
                    if request.get("action") in ("host_status","host_cpu","host_ping","host_shell"):
                        # Never permit a long-lived operator_token to enqueue
                        # privileged kernel admin operations. Require signed
                        # exact-workflow GitHub OIDC identity on each request.
                        bearer=self.headers.get("Authorization","")
                        if not (bearer.startswith("Bearer ") and store.github_oidc
                                and store.github_oidc.verify(bearer[7:])):
                            return self.send(403,{"error":"Signed GitHub OIDC required for host admin"})
                    return self.send(201,{"id":store.add(request)})
                if path=="/v1/result":
                    if not self.authorized("agent"):return self.send(401,{"error":"Unauthorized"})
                    store.finish(self.json());return self.send(200,{"ok":True})
                return self.send(404,{"error":"Missing"})
            except (ValueError,KeyError) as e:return self.send(400,{"error":str(e)})
    return Handler

def main():
    p=argparse.ArgumentParser();p.add_argument("--config",required=True);args=p.parse_args()
    conf=json.loads(Path(args.config).read_text())
    for key in ("operator_token","agent_token"):
        if len(conf.get(key,""))<32:raise SystemExit("Missing strong "+key)
    if conf["operator_token"]==conf["agent_token"]:raise SystemExit("Tokens must differ")
    store=Store(conf)
    port=int(conf.get("port",8765))
    handler=make_handler(store)
    docker_gateway=conf.get("docker_gateway_host")
    if docker_gateway:
        import ipaddress
        addr=ipaddress.ip_address(docker_gateway)
        if addr.version!=4 or not addr.is_private or addr.is_loopback or addr.is_unspecified:
            raise SystemExit("Invalid Docker gateway IPv4 address")
        bridge_server=ThreadingHTTPServer((str(addr),port),handler)
        threading.Thread(target=bridge_server.serve_forever,daemon=True).start()
        print("Docker bridge listener ready: "+str(addr)+":"+str(port),flush=True)
    print("Local VitaLink listener ready: 127.0.0.1:"+str(port),flush=True)
    ThreadingHTTPServer(("127.0.0.1",port),handler).serve_forever()
if __name__=="__main__":main()
