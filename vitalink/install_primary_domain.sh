#!/usr/bin/env bash
# Install VitaLink at https://muchogdps.space/vitalink/ without replacing MuchoGDPS.
set -Eeuo pipefail
if [ "$(id -u)" -ne 0 ]; then echo 'Run using sudo'; exit 1; fi
SOURCE_DIR="$1"
if [ ! -f "$SOURCE_DIR/install_vps.sh" ]; then
  echo 'Usage: sudo bash vitalink/install_primary_domain.sh /path/to/vitalink'
  exit 2
fi
if ! command -v nginx >/dev/null 2>&1; then
  echo 'Nginx was not detected; existing webserver remains unchanged.'
  echo 'Manually reverse proxy /vitalink/ to http://127.0.0.1:8765/ (remove prefix).'
  exit 3
fi
nginx -t
bash "$SOURCE_DIR/install_vps.sh" "$SOURCE_DIR"
export VITALINK_DOMAIN='muchogdps.space'
# Select active HTTPS Nginx server using nginx -T (not a guessed config filename).
TARGET="$(python3 - <<'PY'
import os,re,subprocess,pathlib,sys
name=os.environ['VITALINK_DOMAIN']
p=subprocess.run(['nginx','-T'],capture_output=True,text=True)
if p.returncode:sys.exit('Cannot read Nginx configuration')
sections=re.split(r'^# configuration file ([^\n]+):\s*$',p.stdout,flags=re.M)
for index in range(1,len(sections)-1,2):
    filename=sections[index].strip()
    raw=sections[index+1]
    masked=re.sub(r'(?m)#.*$',lambda m:' '*len(m.group()),raw)
    for m in re.finditer(r'\bserver\s*\{',masked):
        depth=1;end=m.end()
        while end<len(masked) and depth:
            if masked[end]=='{':depth+=1
            elif masked[end]=='}':depth-=1
            end+=1
        if depth:continue
        body=masked[m.end():end-1]
        names=re.findall(r'\bserver_name\s+([^;]+);',body)
        if not any(name in re.split(r'\s+',n.strip()) for n in names):continue
        if not re.search(r'\blisten\s+[^;]*\b443\b[^;]*;',body):continue
        if not (re.search(r'\blisten\s+[^;]*\bssl\b',body) or
                re.search(r'\bssl_certificate\s',body)):continue
        target=pathlib.Path(filename)
        if target.is_file():
            print(target.resolve())
            sys.exit(0)
sys.exit('No active HTTPS Nginx server block found for '+name)
PY
)" || { echo "Unable to find HTTPS vhost for muchogdps.space; existing site unchanged."; exit 4; }
BACKUP="$TARGET.vitalink-backup-$(date +%Y%m%d%H%M%S)"
cp -a -- "$TARGET" "$BACKUP"
export VITALINK_TARGET="$TARGET"
if ! python3 - <<'PY'
import os,re,pathlib,sys
f=pathlib.Path(os.environ['VITALINK_TARGET'])
name=os.environ['VITALINK_DOMAIN']
source=f.read_text()
masked=re.sub(r'(?m)#.*$',lambda m:' '*len(m.group()),source)
candidates=[]
for m in re.finditer(r'\bserver\s*\{',masked):
    depth=1;end=m.end()
    while end<len(masked) and depth:
        if masked[end]=='{':depth+=1
        elif masked[end]=='}':depth-=1
        end+=1
    if depth:continue
    body=masked[m.end():end-1]
    names=re.findall(r'\bserver_name\s+([^;]+);',body)
    if not any(name in re.split(r'\s+',n.strip()) for n in names):continue
    if not re.search(r'\blisten\s+[^;]*\b443\b[^;]*;',body):continue
    if not (re.search(r'\blisten\s+[^;]*\bssl\b',body) or
            re.search(r'\bssl_certificate\s',body)):continue
    candidates.append((m,end,body))
if len(candidates)!=1:
    sys.exit('Expected one HTTPS server block for '+name+', found '+str(len(candidates)))
m,end,body=candidates[0]
if re.search(r'\blocation\s+(?:\^~\s+)?/vitalink/',body):
    print('VitaLink route already exists; leaving it unchanged.')
    sys.exit(0)
block=r'''
    # BEGIN VITALINK / MuchoGDPS-compatible relay
    location ^~ /vitalink/ {
        proxy_pass http://127.0.0.1:8765/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 130s;
        client_max_body_size 140k;
    }
    # END VITALINK
'''
f.write_text(source[:end-1]+block+source[end-1:])
print('Configured https://muchogdps.space/vitalink/ in '+str(f))
PY
then
  echo 'Unable to patch Nginx; restoring prior config.'
  cp -a -- "$BACKUP" "$TARGET"
  exit 5
fi
if ! nginx -t; then
  echo 'Nginx syntax check failed. Restoring prior config.'
  cp -a -- "$BACKUP" "$TARGET"
  nginx -t || true
  exit 6
fi
if ! systemctl reload nginx; then
  echo 'Nginx reload failed. Restoring prior config.'
  cp -a -- "$BACKUP" "$TARGET"
  systemctl reload nginx || true
  exit 7
fi
echo
echo 'Local VitaLink status:'
curl --fail --silent --show-error --max-time 8 http://127.0.0.1:8765/health || true
echo
echo 'Public VitaLink status:'
curl --fail --silent --show-error --max-time 15 https://muchogdps.space/vitalink/health || echo 'Public HTTPS check failed; verify DNS/TLS.'
echo
echo "Backup: $BACKUP"
echo 'VITALINK_URL for GitHub Actions = https://muchogdps.space/vitalink'
echo 'Secrets are local in /etc/vitalink/server.json; do NOT paste them into chat.'
