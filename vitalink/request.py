#!/usr/bin/env python3
"""GitHub Actions -> VitaLink without GitHub repository Secrets.

Actions OIDC provides a short-lived signed JWT; the VPS verifies the GitHub
issuer, signature, audience, exact repository ID, ref and workflow identity.
"""
import json, os, ssl, time
from urllib.parse import quote
from urllib.request import Request, build_opener, HTTPSHandler, HTTPRedirectHandler

BASE="https://muchogdps.space/vitalink"
AUDIENCE="vitalink.muchogdps.space"

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):
        raise RuntimeError("Redirect forbidden")

OPENER=build_opener(NoRedirect(),HTTPSHandler(context=ssl.create_default_context()))

def oidc_token():
    url=os.environ["ACTIONS_ID_TOKEN_REQUEST_URL"]
    bearer=os.environ["ACTIONS_ID_TOKEN_REQUEST_TOKEN"]
    if not url.startswith("https://"):
        raise RuntimeError("Invalid GitHub identity endpoint")
    url+=("&" if "?" in url else "?")+"audience="+quote(AUDIENCE,safe="")
    req=Request(url,headers={"Authorization":"Bearer "+bearer,
                             "Accept":"application/json"})
    with OPENER.open(req,timeout=15) as response:
        token=json.load(response).get("value")
    if not isinstance(token,str) or token.count(".")!=2:
        raise RuntimeError("Invalid GitHub identity token")
    return token

def call(path,token,data=None):
    body=json.dumps(data).encode() if data is not None else None
    headers={"Authorization":"Bearer "+token,
             "Content-Type":"application/json"}
    with OPENER.open(Request(BASE+path,body,headers),timeout=25) as response:
        return json.load(response)

def main():
    token=oidc_token()
    verified=call("/v1/auth",token)
    if verified.get("auth")!="operator":
        raise RuntimeError("VitaLink operator authentication failed")
    print("VitaLink: GitHub OIDC identity verified (zero GitHub Secrets)")
    with open("vitalink/requests/request.json",encoding="utf-8") as f:
        request=json.load(f)
    job=call("/v1/jobs",token,request)
    job_id=job["id"]
    print("Job:",job_id)
    for _ in range(40):
        status=call("/v1/jobs/"+job_id,token)
        if status["state"]=="done":
            result=status.get("result") or {}
            print("Exit:",result.get("code"))
            if request.get("action")=="download":
                print("Binary data withheld from public logs")
            else:
                print(str(result.get("output",""))[:7000])
            return
        time.sleep(3)
    print("Agent offline or busy; job queued on VitaLink:",job_id)

if __name__=="__main__":
    main()
