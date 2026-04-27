# Operating the deployed Ultraprocessed backend

Companion to [`deploy.md`](./deploy.md). That doc covers the one-time bring-up; this is the day-two runbook for the running container on K12 (LXC 101 docker host, 192.168.50.60). Hand this to a Claude instance and it can SSH in, tail logs, fix mistakes and ship updates without poking around.

## Quick reference

| Thing | Value |
|---|---|
| Public URL | https://ultraprocessed.mossom.co.uk |
| Public API health (no auth) | https://ultraprocessed.mossom.co.uk/api/v1/health |
| Docker host | `ssh root@192.168.50.60` (alias not set; use IP) |
| Stack dir on host | `/opt/stacks/ultraprocessed/` (full repo clone) |
| Compose file | `/opt/stacks/ultraprocessed/deploy/compose.prod.yml` |
| Env file | `/opt/stacks/ultraprocessed/deploy/.env` (chmod 600, NEVER commit) |
| Container name | `ultraprocessed-backend` |
| Host port | `8001` (8000 is taken by gluetun) |
| Container port | `8000` (don't change - matches the image's healthcheck) |
| Volume | `ultraprocessed-backend-data` (named volume, holds SQLite DB) |
| GHCR image | `ghcr.io/clinteastman/ultraprocessed-backend:latest` (public) |
| GitHub repo | https://github.com/Clinteastman/ultraprocessed |
| Build workflow | `.github/workflows/build-image.yml` (auto on push to main) |

## Cloudflare Access posture

Two Access apps cover this hostname, by path-precedence:

1. `ultraprocessed.mossom.co.uk/api` -> **bypass everyone** (no auth, lets the phone reach `/api/v1/*`)
2. `ultraprocessed.mossom.co.uk` -> "allow cmoss" by email (browser dashboard at `/` requires login)

The HMAC token in `.env` (`ULTRAPROCESSED_TOKEN_SECRET`) is the actual API auth - Cloudflare Access bypass on `/api` doesn't weaken security. The token is per-device; revoke compromised devices via the dashboard.

To inspect or change the policy, look in the homelab repo for the script:

```bash
# k12-homelab repo:
proxmox/cf-tunnel.py        # add/list/remove tunnel hostname + DNS + Access app (per-domain)
proxmox/cf-bypass-api.py    # add a path-bypass policy for /api on a hostname
```

Both run on the Proxmox host (`ssh root@192.168.50.55`) and read the API token from `/root/.cloudflare-api-token`.

## SSH in and look around

```bash
ssh root@192.168.50.60
cd /opt/stacks/ultraprocessed/deploy

docker ps --filter name=ultraprocessed
docker compose -f compose.prod.yml ps
```

If `Status` is `(healthy)` you're good. The container's healthcheck hits `/api/v1/health` every 30s.

## Logs

```bash
# Live tail
docker compose -f compose.prod.yml logs -f --tail=200

# Last 500 lines, no follow
docker logs --tail=500 ultraprocessed-backend

# Just errors / warnings
docker logs ultraprocessed-backend 2>&1 | grep -iE 'error|warn|exception|traceback'

# What happened around a specific time
docker logs --since=2h ultraprocessed-backend
```

The image runs uvicorn; logs are stdout/stderr only (no log files inside the container). All FastAPI request lines, app errors, and startup messages land in `docker logs`.

## Health checks the phone won't see

- Container healthcheck status: `docker inspect --format '{{.State.Health.Status}}' ultraprocessed-backend` -> `healthy` / `starting` / `unhealthy`
- API health (from inside K12): `curl -fsS http://localhost:8001/api/v1/health`
- API health (external, through CF): `curl -fsS https://ultraprocessed.mossom.co.uk/api/v1/health`
- Dashboard root: open `https://ultraprocessed.mossom.co.uk/` in a browser (needs CF Access login)

