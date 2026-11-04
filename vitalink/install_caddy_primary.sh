#!/usr/bin/env bash
# Fail-closed VitaLink /vitalink/ routing for systemd-managed Caddy.
set -Eeuo pipefail
if (( EUID != 0 )); then echo 'Run as root (sudo)'; exit 1; fi
command -v caddy >/dev/null 2>&1 || { echo 'No Caddy binary. If Caddy runs in Docker, edit the container Caddyfile manually.'; exit 2; }
systemctl is-active --quiet caddy || { echo 'Caddy systemd service inactive: no changes made.'; exit 3; }
FILE="/etc/caddy/Caddyfile"
[[ -f "$FILE" ]] || { echo "Missing $FILE"; exit 4; }
curl -fsS --max-time 10 http://127.0.0.1:8765/health | grep -q '"VitaLink"' || { echo 'VitaLink is not healthy on localhost'; exit 5; }
caddy validate --config "$FILE" --adapter caddyfile >/dev/null || { echo 'Existing Caddyfile is invalid'; exit 6; }
backup="$FILE.before-vitalink.$(date +%Y%m%d-%H%M%S)"
cp -p -- "$FILE" "$backup"
export VITALINK_CADDYFILE="$FILE"
if ! python3 - <<'PY'
import os,re,sys
from pathlib import Path
p=Path(os.environ['VITALINK_CADDYFILE']);s=p.read_text(encoding='utf-8')
a=list(s);mode='normal';escaped=False
for i,c in enumerate(s):
    if mode=='comment':
        if c=='\n':mode='normal'
        else:a[i]=' '
    elif mode in ('double','single','backtick'):
        if c!='\n':a[i]=' '
        if escaped:escaped=False
        elif c=='\\' and mode!='backtick':escaped=True
        elif c=={'double':'"','single':"'",'backtick':chr(96)}[mode]:mode='normal'
    elif c=='#':a[i]=' ';mode='comment'
    elif c=='"':a[i]=' ';mode='double'
    elif c=="'":a[i]=' ';mode='single'
    elif c==chr(96):a[i]=' ';mode='backtick'
m=''.join(a)
depth=0;start=0;hits=[]
for i,ch in enumerate(m):
    if ch=='{':
        if depth==0:
            label=m[start:i].strip()
            if re.search(r'(?<![\w.-])(?:https://)?muchogdps\.space(?=\s|,|$)',label):hits.append(i)
        depth+=1
    elif ch=='}':
        depth-=1
        if depth<0:sys.exit('Malformed Caddyfile braces')
        if depth==0:start=i+1
if depth:sys.exit('Unbalanced Caddyfile braces')
if len(hits)!=1:sys.exit('Expected a single muchogdps.space block, found '+str(len(hits)))
if re.search(r'\b(?:handle_path|handle|route)\s+[^\n{]*?/vitalink',m):
    sys.exit('VitaLink route already exists; no changes made')
i=hits[0]
route='''
    # BEGIN VITALINK
    handle_path /vitalink/* {
        reverse_proxy 127.0.0.1:8765
    }
    # END VITALINK
'''
p.write_text(s[:i+1]+route+s[i+1:],encoding='utf-8')
print('Added isolated /vitalink/ route to the existing Caddy site.')
PY
then
  cp -p -- "$backup" "$FILE"
  echo 'Ambiguous site config: automatic modification was refused.'
  exit 7
fi
rollback() {
  cp -p -- "$backup" "$FILE"
  caddy validate --config "$FILE" --adapter caddyfile || true
  systemctl reload caddy || true
  echo 'Original MuchoGDPS Caddyfile restored.'
}
if ! caddy validate --config "$FILE" --adapter caddyfile; then rollback; exit 8; fi
if ! systemctl reload caddy; then rollback; exit 9; fi
if ! response="$(curl -fsS --max-time 20 https://muchogdps.space/vitalink/health)"; then
  rollback
  echo 'Public health check failed, route rolled back.'
  exit 10
fi
if ! printf '%s' "$response" | python3 -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if d.get("ok") is True and d.get("service")=="VitaLink" else 1)'; then
  rollback
  echo "Bad public health response: $response"
  exit 11
fi
echo 'SUCCESS: https://muchogdps.space/vitalink/health reaches VitaLink.'
echo "Backup of previous Caddyfile: $backup"
