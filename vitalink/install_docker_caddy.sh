#!/usr/bin/env bash
# Route HTTPS /vitalink/* through existing Docker Caddy to the private host bridge.
# Back up and restore both services if any step fails.
set -Eeuo pipefail
if [[ "$EUID" -ne 0 ]]; then echo "Use sudo/root."; exit 1; fi
CADDY="mucho-core-caddy-1"
SOURCE_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER="/opt/vitalink/server.py"
CONF="/etc/vitalink/server.json"
command -v docker >/dev/null || { echo "Docker missing."; exit 2; }
[[ -f "$SERVER" && -f "$CONF" ]] || { echo "VitaLink server missing."; exit 3; }
[[ -f "$SOURCE_DIR/server.py" ]] || { echo "Updated source missing."; exit 4; }
docker inspect "$CADDY" >/dev/null || { echo "Cannot find Caddy container."; exit 5; }
[[ "$(docker inspect --format '{{.State.Running}}' "$CADDY")" == true ]] || { echo "Caddy not running."; exit 6; }
DETAILS="$(python3 - <<'PY'
import subprocess,json,ipaddress,sys
c=json.loads(subprocess.check_output(["docker","inspect","mucho-core-caddy-1"],text=True))[0]
mount=[m for m in c["Mounts"] if m.get("Destination")=="/etc/caddy/Caddyfile" and m.get("Type")=="bind"]
if len(mount)!=1:sys.exit("Expected a bind-mounted Caddyfile")
nets=c["NetworkSettings"]["Networks"]
bridges=[]
for name,n in nets.items():
    try:
        addr=ipaddress.ip_address(n.get("Gateway",""))
        if addr.version==4 and addr.is_private and not addr.is_loopback:bridges.append((name,str(addr)))
    except ValueError:pass
if len(bridges)!=1:sys.exit("Expected one bridge network; got "+str(bridges))
print(mount[0]["Source"])
print(bridges[0][1])
print(bridges[0][0])
PY
)" || exit 7
CADDYFILE="$(sed -n '1p' <<< "$DETAILS")"
GATEWAY="$(sed -n '2p' <<< "$DETAILS")"
NETNAME="$(sed -n '3p' <<< "$DETAILS")"
[[ -f "$CADDYFILE" ]] || { echo "Host Caddyfile missing: $CADDYFILE"; exit 8; }
echo "Found Docker Caddyfile: $CADDYFILE"
echo "Using private Docker network: $NETNAME / $GATEWAY"
docker exec "$CADDY" caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
curl -fsS --max-time 10 http://127.0.0.1:8765/health | grep -q '"VitaLink"' || exit 9
STAMP="$(date +%Y%m%d-%H%M%S)"
CB="$CADDYFILE.vitalink-backup-$STAMP"
SB="$SERVER.vitalink-backup-$STAMP"
TB="$CONF.vitalink-backup-$STAMP"
cp -p "$CADDYFILE" "$CB"
cp -p "$SERVER" "$SB"
cp -p "$CONF" "$TB"
ROLLBACK=1
restore_on_error() {
  STATUS="$?"
  if [[ "$ROLLBACK" == 1 && "$STATUS" -ne 0 ]]; then
    echo "Validation failed: restoring MuchoGDPS and VitaLink configuration..."
    cp -p "$CB" "$CADDYFILE" || true
    cp -p "$SB" "$SERVER" || true
    cp -p "$TB" "$CONF" || true
    systemctl restart vitalink || true
    docker exec "$CADDY" caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile || true
  fi
}
trap restore_on_error EXIT
install -m 0644 "$SOURCE_DIR/server.py" "$SERVER"
export VITALINK_DOCKER_GATEWAY="$GATEWAY"
python3 - <<'PY'
import os,json
from pathlib import Path
p=Path("/etc/vitalink/server.json")
data=json.loads(p.read_text())
data["docker_gateway_host"]=os.environ["VITALINK_DOCKER_GATEWAY"]
p.write_text(json.dumps(data,indent=2)+"\n")
PY
systemctl restart vitalink
sleep 1
curl -fsS --max-time 10 http://127.0.0.1:8765/health
echo
curl -fsS --max-time 10 "http://$GATEWAY:8765/health"
echo
docker exec "$CADDY" wget -q -T 10 -O - "http://$GATEWAY:8765/health"
echo
export VITALINK_CADDYFILE="$CADDYFILE"
python3 - <<'PY'
import os,re,sys
from pathlib import Path
p=Path(os.environ["VITALINK_CADDYFILE"])
s=p.read_text()
if re.search(r"(?m)^\s*(?:handle_path|handle)\s+[^\n]*?/vitalink",s):
    sys.exit("Existing VitaLink route found; refusing duplicate edit")
site=re.search(r"(?m)^[ \t]*\{\$CADDY_ADDRESS\}[ \t]*\{[ \t]*$",s)
if not site:sys.exit("Unable to locate MuchoCore Caddy site header")
gateway=os.environ["VITALINK_DOCKER_GATEWAY"]
route="\n    # VITALINK / canonical domain only\n"
route+="    @vitalink_canonical {\n        host muchogdps.space\n        path /vitalink/*\n    }\n"
route+="    handle @vitalink_canonical {\n        uri strip_prefix /vitalink\n"
route+="        reverse_proxy "+gateway+":8765\n    }\n"
p.write_text(s[:site.end()]+route+s[site.end():])
print("Added scoped VitaLink route.")
PY
docker exec "$CADDY" caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker exec "$CADDY" caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
sleep 1
RESULT="$(curl -fsS --max-time 20 -H 'Cache-Control: no-cache' https://muchogdps.space/vitalink/health)"
printf '%s\n' "$RESULT"
printf '%s' "$RESULT" | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("ok") is True and d.get("service")=="VitaLink"'
ROLLBACK=0
echo "SUCCESS: https://muchogdps.space/vitalink/health"
echo "Caddy backup: $CB"