If health is fine internally but the public URL fails, the issue is between K12 and Cloudflare:
1. Is `cloudflared` container up? `docker ps --filter name=cloudflared`
2. Is the tunnel healthy in CF dashboard? Tunnels -> your tunnel -> active connectors
3. Is the route still in ingress? `ssh root@192.168.50.55 "/root/cf-tunnel.py list"` should include `ultraprocessed.mossom.co.uk`

## Restart, recreate, stop

```bash
# Soft restart (keeps container, keeps volume)
docker compose -f compose.prod.yml restart

# Recreate (rebuilds container from current image, keeps volume)
docker compose -f compose.prod.yml up -d --force-recreate

# Stop without removing
docker compose -f compose.prod.yml stop

# Stop AND remove container (volume + image survive)
docker compose -f compose.prod.yml down

# Nuke the data volume too (LOSES THE SQLITE DB - back up first)
docker compose -f compose.prod.yml down -v
```

## Bug-testing iteration loop

Active development pattern - edit, push, deploy, watch, repeat. Use this when you're iterating, not for production rollouts.

### Standard cycle (push to main -> Actions -> deploy)

GitHub Actions takes ~5 min to build amd64+arm64 and push to GHCR. Pulling before that finishes gets you the previous image with a misleading "no changes" message.

The one-liner that does it all - push, wait for the build, deploy on K12:

```bash
deploy/redeploy.sh                  # waits for Actions, pulls, up -d, tails 20 lines
deploy/redeploy.sh --no-wait        # if you've already waited; pull+up immediately
deploy/redeploy.sh --no-pull        # compose changes only, don't pull a new image
```

Or by hand:

```bash
git push
gh run watch --exit-status $(gh run list --limit 1 --json databaseId --jq '.[0].databaseId')
ssh root@192.168.50.60 "cd /opt/stacks/ultraprocessed && git pull && cd deploy \
  && docker compose -f compose.prod.yml pull \
  && docker compose -f compose.prod.yml up -d \
  && docker logs --tail=20 ultraprocessed-backend"
```

### What SHA is running right now

Three ways, increasingly precise:

```bash
# Image tag (probably ":latest", not informative for dev work)
ssh root@192.168.50.60 "docker inspect ultraprocessed-backend --format '{{.Config.Image}}'"

# Image content hash (changes every build, not human-readable)
ssh root@192.168.50.60 "docker inspect ultraprocessed-backend --format '{{.Image}}'"

# Git SHA the image was built from (what you actually want)
ssh root@192.168.50.60 "docker inspect ultraprocessed-backend \
  --format '{{index .Config.Labels \"org.opencontainers.image.revision\"}}'"
```

The last one is set by `docker/metadata-action` automatically and gives you the commit SHA. If it's empty, the build predates the OCI labels and you're flying blind - rebuild and redeploy.

### Two-shell pattern: tail-and-deploy

Run these in two separate terminals:

```bash
# shell 1 - leave running
ssh root@192.168.50.60 "docker logs -f --tail=50 ultraprocessed-backend"

# shell 2 - your edit/push loop
deploy/redeploy.sh
```

When the deploy completes, the new container starts up and shell 1 immediately shows the startup logs. No SSH dance per cycle.

### Skipping GHCR for tight loops (build on the host)

When you're chasing a specific bug and 5-min Actions cycles are too slow, build the image directly on K12 against your local code:

```bash
# Sync your in-progress code to the host (rsync or git push to a working branch)
rsync -avz --exclude=.git --exclude=node_modules ./ root@192.168.50.60:/opt/stacks/ultraprocessed/

# Build + run on the host, bypassing GHCR
ssh root@192.168.50.60 "cd /opt/stacks/ultraprocessed && \
  docker build -t ultraprocessed-backend:dev -f backend/Dockerfile . && \
  cd deploy && \
  ULTRAPROCESSED_IMAGE=ultraprocessed-backend:dev \
    docker compose -f compose.prod.yml up -d --force-recreate"
```

When the bug's fixed, push to GitHub, wait for Actions, then `unset ULTRAPROCESSED_IMAGE` (or remove from `.env`) and `redeploy.sh` to go back to the GHCR image.

