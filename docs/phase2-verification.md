# Phase 2 verification

End-to-end test: phone -> backend -> dashboard.

## Bring up the backend + dashboard

```
cd dashboard && pnpm install && pnpm build
cd ../deploy
cp .env.example .env
# generate a real secret:
openssl rand -hex 32 | xargs -I {} sed -i "s/change-me-please/{}/" .env
docker compose up -d --build
```

Then open `http://localhost:8000/` and you should see the (empty) Today view.

The container exposes:
- `GET /api/v1/health` returns `{"status": "ok"}`.
- `/` serves the SvelteKit SPA.
- All other `/api/v1/*` routes (foods, consumption, fasting, openfoodfacts, dashboard, ha) are listed in the OpenAPI doc at `/docs`.

## Pair the dashboard

1. Open `http://localhost:8000/settings`.
2. Leave **Backend URL** blank (the dashboard is being served from the same origin).
3. Click **Pair this browser** with `device_name = "dashboard"`.
4. Click **Test connection** - should report device + user IDs.

## Pair the phone

In the Android app's Settings:
1. Set **Backend URL** to `http://<host-lan-ip>:8000` (or your Cloudflare tunnel hostname).
2. Use the dashboard's Settings page to issue a separate phone token, OR run:
   ```
   curl -X POST http://localhost:8000/api/v1/auth/token \
     -H "Content-Type: application/json" \
     -d '{"device_name":"phone"}'
   ```
   Paste the returned `token` into the phone's **Device token** field.

After this, every "I ate it" log on the phone triggers a sync to the backend (the SyncCoordinator runs at log time and at app launch).

## Verify

1. **Phone -> backend**: scan a barcoded item, log it 100%. On the dashboard's `/history` page (refresh), the entry appears.
2. **Today aggregations**: dashboard `/` shows kcal vs target, NOVA breakdown, macro and micro adequacy bars colour-coded low / ok / high.
3. **Nutrient adequacy**: log a single banana - protein/iron/B6 bars should rise; calcium/B12/D should stay near zero (low / amber).
4. **Fasting**: dashboard's `/api/v1/fasting/profile` accepts a 16:8 profile via PUT; `/api/v1/fasting/state` reports `currently_fasting` + `next_eat_at`.
5. **HA polling target**: `curl -H "Authorization: Bearer $TOKEN" http://localhost:8000/api/v1/ha/snapshot` returns the compact blob HA's coordinator will consume in Phase 5.

## Cloudflare tunnel

In your Cloudflare dashboard, create a tunnel target pointing at `http://<homelab-host>:8000`. Update the phone's **Backend URL** to the public hostname. The token does not change.

## Known gaps (closed in subsequent tasks)

- Dashboard charts (NOVA distribution over time, calorie trend, top UPF offenders) and the map view.
- HA custom_component (Phase 5).
- Pair flow on the phone (currently you copy the token manually; phone-side Pair button is small follow-up).
- Image bytes (scan photos) are stored locally on the phone but not uploaded to the backend yet; only the metadata syncs.
