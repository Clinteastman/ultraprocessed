# Ultraprocessed

Point your phone at food and find out how ultra-processed it is.

A self-hostable food tracker built around the [NOVA classification](https://en.wikipedia.org/wiki/Nova_classification) system. Scan barcodes, OCR ingredient labels, or photograph unlabeled food (an apple, a plate of pasta) and get an instant 1-4 NOVA score plus calorie estimate. Log what you actually ate (with a percentage), see your intake over time, and respect your fasting windows.

> **Status:** early development. Phase 1 (phone-only MVP) is in progress.

## Components

| Component | Path | Stack |
| --- | --- | --- |
| Android app | [`android/`](android/) | Kotlin, Jetpack Compose, CameraX, ML Kit, Room |
| Backend | [`backend/`](backend/) | Python 3.12, FastAPI, SQLModel, SQLite/Postgres |
| Web dashboard | [`dashboard/`](dashboard/) | SvelteKit, Tailwind, ECharts, MapLibre |
| Home Assistant integration | [`homeassistant/`](homeassistant/) | HACS-installable custom_component |

The backend ships a single Docker image with the dashboard baked in, ready to drop behind a Cloudflare tunnel or any reverse proxy.

## Pluggable LLM providers

The phone analyzer is provider-agnostic. Two adapters cover most setups:

- **Anthropic** (default) - Claude Haiku 4.5, vision-capable.
- **OpenAI-compatible** - any service that speaks the OpenAI Chat Completions API: OpenAI, NVIDIA NIM, OpenRouter, Together, Groq, plus local runtimes like Ollama and LM Studio.

Bring your own key in Settings, or run the backend with its own key and let the phone relay through it.

## Documentation

- [Architecture overview](docs/architecture.md)
- [Design system](docs/design.md)
- [Motion + haptics spec](docs/motion.md)
- [Deploy to a Docker host](docs/deploy.md) - end-to-end runbook for putting the backend on a homelab Docker box behind a Cloudflare tunnel
- [Phase 1 verification](docs/phase1-verification.md) and [Phase 2 verification](docs/phase2-verification.md)

## License

[AGPL-3.0](LICENSE). If you run a modified version as a service, you must share the source.