The dev image and the GHCR image use the same volume, so SQLite data is preserved across the back-and-forth.

### Fast rollback when an iteration breaks things

```bash
# What images are still on the host?
ssh root@192.168.50.60 "docker images ghcr.io/clinteastman/ultraprocessed-backend"

# Pin .env to a specific SHA tag (older successful build)
ssh root@192.168.50.60 "echo 'ULTRAPROCESSED_IMAGE=ghcr.io/clinteastman/ultraprocessed-backend:sha-<previous-sha>' \
  >> /opt/stacks/ultraprocessed/deploy/.env"
ssh root@192.168.50.60 "cd /opt/stacks/ultraprocessed/deploy && \
  docker compose -f compose.prod.yml pull && \
  docker compose -f compose.prod.yml up -d --force-recreate"
```

After fixing forward, **remove the pin** from `.env` so future deploys pick up `:latest` again.

### Things to watch for during iteration

- **DB schema migrations.** This app creates schema on first boot but doesn't auto-migrate existing rows. If you add a non-nullable column without a default, the existing SQLite DB will refuse to start. Either ship a migration step in code, or `docker compose down -v` and lose the data (only acceptable in dev, never with real user data).
- **Stale `:latest` tag in Docker.** `docker compose pull` pulls if the remote digest changed. If for some reason it's not pulling fresh, force it: `docker pull ghcr.io/clinteastman/ultraprocessed-backend:latest --disable-content-trust=false`.
- **Healthcheck race during recreate.** `up -d` recreates and the healthcheck has a 20s start period. If you're spamming redeploys you can hit a brief window where the container is `starting` and `/api/v1/health` returns connection refused at the cloudflared layer. Wait for `(healthy)` in `docker ps` before retrying.
- **Env vars from `.env` only reload on `up -d`, not `restart`.** If you're tweaking `.env`, always `up -d` (which recreates), never `restart` (which just restarts the existing container with stale env).

## Updating to a newer image

The GitHub Actions workflow rebuilds `:latest` on every push to main, plus a `:sha-<commit>` tag. To pull and roll forward:

```bash
ssh root@192.168.50.60
cd /opt/stacks/ultraprocessed
git pull                                                # pull repo updates (compose changes, etc)
cd deploy
docker compose -f compose.prod.yml pull                 # pull latest image
docker compose -f compose.prod.yml up -d                # recreate container with new image
docker compose -f compose.prod.yml logs -f --tail=50    # watch it come up
```

If you want a specific commit instead of `:latest`, set `ULTRAPROCESSED_IMAGE=ghcr.io/clinteastman/ultraprocessed-backend:sha-<full-commit-sha>` in `.env`, then `up -d`.

## Rolling back

GHCR keeps every SHA tag forever. To revert:

```bash
# Find the previous good tag
gh api /users/Clinteastman/packages/container/ultraprocessed-backend/versions \
  --jq '.[].metadata.container.tags' | head -10

# Pin .env to that SHA
echo "ULTRAPROCESSED_IMAGE=ghcr.io/clinteastman/ultraprocessed-backend:sha-<sha>" >> .env

# Recreate
docker compose -f compose.prod.yml pull
docker compose -f compose.prod.yml up -d
```

The DB volume survives so any data captured on the bad version stays. If a DB migration in the bad version is the issue, restore from a backup before rolling back.

## Backups

There's no scheduled backup yet. Manual snapshot of the SQLite DB:

```bash
docker compose -f compose.prod.yml exec backend \
  sqlite3 /app/data/ultraprocessed.db ".backup /app/data/backup.db"
docker cp ultraprocessed-backend:/app/data/backup.db /opt/stacks/ultraprocessed/backups/up-$(date +%F-%H%M).db
```

To wire up the cron from `deploy.md`, drop it in `/etc/cron.d/ultraprocessed-backup` on the host and create `/backup/` first.

## Editing config

