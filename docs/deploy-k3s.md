# Deploying Ultraprocessed to K3s

This is a self-contained runbook for deploying the Ultraprocessed backend (FastAPI + baked-in SvelteKit dashboard) to a K3s cluster, exposed via Cloudflare Tunnel. It's written so you can hand it to a Claude instance running in your homelab repo and have it execute the steps end-to-end.

## What you'll end up with

- One Deployment running the backend image, exposing the API at `/api/v1/*` and the dashboard at `/`.
- A 2 GiB PVC backed by K3s' `local-path` storage class (override-able) holding the SQLite database.
- A ClusterIP Service called `ultraprocessed-backend` in the `ultraprocessed` namespace.
- A Cloudflare Tunnel route exposing `https://ultraprocessed.<your-domain>` to the service.
- A GitHub Actions workflow that builds and pushes a multi-arch (`amd64` + `arm64`) image to `ghcr.io/<your-gh-username>/ultraprocessed-backend` on every push to `main`.

## Prerequisites

- K3s cluster reachable via `kubectl` (you already have this).
- A Cloudflare Tunnel running on the cluster (you already have this).
- A GitHub repo for the project, public or private. Private repos need an `imagePullSecret` (covered below).
- `kubectl`, `kustomize`, `openssl` available locally (or in the homelab Claude's environment).

## Step 1 - Push the repo to GitHub

If the repo isn't on GitHub yet:

```sh
gh repo create ultraprocessed --private --source=. --remote=origin --push
```

If it's already there, make sure `main` is up to date.

## Step 2 - Build and publish the image

The included workflow at `.github/workflows/build-image.yml` builds and pushes `ghcr.io/<your-gh-username>/ultraprocessed-backend:latest` (plus a SHA tag) on every push to `main`. Trigger it once now:

```sh
git push origin main
gh run watch  # follow the build
```

When it finishes, confirm the image is published:

```sh
gh api /user/packages/container/ultraprocessed-backend/versions --jq '.[0].metadata.container.tags'
```

If the GHCR repo defaulted to private and your cluster can't pull, either flip it to public:

- GitHub -> your-username -> Packages -> ultraprocessed-backend -> Package settings -> Change visibility -> Public.

Or create an `imagePullSecret` in the cluster (see Appendix A).

### Alternative: build locally and push

```sh
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t ghcr.io/<your-gh-username>/ultraprocessed-backend:latest \
  -f backend/Dockerfile \
  --push \
  .
```

## Step 3 - Update the image reference

Edit `deploy/k8s/deployment.yaml` and replace `ghcr.io/clinteastman/ultraprocessed-backend` with your own GHCR namespace.

## Step 4 - Generate the secret

```sh
cp deploy/k8s/secret.example.yaml deploy/k8s/secret.yaml
TOKEN=$(openssl rand -hex 32)
sed -i "s|REPLACE-ME-with-openssl-rand-hex-32|$TOKEN|" deploy/k8s/secret.yaml
```

`deploy/k8s/secret.yaml` is gitignored. Keep it out of version control.

## Step 5 - Apply the manifests

```sh
kubectl apply -k deploy/k8s/
kubectl -n ultraprocessed get pods -w
```

The pod should reach `Running` and pass its readiness probe within ~30 seconds. If it doesn't, `kubectl -n ultraprocessed logs deploy/ultraprocessed-backend` will show why.

Sanity check from inside the cluster:

```sh
kubectl -n ultraprocessed run -i --rm --restart=Never curl --image=curlimages/curl --command -- \
  curl -fsS http://ultraprocessed-backend/api/v1/health
```

You should see `{"status":"ok","version":"0.1.0"}`.

## Step 6 - Expose via Cloudflare Tunnel

The service to expose is:

```
http://ultraprocessed-backend.ultraprocessed.svc.cluster.local:80
```

Pick whichever pattern you already use:

### Cloudflare Tunnel CRD (Operator)

```yaml
apiVersion: cloudflare.com/v1
kind: TunnelBinding
metadata:
  name: ultraprocessed
  namespace: ultraprocessed
spec:
  subjects:
    - kind: Service
      name: ultraprocessed-backend
  template:
    spec:
      hostname: ultraprocessed.<your-domain>
      service: http://ultraprocessed-backend:80
```

### Plain cloudflared ConfigMap

Add to your existing `config.yaml` ingress, before the catch-all `404`:

```yaml
- hostname: ultraprocessed.<your-domain>
  service: http://ultraprocessed-backend.ultraprocessed.svc.cluster.local:80
```

Then bounce cloudflared:

```sh
kubectl -n cloudflared rollout restart deploy/cloudflared
```

A reference snippet is at `deploy/k8s/cloudflare-tunnel.example.yaml`.

## Step 7 - Verify externally

```sh
curl -fsS https://ultraprocessed.<your-domain>/api/v1/health
```

Open `https://ultraprocessed.<your-domain>/` in a browser. The dashboard should load and silently auto-pair itself.

## Step 8 - Pair the phone

On the dashboard at `https://ultraprocessed.<your-domain>/settings`, click **Pair a device**. The QR code now contains the public URL (not localhost) so the phone can reach it.

In the Android app: Settings -> **Scan pairing QR**. Camera opens, point at the QR. Backend URL + token populate. Tap **Save**. The cloud icon top-right on the camera screen should flip to green within a few seconds.

## Updating

The workflow rebuilds on every push to `main`. To pick up a new image:

```sh
kubectl -n ultraprocessed rollout restart deploy/ultraprocessed-backend
```

Or pin to a specific commit by editing the `images:` block in `deploy/k8s/kustomization.yaml`.

## Backups

The PVC holds a single SQLite file at `/app/data/ultraprocessed.db`. Easiest backup:

```sh
kubectl -n ultraprocessed exec deploy/ultraprocessed-backend -- \
  sqlite3 /app/data/ultraprocessed.db ".backup /tmp/backup.db"
kubectl -n ultraprocessed cp ultraprocessed-backend-<pod-id>:/tmp/backup.db ./ultraprocessed-$(date +%F).db
```

Or schedule via a CronJob - happy to add one if you want.

## Appendix A: imagePullSecret for private GHCR

```sh
kubectl -n ultraprocessed create secret docker-registry ghcr-pull \
  --docker-server=ghcr.io \
  --docker-username=<your-gh-username> \
  --docker-password=<a-github-pat-with-read:packages> \
  --docker-email=<your-email>
```

Then add to `deployment.yaml` under `spec.template.spec`:

```yaml
imagePullSecrets:
  - name: ghcr-pull
```

## Appendix B: Switching to Postgres

When you outgrow SQLite, swap the `ULTRAPROCESSED_DATABASE_URL` env var on the Deployment to a `postgresql+psycopg://...` URL pointing at a Postgres in the cluster (or external). The PVC can then be deleted; SQLModel will create the Postgres schema automatically on first boot.

## Common failures

- **Pod CrashLoopBackOff with `ULTRAPROCESSED_TOKEN_SECRET` error**: secret didn't get created or has the placeholder value. Re-run Step 4.
- **`ImagePullBackOff`**: registry visibility or `imagePullSecret` missing (Appendix A).
- **Phone "Backend unreachable"**: the QR was generated while you were on `localhost`. Always generate from the public hostname after the tunnel is up.
- **Sync goes through but dashboard `/today` is empty**: dashboard token belongs to a different user than the phone token. In single-user mode all tokens map to the same user, so this shouldn't happen - but if it does, regenerate both tokens from the same dashboard origin.
