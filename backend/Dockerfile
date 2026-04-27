# Self-contained image: backend + built SvelteKit dashboard.
# Build context is the repo root so we can COPY both backend/ and dashboard/.
#   docker build -t ghcr.io/<owner>/ultraprocessed-backend:latest -f backend/Dockerfile .

# ---- Stage 1: build the SvelteKit dashboard ----
FROM node:22-alpine AS dashboard-builder
WORKDIR /work

RUN corepack enable

COPY dashboard/package.json dashboard/pnpm-lock.yaml dashboard/.npmrc ./
RUN pnpm install --frozen-lockfile

COPY dashboard/ ./
RUN pnpm build
# Output: /work/build/  (static SPA)


# ---- Stage 2: build Python wheels ----
FROM python:3.12-slim-bookworm AS py-builder

ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /build

RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY backend/pyproject.toml backend/README.md ./
RUN pip install --upgrade pip && pip wheel --wheel-dir=/wheels .


# ---- Stage 3: runtime ----
FROM python:3.12-slim-bookworm AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

RUN groupadd -r app && useradd -r -g app -m -d /app app
WORKDIR /app

COPY --from=py-builder /wheels /wheels
RUN pip install --no-index --find-links=/wheels /wheels/*.whl \
    && rm -rf /wheels

COPY backend/app /app/app
COPY backend/alembic.ini /app/alembic.ini
COPY backend/alembic /app/alembic

COPY --from=dashboard-builder /work/build /app/dashboard

RUN mkdir -p /app/data && chown -R app:app /app

USER app
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request,sys;sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/api/v1/health').status==200 else 1)" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
