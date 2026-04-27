from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from app.config import get_settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Database init lands in the next task.
    yield


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="Ultraprocessed",
        version="0.1.0",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.get("/api/v1/health")
    async def health() -> dict[str, str]:
        return {"status": "ok", "version": app.version}

    _mount_dashboard(app, settings.dashboard_dir)
    return app


def _mount_dashboard(app: FastAPI, directory: Path) -> None:
    """Serve the built SvelteKit static assets at /. Falls through gracefully when
    the dashboard hasn't been built yet (developer runs the backend alone)."""
    if not directory.exists():
        @app.get("/")
        async def dashboard_placeholder() -> JSONResponse:
            return JSONResponse(
                {
                    "message": "Ultraprocessed backend running. Dashboard not built.",
                    "api": "/api/v1",
                }
            )
        return

    app.mount("/_app", StaticFiles(directory=directory / "_app"), name="dashboard-app")

    @app.get("/{path:path}", include_in_schema=False)
    async def dashboard_fallback(path: str) -> FileResponse:
        candidate = directory / path
        if candidate.exists() and candidate.is_file():
            return FileResponse(candidate)
        return FileResponse(directory / "index.html")


app = create_app()
