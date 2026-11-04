#!/usr/bin/env python3
"""Offline regression tests: rejects unsigned, forged and wrong-workflow tokens."""
import time,unittest
from types import SimpleNamespace
import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
from vitalink.server import GitHubOIDCVerifier

class FakeJwks:
    def __init__(self,key):
        self.key=key
    def get_signing_key_from_jwt(self,unused):
        return SimpleNamespace(key=self.key)

class GitHubIdentityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.private=rsa.generate_private_key(public_exponent=65537,key_size=2048)
        cls.public=cls.private.public_key()
        cls.verifier=GitHubOIDCVerifier()
        cls.verifier.keys=FakeJwks(cls.public)
    def make_token(self,**replacements):
        now=int(time.time())
        claims={
            "iss":"https://token.actions.githubusercontent.com",
            "aud":"vitalink.muchogdps.space",
            "sub":"repo:IZKGMD/AutoGDBot:ref:refs/heads/vitabridge-android",
            "repository":"IZKGMD/AutoGDBot",
            "repository_id":"1407579678",
            "ref":"refs/heads/vitabridge-android",
            "workflow_ref":"IZKGMD/AutoGDBot/.github/workflows/vitalink-requests.yml@refs/heads/vitabridge-android",
            "event_name":"workflow_dispatch",
            "iat":now,
            "exp":now+240
        }
        claims.update(replacements)
        return jwt.encode(claims,self.private,algorithm="RS256",headers={"kid":"test-key"})
    def test_valid_token(self):
        self.assertTrue(self.verifier.verify(self.make_token()))
    def test_wrong_repo(self):
        self.assertFalse(self.verifier.verify(self.make_token(repository="attacker/AutoGDBot")))
    def test_wrong_id(self):
        self.assertFalse(self.verifier.verify(self.make_token(repository_id="0")))
    def test_wrong_branch(self):
        self.assertFalse(self.verifier.verify(self.make_token(ref="refs/heads/main")))
    def test_wrong_workflow(self):
        self.assertFalse(self.verifier.verify(self.make_token(workflow_ref="IZKGMD/AutoGDBot/.github/workflows/evil.yml@refs/heads/vitabridge-android")))
    def test_wrong_event(self):
        self.assertFalse(self.verifier.verify(self.make_token(event_name="pull_request")))
    def test_wrong_audience(self):
        self.assertFalse(self.verifier.verify(self.make_token(aud="not-vitalink")))
    def test_expired(self):
        self.assertFalse(self.verifier.verify(self.make_token(iat=int(time.time())-1000,exp=int(time.time())-500)))
    def test_long_lived(self):
        self.assertFalse(self.verifier.verify(self.make_token(exp=int(time.time())+7200)))
    def test_unsigned_or_empty(self):
        self.assertFalse(self.verifier.verify(""))
        self.assertFalse(self.verifier.verify("a.b.c"))
        self.assertFalse(self.verifier.verify("a"*8193))
    def test_forged_signature(self):
        forged_key=rsa.generate_private_key(public_exponent=65537,key_size=2048)
        now=int(time.time())
        claims=jwt.decode(self.make_token(),options={"verify_signature":False})
        forged=jwt.encode(claims,forged_key,algorithm="RS256",headers={"kid":"test-key"})
        self.assertFalse(self.verifier.verify(forged))

if __name__=="__main__":
    unittest.main()
