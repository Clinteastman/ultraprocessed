"""Database engine + session management."""
from __future__ import annotations

from collections.abc import Generator
from pathlib import Path

from sqlalchemy.engine import Engine
from sqlmodel import Session, SQLModel, create_engine

from app.config import get_settings


def _build_engine() -> Engine:
    settings = get_settings()
    url = settings.database_url
    connect_args: dict = {}
    if url.startswith("sqlite"):
        connect_args["check_same_thread"] = False
        # Make sure the directory exists (SQLite won't create parents).
        if "///" in url:
            path_part = url.split("///", 1)[1]
            db_path = Path(path_part.lstrip("/")) if url.startswith("sqlite:////") else Path(path_part)
            if db_path.parent.exists() is False and str(db_path.parent) not in (".", ""):
                db_path.parent.mkdir(parents=True, exist_ok=True)
    return create_engine(url, echo=False, connect_args=connect_args)


engine: Engine = _build_engine()


def init_schema() -> None:
    """Create tables if they don't exist. Used at app startup as a fallback
    when alembic migrations haven't been run yet (dev / fresh installs).

    Also runs idempotent ALTER TABLE statements for columns added after
    the initial v1 schema, since SQLModel.create_all() never alters
    existing tables.
    """
    SQLModel.metadata.create_all(engine)
    _ensure_evolved_columns()


def _ensure_evolved_columns() -> None:
    from sqlalchemy import text

    additions = {
        "fasting_profiles": [
            ("restricted_days_mask", "INTEGER NOT NULL DEFAULT 0"),
            ("restricted_kcal_target", "INTEGER"),
        ],
    }
    if not engine.url.drivername.startswith("sqlite"):
        # ALTER TABLE syntax differs per dialect; only run for SQLite,
        # the production target. Postgres deployments should use alembic.
        return
    with engine.begin() as conn:
        for table, cols in additions.items():
            existing = {row[1] for row in conn.execute(text(f"PRAGMA table_info({table})"))}
            for name, definition in cols:
                if name not in existing:
                    conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {name} {definition}"))


def get_session() -> Generator[Session, None, None]:
    """FastAPI dependency yielding a SQLModel session."""
    with Session(engine) as session:
        yield session
