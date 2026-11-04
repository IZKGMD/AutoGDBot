#!/usr/bin/env bash
# Install named LinuxOnVita host-kernel admin operations on the existing VPS.
# No public root shell, no new inbound port, no changes to Docker or Caddy.
set -Eeuo pipefail
[[ "$(id -u)" -eq 0 ]] || { echo "Run with sudo"; exit 1; }
HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$HERE/server.py"
CONFIG="/etc/vitalink/server.json"
TARGET="/opt/vitalink/server.py"
[[ -f "$SERVER" && -f "$CONFIG" && -f "$TARGET" ]] || {
  echo "Existing VitaLink installation not found"; exit 2;
}
echo "Checking server Python syntax..."
/usr/bin/python3 -m py_compile "$SERVER"
STAMP="$(date +%Y%m%d-%H%M%S)"
cp -p "$TARGET" "$TARGET.before-host-admin-$STAMP"
cp -p "$CONFIG" "$CONFIG.before-host-admin-$STAMP"
RESTORE=1
restore() {
  rc="$?"
  if [[ "$rc" -ne 0 && "$RESTORE" -eq 1 ]]; then
    echo "Restore previous VitaLink server after unsuccessful update"
    cp -p "$TARGET.before-host-admin-$STAMP" "$TARGET" || true
    cp -p "$CONFIG.before-host-admin-$STAMP" "$CONFIG" || true
    systemctl restart vitalink || true
  fi
}
trap restore EXIT
/usr/bin/python3 - <<'PY'
import json
from pathlib import Path
p=Path("/etc/vitalink/server.json")
conf=json.loads(p.read_text())
assert conf.get("enable_github_oidc"),"Install GitHub OIDC before host admin"
assert len(conf.get("agent_token",""))>=32
assert len(conf.get("operator_token",""))>=32
conf["enable_host_admin"]=True
p.write_text(json.dumps(conf,indent=2)+"\n")
PY
chown root:vitalink "$CONFIG"
chmod 0640 "$CONFIG"
install -m 0644 "$SERVER" "$TARGET"
systemctl restart vitalink
healthy=0
for i in $(seq 1 12); do
  if curl -fsS --max-time 3 http://127.0.0.1:8765/health >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 1
done
[[ "$healthy" == 1 ]] || {
  systemctl status vitalink --no-pager || true
  journalctl -u vitalink -n 15 --no-pager || true
  exit 3
}
echo "Checking protected HTTPS API..."
STATUS=$(curl --max-time 12 -s -o /dev/null -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  --data '{"action":"host_cpu","args":{"profile":"max"}}' \
  https://muchogdps.space/vitalink/v1/jobs)
[[ "$STATUS" == 401 ]] || { echo "Unexpected anonymous host admin response: $STATUS"; exit 4; }
RESTORE=0
echo "SUCCESS: VitaLink host admin enabled on VPS (OIDC only)."
echo "Install the opt-in VitaControl agent on Vita, then ask for host_status."
