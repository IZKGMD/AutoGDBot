#!/usr/bin/env python3
"""GitHub Actions relay. Reads operator token from GitHub Actions Secret, not Git."""
import json,os,time,sys
from urllib.request import Request,build_opener,HTTPSHandler,HTTPRedirectHandler
import ssl
class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):raise RuntimeError('Redirect forbidden')
def call(url, token, body=None):
    data=json.dumps(body).encode() if body is not None else None
    opener=build_opener(NoRedirect(),HTTPSHandler(context=ssl.create_default_context()))
    with opener.open(Request(url,data,{"Authorization":"Bearer "+token,"Content-Type":"application/json"}),timeout=25) as r:return json.load(r)
def main():
    base=os.environ["VITALINK_URL"].rstrip("/")
    token=os.environ["VITALINK_OPERATOR_TOKEN"]
    if not base.startswith("https://"):raise SystemExit("HTTPS required")
    request=json.load(open("vitalink/requests/request.json"))
    result=call(base+"/v1/jobs",token,request)
    ident=result["id"];print("Job ID: "+ident)
    for i in range(40):
        job=call(base+"/v1/jobs/"+ident,token)
        if job["state"]=="done":
            data=job.get("result") or {}
            print("Exit: "+str(data.get("code")))
            if request["action"]=="download":print("Binary results withheld from public workflow logs")
            else:print(str(data.get("output",""))[:7000])
            return
        time.sleep(3)
    print("Queued/running: "+ident)
if __name__=="__main__":main()
