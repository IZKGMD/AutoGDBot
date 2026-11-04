#!/usr/bin/env bash
# Opt-in screenshot relay for existing VitaLink / Docker Caddy.
# Does not touch MuchoGDPS containers, Caddy, or existing access tokens.
set -Eeuo pipefail
[[ "$EUID" == 0 ]] || { echo "Run with sudo/root."; exit 1; }
SOURCE="$(cd "$(dirname "$0")" && pwd)"
[[ -f "$SOURCE/server.py" && -f "$SOURCE/viewer.html" ]] || { echo "Missing VitaLink viewer sources."; exit 2; }
[[ -f /opt/vitalink/server.py && -f /etc/vitalink/server.json ]] || { echo "VitaLink is not installed."; exit 3; }
STAMP="$(date +%Y%m%d-%H%M%S)"
S="/opt/vitalink/server.py"
C="/etc/vitalink/server.json"
H="/opt/vitalink/viewer.html"
cp -p "$S" "$S.before-screen-$STAMP"
cp -p "$C" "$C.before-screen-$STAMP"
if [[ -f "$H" ]]; then cp -p "$H" "$H.before-screen-$STAMP"; fi
RESTORE=1
rollback() {
  status="$?"
  if [[ "$status" -ne 0 && "$RESTORE" == 1 ]]; then
    echo "Screenshot relay setup failed: restoring existing VitaLink."
    cp -p "$S.before-screen-$STAMP" "$S" || true
    cp -p "$C.before-screen-$STAMP" "$C" || true
    if [[ -f "$H.before-screen-$STAMP" ]]; then
      cp -p "$H.before-screen-$STAMP" "$H" || true
    else
      rm -f "$H" || true
    fi
    systemctl restart vitalink || true
  fi
}
trap rollback EXIT
/usr/bin/python3 -m py_compile "$SOURCE/server.py"
install -m 0644 "$SOURCE/server.py" "$S"
install -m 0644 "$SOURCE/viewer.html" "$H"
python3 - <<'PY'
import json,secrets
from pathlib import Path
p=Path("/etc/vitalink/server.json")
c=json.loads(p.read_text())
assert len(c.get("agent_token",""))>=32
assert len(c.get("operator_token",""))>=32
if not c.get("screen_view_token"):
    c["screen_view_token"]=secrets.token_urlsafe(36)
assert len(c["screen_view_token"])>=32
p.write_text(json.dumps(c,indent=2)+"\n")
PY
chown root:vitalink "$C"
chmod 0640 "$C"
systemctl restart vitalink
HEALTHY=0
for i in $(seq 1 15); do
  if curl -fsS --max-time 2 http://127.0.0.1:8765/health >/dev/null 2>&1; then
    HEALTHY=1
    break
  fi
  sleep 1
done
if [[ "$HEALTHY" != 1 ]]; then
  systemctl status vitalink --no-pager -l || true
  journalctl -u vitalink --no-pager -n 15 || true
  exit 4
fi
echo "Checking HTTPS viewer route..."
HTTP="$(curl -s -o /dev/null -w '%{http_code}' --max-time 12 https://muchogdps.space/vitalink/viewer)"
[[ "$HTTP" == 200 ]] || { echo "Viewer HTTP $HTTP, expected 200."; exit 5; }
echo "Checking screen API rejects unauthenticated access..."
READ_STATUS="$(curl -s -o /dev/null -w '%{http_code}' --max-time 12 https://muchogdps.space/vitalink/v1/screen/frame)"
WRITE_STATUS="$(curl -s -o /dev/null -w '%{http_code}' --max-time 12 -X POST https://muchogdps.space/vitalink/v1/screen/session)"
[[ "$READ_STATUS" == 401 && "$WRITE_STATUS" == 401 ]] || {
  echo "Unsafe access result: GET=$READ_STATUS POST=$WRITE_STATUS"; exit 6;
}
RESTORE=0
echo "SUCCESS: private, opt-in Vita screen viewer installed."
echo "Open: https://muchogdps.space/vitalink/viewer"
echo "Read viewer token privately with:"
echo "sudo python3 -c 'import json; print(json.load(open(\"/etc/vitalink/server.json\"))[\"screen_view_token\"])'"
echo "Next: upgrade and locally enable the capture agent on PS Vita."
