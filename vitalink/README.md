# VitaLink — bridge for PS Vita LinuxOnVita

VitaLink lets a PS Vita reach your private VPS via outbound HTTPS. A GitHub Actions workflow sends authorized diagnostic requests and waits for the Vita agent to report results. ChatGPT can edit the request file and read the resulting GitHub Actions logs through the connected GitHub app. It does NOT give ChatGPT direct persistent SSH/network access.

## 1. VPS (Ubuntu 22.04)

Create a DNS A record for **vita.muchogdps.space** pointing to your VPS IP. Do not change the live MuchoGDPS site.

On your VPS via your own SSH connection, run:

    git clone --depth 1 --branch vitabridge-android https://github.com/IZKGMD/AutoGDBot.git /tmp/vitalink-source
    sudo bash /tmp/vitalink-source/vitalink/install_vps.sh /tmp/vitalink-source/vitalink

The installer only starts an isolated systemd service on **127.0.0.1:8765**. It never modifies Nginx, Docker, MuchoGDPS or your DNS. Configure **HTTPS** reverse proxy for vita.muchogdps.space with your existing Nginx/Caddy and a valid certificate. Point upstream at http://127.0.0.1:8765. Do not expose port 8765 to the internet. Check https://vita.muchogdps.space/health should return {"ok":true,"service":"VitaLink"}.

Example Nginx settings INSIDE a valid HTTPS server block:

    server_name vita.muchogdps.space;
    location / {
        proxy_pass http://127.0.0.1:8765;
        client_max_body_size 140k;
    }

Obtain valid TLS certificate first, and do not overwrite the existing MuchoGDPS server block.

## 2. Two distinct secrets (never send them to ChatGPT)

Read the generated secrets locally on the VPS: sudo cat /etc/vitalink/server.json

- GitHub repo Settings → Secrets and variables → Actions: set **VITALINK_URL** = https://vita.muchogdps.space and **VITALINK_OPERATOR_TOKEN** = operator_token from the VPS config.
- On the phone in VitaControl **Мост** tab enter **agent_token** (a different token). The Android app installs agent.py into Alpine on the Vita using its existing SSH connection. Never commit either token.

## 3. Using ChatGPT after setup

Editing vitalink/requests/request.json triggers .github/workflows/vitalink-requests.yml. Read-only diagnostic example:

    {"action":"diagnose","args":{},"note":"test-1"}

Other actions: search (package names array), game (one of the four built-in projects), upload (max 50KB, inbox directory), download (max 15KB from outbox but NOT printed to public logs). install and shell are disabled by default; enabling requires explicit local configuration in both VPS and Vita agent.

## Security and privacy

**This repository is PUBLIC.** Anything submitted in request.json or printed in workflow logs becomes publicly visible. Do not submit secrets, passwords, sensitive files or private command output here. To work extensively with files and unrestricted terminal, move the request workflow into a PRIVATE repository before enabling shell access. User needs to explicitly configure and authorize that private control channel.

Vita agent uses outbound HTTPS only. No router port-forwarding, public SSH or exposed reverse tunnel is needed. The VPS stores separate operator/agent credentials with strict file permissions. The agent runs diagnostics by default; package installation and arbitrary shell commands are NOT enabled.

## Limitations

I cannot automatically sign into your VPS or discover its private SSH credentials. DNS, TLS and GitHub secrets require a one-time setup by the owner. Nothing should be described as connected until /health and an authenticated Vita diagnostic job actually succeed.
