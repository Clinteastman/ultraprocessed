# Deploying Ultraprocessed to a Docker host

This is a self-contained runbook for running the Ultraprocessed backend (FastAPI + baked-in SvelteKit dashboard) on a plain Docker host - in our case **K12**, a Proxmox VM/LXC running Docker + cloudflared. Hand this to a Claude instance running in your homelab repo and have it execute end-to-end.

## What you'll end up with

- One container running `ghcr.io/<your-gh-username>/ultraprocessed-backend:latest`, exposing the API at `/api/v1/*` and the dashboard at `/`.
- A named Docker volume `ultraprocessed-backend-data` holding the SQLite database, persisting across image upgrades.
- A Cloudflare Tunnel route exposing `https://ultraprocessed.<your-domain>` to that container.
- A GitHub Actions workflow that builds and pushes a multi-arch (`amd64` + `arm64`) image to GHCR on every push to `main`, so future updates are `docker compose pull && docker compose up -d`.

## Prerequisites

- K12 has Docker + Docker Compose installed and reachable via SSH (or `pct exec` / `qm guest exec` if it's a Proxmox container/VM).
- cloudflared is already running on K12 (or somewhere with network access to K12) and you control its config.
- A GitHub repo for the project. Public, or private with a personal access token K12 can use to pull from GHCR.

## Step 1 - Push the repo to GitHub

If the repo isn't on GitHub yet:

```sh
gh repo create ultraprocessed --public --source=. --remote=origin --push
```

Public is easiest because GHCR images inherit visibility from the repo and K12 can pull without credentials. Use `--private` if you'd rather, and follow Appendix A.

## Step 2 - Build and publish the image

The included workflow at `.github/workflows/build-image.yml` builds and pushes `ghcr.io/<your-gh-username>/ultraprocessed-backend:latest` (plus a SHA tag) on every push to `main`. Trigger it once now:

```sh
git push origin main
gh run watch  # follow the build
```

When it finishes, confirm the image is published:

```sh
gh api /user/packages/container/ultraprocessed-backend/versions \
  --jq '.[0].metadata.container.tags'
```

The first time GHCR creates the package it usually defaults to private. Flip it to public:

GitHub -> your-username -> Packages -> ultraprocessed-backend -> Package settings -> Change visibility -> Public.

(Or keep it private and follow Appendix A.)

## Step 3 - Update the image reference (if your username isn't `clinteastman`)

Edit `deploy/compose.prod.yml` and change the default image to your GHCR namespace:

```yaml
image: ${ULTRAPROCESSED_IMAGE:-ghcr.io/<your-gh-username>/ultraprocessed-backend:latest}
```

Or skip editing and override per-host with `ULTRAPROCESSED_IMAGE` in `.env` (Step 5).

## Step 4 - Copy the deploy bits to K12

On K12, create a folder somewhere stable (e.g. `/opt/ultraprocessed/`). Two things need to land there:

- `compose.prod.yml`
- `.env`

Easiest is to clone the repo on K12 (so future updates are `git pull`):

```sh
ssh k12
sudo mkdir -p /opt/ultraprocessed && sudo chown $USER /opt/ultraprocessed
cd /opt/ultraprocessed
git clone https://github.com/<your-gh-username>/ultraprocessed.git .
cd deploy
```

Or just `scp` the two files:

```sh
scp deploy/compose.prod.yml deploy/.env.example k12:/opt/ultraprocessed/
```

## Step 5 - Generate the .env on K12

```sh
cd /opt/ultraprocessed/deploy   # or wherever compose.prod.yml lives
cp .env.example .env
TOKEN=$(openssl rand -hex 32)
sed -i "s|change-me-please|$TOKEN|" .env
cat .env  # sanity check
```

Optional: pin the image to a specific commit instead of `:latest` for reproducible deploys, by adding to `.env`:

```sh
echo "ULTRAPROCESSED_IMAGE=ghcr.io/<your-gh-username>/ultraprocessed-backend:sha-<commit-sha>" >> .env
```

## Step 6 - Pull and start the container

```sh
docker compose -f compose.prod.yml pull
docker compose -f compose.prod.yml up -d
docker compose -f compose.prod.yml logs -f --tail=50
```

The container should become `healthy` within ~30 seconds. Sanity check from K12:

```sh
curl -fsS http://localhost:8000/api/v1/health
# {"status":"ok","version":"0.1.0"}
```

If the port differs, you set `ULTRAPROCESSED_PORT` in `.env`.

## Step 7 - Wire up Cloudflare Tunnel

Add a route to your existing cloudflared config. Two common patterns:

### Plain `config.yml` (cloudflared running natively or in a container)

In your cloudflared `config.yml`, before the catch-all `404`:

```yaml
ingress:
  # ... your existing routes ...
  - hostname: ultraprocessed.<your-domain>
    service: http://k12.<your-internal-domain>:8000
    # If cloudflared runs ON K12 itself:
    # service: http://localhost:8000
  - service: http_status:404
```

Then bounce cloudflared so it picks up the new ingress. If cloudflared is running as a Docker container:

```sh
docker restart cloudflared
```

### Cloudflare Zero Trust dashboard (no local config.yml)

Tunnels -> your tunnel -> Public Hostnames -> Add a public hostname:

- Subdomain: `ultraprocessed`
- Domain: `<your-domain>`
- Service: `HTTP`, `k12.<your-internal-domain>:8000` (or `localhost:8000` if cloudflared is on K12).

## Step 8 - Verify externally

```sh
curl -fsS https://ultraprocessed.<your-domain>/api/v1/health
```

Open `https://ultraprocessed.<your-domain>/` in a browser. The dashboard should load and silently auto-pair itself.

## Step 9 - Pair the phone

On the dashboard at `https://ultraprocessed.<your-domain>/settings`, click **Pair a device**. The QR code now contains the public URL (not localhost) so the phone can reach it.

In the Android app: Settings -> **Scan pairing QR**. Camera opens, point at the QR. Backend URL + token populate. Tap **Save**. The cloud icon top-right on the camera screen should flip to green within a few seconds, and any food you've already logged offline syncs.

## Updating

Whenever main is rebuilt by Actions:

```sh
ssh k12
cd /opt/ultraprocessed/deploy
docker compose -f compose.prod.yml pull
docker compose -f compose.prod.yml up -d
```

The named volume `ultraprocessed-backend-data` survives, so the SQLite database isn't touched.

## Backups

The PVC holds a single SQLite file at `/app/data/ultraprocessed.db`. One-shot backup from K12:

```sh
docker compose -f compose.prod.yml exec backend \
  sqlite3 /app/data/ultraprocessed.db ".backup /app/data/backup.db"
docker cp ultraprocessed-backend:/app/data/backup.db ./ultraprocessed-$(date +%F).db
```

Or schedule via `cron` on K12:

```cron
# /etc/cron.d/ultraprocessed-backup
0 3 * * * root cd /opt/ultraprocessed/deploy && docker compose -f compose.prod.yml exec -T backend sqlite3 /app/data/ultraprocessed.db ".backup /app/data/backup.db" && docker cp ultraprocessed-backend:/app/data/backup.db /backup/ultraprocessed-$(date +\%F).db
```

## Appendix A - Pulling from a private GHCR image

Create a GitHub Personal Access Token with `read:packages` scope, then on K12:

```sh
echo "<your-pat>" | docker login ghcr.io -u <your-gh-username> --password-stdin
```

Docker Compose will reuse those credentials on subsequent pulls. To rotate, repeat with the new PAT.

## Appendix B - Switching to Postgres

When you outgrow SQLite, set `ULTRAPROCESSED_DATABASE_URL` in `.env` to a `postgresql+psycopg://...` URL pointing at a Postgres elsewhere on K12 or in your network. The backend creates the schema automatically on first boot. The SQLite volume can be removed afterwards.

## Common failures

- **`docker compose pull` returns `denied`**: GHCR image is private and K12 isn't logged in. Either flip the package to public (Step 2) or follow Appendix A.
- **Container starts but `curl` returns connection refused**: another service is bound to port 8000. Set `ULTRAPROCESSED_PORT=<free port>` in `.env` and restart.
- **Cloudflared sees the route but returns 502**: cloudflared can't reach K12:8000. From the host running cloudflared, `curl http://k12:8000/api/v1/health` to confirm.
- **Phone "Backend unreachable"**: the QR was generated while you were on `localhost`. Always generate from the public hostname after the tunnel is up.
- **Sync goes through but dashboard `/today` is empty**: dashboard token belongs to a different user than the phone token. In single-user mode all tokens map to the same user, so this shouldn't happen - regenerate both from the same dashboard origin if it does.
