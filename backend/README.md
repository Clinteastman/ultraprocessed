# Ultraprocessed backend

FastAPI + SQLModel + SQLite (default) or Postgres. Single Docker image that also serves the SvelteKit dashboard once it's built.

## Run locally

```
cd backend
python -m venv .venv && source .venv/bin/activate   # or .\.venv\Scripts\Activate.ps1 on Windows
pip install -e ".[dev]"
mkdir -p data
ULTRAPROCESSED_TOKEN_SECRET=$(openssl rand -hex 32) uvicorn app.main:app --reload
```

Health check: `curl http://localhost:8000/api/v1/health`

## Run via Docker

```
cd deploy
cp .env.example .env
# edit .env to set a real ULTRAPROCESSED_TOKEN_SECRET
docker compose up -d --build
```

The backend listens on the port set by `ULTRAPROCESSED_PORT` (default 8000). Point your Cloudflare tunnel at it.

## Migrations

Alembic. After changing models:

```
alembic revision --autogenerate -m "what changed"
alembic upgrade head
```

The Docker entrypoint runs `alembic upgrade head` automatically (next task).
