#!/usr/bin/env python3
"""VitaLink private screen relay regression tests (no real Vita required)."""
import base64, http.client, json, tempfile, threading, unittest
from pathlib import Path
from http.server import ThreadingHTTPServer
from vitalink.server import Store, make_handler

class ScreenRelayTests(unittest.TestCase):
    def setUp(self):
        self.directory=tempfile.TemporaryDirectory()
        config={
            "operator_token":"O"*48, "agent_token":"A"*48,
            "screen_view_token":"V"*48, "database":str(Path(self.directory.name)/"jobs.sqlite"),
            "enable_github_oidc":False
        }
        self.store=Store(config)
        self.server=ThreadingHTTPServer(("127.0.0.1",0),make_handler(self.store))
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True)
        self.thread.start()
    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.store.db.close()
        self.directory.cleanup()
    def call(self, method, path, token=None, body=None):
        conn=http.client.HTTPConnection("127.0.0.1",self.server.server_port,timeout=2)
        headers={}
        if token is not None: headers["Authorization"]="Bearer "+token
        data=json.dumps(body).encode() if body is not None else None
        if data is not None:headers["Content-Type"]="application/json"
        conn.request(method,path,body=data,headers=headers)
        res=conn.getresponse();raw=res.read();code=res.status;conn.close()
        return code,json.loads(raw)
    def jpeg(self):
        return base64.b64encode(b"\xff\xd8\xff"+b"\x00"*800+b"\xff\xd9").decode()
    def test_viewer_cannot_use_operator_api(self):
        code,_=self.call("POST","/v1/jobs","V"*48,{"action":"diagnose","args":{}})
        self.assertEqual(code,401)
    def test_anonymous_cannot_start_or_view(self):
        for method,path in (("POST","/v1/screen/session"),("GET","/v1/screen/frame")):
            with self.subTest(path=path):
                code,_=self.call(method,path)
                self.assertEqual(code,401)
    def test_agent_cannot_start_viewer(self):
        self.assertEqual(self.call("POST","/v1/screen/session","A"*48)[0],401)
    def test_opt_in_session_stream_and_stop(self):
        self.assertFalse(self.call("GET","/v1/screen/need","A"*48)[1]["capture"])
        self.assertTrue(self.call("POST","/v1/screen/session","V"*48)[1]["active"])
        self.assertTrue(self.call("GET","/v1/screen/need","A"*48)[1]["capture"])
        self.assertEqual(self.call("POST","/v1/screen/frame","A"*48,{"jpeg":self.jpeg()})[0],200)
        status,result=self.call("GET","/v1/screen/frame","V"*48)
        self.assertEqual(status,200)
        self.assertTrue(result["available"])
        self.assertEqual(result["jpeg"],self.jpeg())
        self.assertEqual(self.call("POST","/v1/screen/stop","V"*48)[0],200)
        self.assertFalse(self.call("GET","/v1/screen/frame","V"*48)[1]["available"])
        self.assertFalse(self.call("GET","/v1/screen/need","A"*48)[1]["capture"])
    def test_frame_rejected_when_no_active_viewer(self):
        self.assertEqual(self.call("POST","/v1/screen/frame","A"*48,{"jpeg":self.jpeg()})[0],400)
    def test_bad_images_rejected(self):
        self.call("POST","/v1/screen/session","V"*48)
        self.assertEqual(self.call("POST","/v1/screen/frame","A"*48,{"jpeg":"deadbeef"})[0],400)
        self.assertEqual(self.call("POST","/v1/screen/frame","A"*48,{"jpeg":"a"*230001})[0],400)
    def test_expired_session_does_not_reveal_frame(self):
        self.call("POST","/v1/screen/session","V"*48)
        self.call("POST","/v1/screen/frame","A"*48,{"jpeg":self.jpeg()})
        self.store.screen_active_until=0
        self.assertFalse(self.call("GET","/v1/screen/frame","V"*48)[1]["available"])
    def test_agent_does_not_have_operator_permissions(self):
        self.assertEqual(self.call("GET","/v1/jobs/not-a-job","A"*48)[0],401)

if __name__=="__main__":
    unittest.main()