Anything in `.env` (token, port, optional relay keys) takes effect on `up -d`:

```bash
vim /opt/stacks/ultraprocessed/deploy/.env
docker compose -f compose.prod.yml up -d        # recreates with new env
```

Anything in compose.prod.yml is tracked in git - edit on the host with `git pull` after pushing the change upstream, OR edit in place and `git stash`/`git checkout` later.

The HMAC `ULTRAPROCESSED_TOKEN_SECRET` is **the** secret. If you rotate it, **every paired device gets logged out** and has to re-pair via QR.

## Common failure modes (and what to check)

| Symptom | First check | Fix |
|---|---|---|
| Container restart-looping | `docker logs ultraprocessed-backend 2>&1 \| tail -50` | usually missing/wrong env var; fix `.env`, `up -d` |
| Healthcheck flapping `starting`/`unhealthy` | `docker inspect ultraprocessed-backend \| jq .[].State.Health` shows last probes | DB locked? container OOMing? `docker stats ultraprocessed-backend` |
| `/api/v1/health` returns 502 externally | `curl http://localhost:8001/api/v1/health` from K12 | if local works, cloudflared can't reach K12. Check the tunnel + ingress |
| `/api/v1/health` returns CF Access 302 | CF Access bypass app missing or precedence wrong | re-run `/root/cf-bypass-api.py ultraprocessed.mossom.co.uk /api` on Proxmox |
| Phone says "Backend unreachable" | external `/api/v1/health` returns 200? | if yes, it's the token: re-pair. If no, fix the tunnel first |
| Sync goes through but `/today` empty | dashboard token != phone token | regenerate both from same dashboard origin |
| New build didn't deploy after push | `gh run list --repo Clinteastman/ultraprocessed --limit 3` | if Actions failed, fix and re-push. If success but old image, `docker compose pull && up -d` |
| Port 8001 already in use after reboot | another container grabbed it | change `ULTRAPROCESSED_PORT` in `.env`, `up -d`, AND update CF tunnel ingress URL via `/root/cf-tunnel.py` |

## Where the secrets live

- `.env` (host: `/opt/stacks/ultraprocessed/deploy/.env`) - HMAC token, not committed
- `/root/.cloudflare-api-token` on Proxmox - CF API token used by `cf-tunnel.py` and `cf-bypass-api.py`
- `CF_TUNNEL_TOKEN` in `/opt/stacks/cloudflared/.env` - the cloudflared connector token
- GHCR image is public, no auth needed for pulls

If any of these leak, rotate them in this order: HMAC token (re-pair all devices), CF API token (regen in CF dashboard, update file), CF Tunnel token (only if compromised - regen ends the tunnel until cloudflared has the new value).

## Things you should NOT do without asking the user first

- Wipe the data volume (`down -v`) - irrecoverable user data loss without a backup
- Change `ULTRAPROCESSED_TOKEN_SECRET` - logs every device out
- Modify Cloudflare Access apps in ways that expose the dashboard (`/`) publicly
- Push a new build that changes the DB schema without a verified backup first
- Force-push to `main` on the GitHub repo

## Where this fits with the rest of the homelab

| | |
|---|---|
| Sibling stacks on LXC 101 | immich, dawarich, dockge, homepage, qbit-gluetun, crafty, cloudflared, uptime-kuma, scraper, rclone-onedrive |
| LXC 101 sizing | docker host runs everything above + this; check `docker stats` if things slow down |
| Tunnel | `cloudflared` container on LXC 101 (token mode, configured in CF Zero Trust dashboard) |
| Auth control | `proxmox/cf-tunnel.py` and `proxmox/cf-bypass-api.py` in the [k12-homelab repo](https://github.com/Clinteastman/k12-homelab) |
| Runbook | `runbook/k12-runbook.md` in the same repo - canonical "what's where" |

When in doubt: read `k12-homelab/runbook/k12-runbook.md` first. That doc owns the source-of-truth view of the homelab. This `operations.md` is just the slice that's specific to this app.
