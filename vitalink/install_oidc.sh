#!/usr/bin/env bash
# Enable trusted GitHub Actions OIDC, no GitHub repository Secrets required.
# Existing VPS, VitaLink service, Docker and Caddy are preserved.
set -Eeuo pipefail
if [[ "$EUID" != 0 ]]; then echo "Run with sudo/root."; exit 1; fi
SOURCE="$(cd "$(dirname "$0")" && pwd)"
if [[ ! -f "$SOURCE/server.py" ]]; then echo "Missing source/server.py"; exit 2; fi
if [[ ! -f /opt/vitalink/server.py || ! -f /etc/vitalink/server.json ]]; then
  echo "VitaLink is not installed on the VPS yet."; exit 3
fi
echo "Installing Ubuntu's security-updated JWT signature verifier..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq python3-jwt python3-cryptography
/usr/bin/python3 - <<'PY'
import jwt
from jwt import PyJWKClient
assert PyJWKClient
print("JWT library installed:",jwt.__version__)
PY
STAMP="$(date +%Y%m%d-%H%M%S)"
SERVER_BACKUP="/opt/vitalink/server.py.before-oidc-$STAMP"
CONFIG_BACKUP="/etc/vitalink/server.json.before-oidc-$STAMP"
cp -p /opt/vitalink/server.py "$SERVER_BACKUP"
cp -p /etc/vitalink/server.json "$CONFIG_BACKUP"
RESTORE=1
restore_on_error() {
  status="$?"
  if [[ "$status" -ne 0 && "$RESTORE" == 1 ]]; then
    echo "VitaLink OIDC setup failed. Restoring existing server and configuration."
    cp -p "$SERVER_BACKUP" /opt/vitalink/server.py || true
    cp -p "$CONFIG_BACKUP" /etc/vitalink/server.json || true
    systemctl restart vitalink || true
  fi
}
trap restore_on_error EXIT
install -m 0644 "$SOURCE/server.py" /opt/vitalink/server.py
/usr/bin/python3 - <<'PY'
import json
from pathlib import Path
p=Path("/etc/vitalink/server.json")
data=json.loads(p.read_text())
assert isinstance(data.get("agent_token"),str) and len(data["agent_token"])>=32
assert isinstance(data.get("operator_token"),str) and len(data["operator_token"])>=32
data["enable_github_oidc"]=True
p.write_text(json.dumps(data,indent=2)+"\n")
PY
/usr/bin/python3 -m py_compile /opt/vitalink/server.py
systemctl restart vitalink
sleep 1
echo "Checking local health..."
curl -fsS --max-time 10 http://127.0.0.1:8765/health
echo
echo "Checking HTTPS health..."
curl -fsS --max-time 15 https://muchogdps.space/vitalink/health
echo
echo "Checking unauthenticated users cannot submit Vita tasks..."
CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 12 https://muchogdps.space/vitalink/v1/auth)"
if [[ "$CODE" != 401 ]]; then echo "Expected HTTP 401, got $CODE"; exit 4; fi
RESTORE=0
echo "SUCCESS: VitaLink accepts verified GitHub OIDC; GitHub Secrets not required."
echo "GitHub Actions workflow can now authenticate automatically."
echo "Keep your existing PS Vita agent_token only in the VitaControl app."
