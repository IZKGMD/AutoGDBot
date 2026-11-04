#!/usr/bin/env python3
"""VitaLink: HTTPS reverse-proxied, authenticated job queue on VPS loopback."""
import json, sqlite3, threading, time, secrets, hmac, uuid, argparse
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path

class Store:
    def __init__(self, config):
        self.config=config; self.lock=threading.RLock()
        self.db=sqlite3.connect(config["database"],check_same_thread=False)
        self.db.row_factory=sqlite3.Row
        with self.db:
            self.db.execute("CREATE TABLE IF NOT EXISTS jobs (id TEXT PRIMARY KEY, action TEXT, args TEXT, state TEXT, created INTEGER, result TEXT)")
    def add(self, body):
        action=body.get("action"); args=body.get("args",{})
        if action not in ("diagnose","search","install","game","upload","download","shell") or not isinstance(args,dict):
            raise ValueError("Unknown action or invalid args")
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
            return hmac.compare_digest(self.headers.get("Authorization",""),"Bearer "+store.config[role+"_token"])
        def json(self):
            n=int(self.headers.get("Content-Length","0"))
            if n<1 or n>140000:raise ValueError("Invalid Content-Length")
            body=json.loads(self.rfile.read(n))
            if not isinstance(body,dict):raise ValueError("Expected object")
            return body
        def do_GET(self):
            path=self.path.split("?",1)[0]
            if path=="/health":return self.send(200,{"ok":True,"service":"VitaLink"})
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
                if path=="/v1/jobs":
                    if not self.authorized("operator"):return self.send(401,{"error":"Unauthorized"})
                    return self.send(201,{"id":store.add(self.json())})
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
