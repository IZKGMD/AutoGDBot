# VitaLink без GitHub Secrets — OIDC

Теперь **не нужно** вводить `VITALINK_OPERATOR_TOKEN` или `VITALINK_URL` в настройках GitHub. GitHub Actions получает краткоживущий подписанный токен OIDC автоматически, а сервер проверяет подпись GitHub, срок действия, ветку и идентификатор разрешённого workflow.

На действующем VPS с Caddy в Docker выполни один раз:

```bash
WORKDIR="$(mktemp -d)"
git clone --depth 1 --branch vitabridge-android https://github.com/IZKGMD/AutoGDBot.git "$WORKDIR/source"
sudo bash "$WORKDIR/source/vitalink/install_oidc.sh"
```

Установщик обновляет только Python-сервер VitaLink, ставит библиотеки проверки цифровой подписи из репозитория безопасности Ubuntu, сохраняет резервные копии конфигурации и проверяет, что посторонние запросы отклоняются с HTTP 401. **Docker, Caddy и MuchoGDPS он не изменяет.**

Ключ `agent_token` по-прежнему нужен **только при первом подключении PS Vita в VitaControl**. Токен `operator_token` сохраняется на VPS для совместимости, но в GitHub не копируется.

Для проверки через GitHub после установки агента запусти workflow `VitaLink - dispatch a Vita job` или обнови `vitalink/requests/request.json`. Журналы Actions в публичном репозитории видны всем: не отправляй туда секреты, приватные файлы и конфиденциальные результаты диагностики. Удалённая оболочка и установка пакетов по умолчанию запрещены.

---

# Dockerized MuchoGDPS / main-domain VitaLink

The actual VPS uses Docker Caddy container `mucho-core-caddy-1`, not host Nginx.
Its bind-mounted Caddyfile lives in the existing MuchoCore project, and
the localhost VitaLink service is running normally.

One-time Docker fix (no VPS reinstallation, no subdomain):

```bash
WORKDIR="$(mktemp -d)"
git clone --depth 1 --branch vitabridge-android https://github.com/IZKGMD/AutoGDBot.git "$WORKDIR/source"
sudo bash "$WORKDIR/source/vitalink/install_docker_caddy.sh"
```

The fixer reads the actual Docker bind mount and bridge gateway, updates the
existing VitaLink Python process to listen on private Docker gateway as well
as 127.0.0.1, and adds one canonical-domain Caddy handle for /vitalink/*.
It validates and hot-reloads Caddy without restarting any MuchoCore containers.
It backs up Caddyfile, server.py, and server.json and restores them on failure.

Test: https://muchogdps.space/vitalink/health should return JSON with
ok=true and service=VitaLink. Docker port 8765 is not published publicly.

**Note:** The route is in the MuchoCore Caddyfile which is tracked in its Git
repository; a future git checkout/update could overwrite this manual change.
Preserve the Caddyfile or incorporate the route in source before upgrades.


# VitaLink at muchogdps.space — one-domain installation

This deployment uses the **existing main domain**, not a subdomain:
`https://muchogdps.space/vitalink`.

It keeps the current MuchoGDPS application and TLS certificate in place. VitaLink runs as a separate Linux service on `127.0.0.1:8765`; a single additional Nginx `location ^~ /vitalink/` forwards requests while removing the prefix.

## One-time setup on VPS (Ubuntu 22.04 with Nginx)

Run from your normal VPS SSH console:

```bash
WORKDIR="$(mktemp -d)"
git clone --depth 1 --branch vitabridge-android https://github.com/IZKGMD/AutoGDBot.git "$WORKDIR/source"
sudo bash "$WORKDIR/source/vitalink/install_primary_domain.sh" "$WORKDIR/source/vitalink"
```

The script checks the live Nginx configuration, installs the isolated service, backs up the current HTTPS virtual host, inserts only the new route, checks syntax with `nginx -t`, and reloads Nginx. It restores the original config if validation/reload fails. It does **not** modify the root website, other routes, Docker containers, or SSH settings. It requires Nginx and a working existing HTTPS virtual host for `muchogdps.space`. If not present, the script stops without changing the web configuration.

Test: `https://muchogdps.space/vitalink/health` should respond with `{"ok":true,"service":"VitaLink"}`.

## Pairing

The installer creates separate tokens in `/etc/vitalink/server.json` (mode 0640). Never send them in chat or commit them to GitHub.

1. Repository **Settings → Secrets and variables → Actions**: add `VITALINK_URL` = `https://muchogdps.space/vitalink` and `VITALINK_OPERATOR_TOKEN` = local `operator_token`.
2. Android VitaControl **2.6 → Мост**: keep `https://muchogdps.space/vitalink` and enter local `agent_token` (not the operator token). The phone installs the LinuxOnVita Alpine agent over SSH, then the agent connects by outbound HTTPS.
3. On successful pairing, editing `vitalink/requests/request.json` triggers `.github/workflows/vitalink-requests.yml`, which sends a job using GitHub Actions Secrets. See workflow logs for results.

## Security and limitations

**This repository is public.** Workflows and requests may be visible to anyone. Only non-sensitive diagnostics should be published there. The agent defaults to safe diagnostics; remote shell and package installation are disabled without additional local authorization. To work with private files and unrestricted shell, migrate the control workflow to a private repository and secure its tokens first. Do not expose port 8765 or PS Vita's SSH to the Internet.

The VPS must be configured once using the commands above. The ChatGPT execution environment cannot SSH into the VPS itself. No claim of a live bridge should be made until the public HTTPS health endpoint and an authenticated job succeed.

## Manual Nginx fallback

For a custom Nginx layout, insert this **inside the existing HTTPS server for muchogdps.space** (not as a new server block):

```nginx
location ^~ /vitalink/ {
    proxy_pass http://127.0.0.1:8765/;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 140k;
}
```

Then run `sudo nginx -t && sudo systemctl reload nginx`. This does not need a new DNS record or certificate.

## Debugging

On VPS: `sudo systemctl status vitalink` and `sudo journalctl -u vitalink -n 50 --no-pager`.
On Vita: VitaControl → Мост → journal, or `/root/vitalink/agent.log` within Alpine.
