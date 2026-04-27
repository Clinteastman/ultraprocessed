# Architecture

## System

```
              +---------------------+
              |    Android app      |
              |  (Kotlin + Compose) |
              +---------+-----------+
                        |
              CameraX + ML Kit (on-device)
                        |
   +--------------------+--------------------+
   |                    |                    |
   v                    v                    v
Open Food Facts     Backend API         LLM provider
(direct or via      /api/v1/...         (Anthropic OR
 backend cache)      |                   OpenAI-compat)
                     |
            +--------+--------+
            |  FastAPI + SQL  |
            |  + static dash  |
            +--------+--------+
                     |
            +--------+--------+
            |   Web dashboard  |
            |   (SvelteKit)    |
            +------------------+
                     |
            +--------+--------+
            |  HA integration  |
            |  (custom_comp.)  |
            +------------------+
```

The Android app is the source of truth offline; the backend is the source of truth across devices and powers the dashboard and HA integration. Either can call the LLM provider; the user picks at runtime.

## Components

### Android app
Phone-side scan/log experience. Holds local Room DB. Speaks to Open Food Facts (direct or via backend cache), the configured LLM provider, and the backend (when configured). Settings screen exposes the LLM provider config (so the app is shareable: drop in any compatible key).

### Backend
FastAPI service. Owns the canonical food entries, consumption logs, and fasting state. Caches Open Food Facts responses. Optionally relays LLM calls so the phone never holds a key. Serves a built SvelteKit dashboard from `/`, API from `/api/v1`. SQLite by default; Postgres via `DATABASE_URL`.

### Dashboard
SvelteKit static build hosted by the backend. Routes: today, history, charts, map, foods, settings. Talks to backend over `/api/v1`. Live updates via SSE on `/api/v1/events`.

### Home Assistant integration
HACS-installable `custom_components/ultraprocessed/`. Uses `DataUpdateCoordinator` to poll `/api/v1/ha/snapshot`. Exposes calories, NOVA average, UPF calories, meal count, last-meal sensor, next-eat-at timestamp, and a fasting binary sensor. Provides `ultraprocessed.log_food` service for manual entries from HA.

## Data flow

### Barcode path
1. CameraX preview runs the ML Kit barcode analyzer in real time.
2. On detect, the app GETs `/api/v1/openfoodfacts/{code}` (or hits OFF directly if no backend).
3. Backend cache miss -> fetch `https://world.openfoodfacts.org/api/v2/product/{code}.json`, store, return.
4. Map response to `FoodAnalysis { name, brand, novaClass, kcalPer100g, ingredients }`. Show ResultScreen.

### OCR path (label without a useful barcode)
1. User taps shutter; latest frame captured at full res.
2. ML Kit on-device text recognition extracts blocks.
3. Heuristic: if the text looks like an ingredient list ("ingredients:" prefix, comma-separated tokens, common additive markers), send TEXT to `analyzer.analyzeText(extracted)`.
4. Analyzer returns structured `FoodAnalysis`. Show ResultScreen.

### Vision path (no useful text)
1. User taps shutter; frame captured.
2. Send image bytes to `analyzer.analyzeImage(bytes)`.
3. Analyzer returns `FoodAnalysis`. Show ResultScreen.

In all three paths, the user can tap "I ate it" and pick a percentage. A `ConsumptionLog` is written to Room and queued for sync. Sync worker pushes to backend when online.

## Provider abstraction

Both the phone and backend implement the same logical interface:

```
FoodAnalyzer:
  analyzeText(text: String): FoodAnalysis
  analyzeImage(bytes: ByteArray, mediaType: String): FoodAnalysis
```

`FoodAnalysis`:
```
{
  name: String,
  brand: String?,
  novaClass: 1..4,
  novaRationale: String,
  kcalPer100g: Double?,
  kcalPerUnit: Double?,
  servingDescription: String?,
  ingredients: List<String>,
  confidence: 0.0..1.0
}
```

Adapters:
- `AnthropicAnalyzer` - Claude messages API; vision via image content blocks; system prompt locks the JSON schema.
- `OpenAICompatibleAnalyzer` - Chat Completions with `response_format: json_schema`; vision via OpenAI-style image_url content. Configurable base URL + model.

Settings model: `provider`, `baseUrl`, `apiKey`, `model`. Defaults bake in for known providers (Anthropic, OpenAI, Groq, OpenRouter, NVIDIA NIM, Ollama@localhost:11434).

Optional backend relay: phone POSTs to `/api/v1/analyze/{text|image}` instead of calling the provider directly. Useful when the user prefers to keep keys server-side or wants caching.

## Sync model

- Phone writes locally first; `sync_state` column tracks `pending|synced|error`.
- A WorkManager job pushes `POST /api/v1/consumption` and `POST /api/v1/foods` for pending rows when online.
- Backend assigns canonical IDs; phone reconciles by `(client_uuid)`.
- Conflict resolution: last-writer-wins by `updated_at`. Single-user mode means conflicts are rare (same person, multiple devices).
- HA never writes through to the phone; HA changes go via backend, phone picks them up on next sync.

## Auth

- Per-device bearer token. Generated by `POST /api/v1/auth/token` after one-time pairing (e.g., a code shown in the dashboard). Stored on phone in `EncryptedSharedPreferences`.
- Cloudflare Access can wrap the whole backend; documented in `deploy/`. Backend trusts `Cf-Access-Authenticated-User-Email` when configured.
- Multi-user user accounts are post-Phase-6.

## Storage

Phone (Room):
- `food_entry` (client_uuid PK, server_id, name, brand, barcode, nova_class, kcal_per_100g, kcal_per_unit, image_path, ingredients_json, source, created_at, updated_at, sync_state)
- `consumption_log` (client_uuid PK, server_id, food_client_uuid, percentage, eaten_at, lat, lng, location_label, kcal_consumed_snapshot, sync_state)
- `fasting_profile` (id, schedule_type, eating_window_start, eating_window_end, active)

Backend (SQLModel):
- `users`, `devices`
- `food_entries`, `consumption_logs`, `fasting_profiles` (mirroring phone but with `user_id` FK)
- `openfoodfacts_cache` (barcode PK, payload JSON, fetched_at, expires_at)

## Open questions

Tracked in the plan file under "Open decisions". License, multi-user, and deployment splitting are deferred until Phase 6.
