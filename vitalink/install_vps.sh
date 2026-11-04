#!/usr/bin/env bash
# VitaLink installer (does not touch Nginx, DNS, Docker, or MuchoGDPS).
set -euo pipefail
if [ "$(id -u)" != 0 ]; then echo "Run with sudo"; exit 1; fi
SRC="${1:-$(pwd)/vitalink}"
test -f "$SRC/server.py" || { echo "Missing $SRC/server.py"; exit 2; }
id vitalink >/dev/null 2>&1 || useradd -r -d /var/lib/vitalink -s /usr/sbin/nologin vitalink
install -d -o root -g vitalink -m 0750 /etc/vitalink
install -d -o vitalink -g vitalink -m 0700 /var/lib/vitalink
install -d -m 0755 /opt/vitalink
install -m 0644 "$SRC/server.py" /opt/vitalink/server.py
install -m 0644 "$SRC/vitalink.service" /etc/systemd/system/vitalink.service
if [ ! -f /etc/vitalink/server.json ]; then
python3 - <<'PY'
import json,secrets
from pathlib import Path
p=Path("/etc/vitalink/server.json")
p.write_text(json.dumps({"operator_token":secrets.token_urlsafe(36),
 "agent_token":secrets.token_urlsafe(36),"database":"/var/lib/vitalink/jobs.sqlite",
 "port":8765,"enable_shell":False},indent=2)+"\n")
PY
fi
chown root:vitalink /etc/vitalink/server.json
chmod 0640 /etc/vitalink/server.json
systemctl daemon-reload
systemctl enable --now vitalink
sleep 1
curl -fsS http://127.0.0.1:8765/health || { journalctl -u vitalink --no-pager -n 30; exit 3; }
echo
echo "VitaLink active on 127.0.0.1:8765 only."
echo "Configure HTTPS reverse proxy for vita.muchogdps.space separately."
echo "VitaLink secrets at /etc/vitalink/server.json. DO NOT send them to ChatGPT."
echo "Add VITALINK_URL and VITALINK_OPERATOR_TOKEN to GitHub Actions Secrets."
echo "Enter agent_token privately into VitaControl on Android."
