"""Full-data export endpoints.

Two flavours:
  - JSON: portable, human-readable, suitable for re-import or external
    inspection. Returns one document with every row owned by the
    requesting user.
  - SQLite: a consistent .backup of the live SQLite database file,
    streamed to the client. Lossless and restore-able to another
    instance with `cp` or `sqlite3 .restore`.

Single-user mode means the calling user's records are everything they
care about; multi-user mode (later) will need stricter scoping.
"""
from __future__ import annotations

import json
import sqlite3
import tempfile
from datetime import datetime, timezone
from pathlib import Path

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from fastapi.responses import FileResponse, JSONResponse
from sqlmodel import Session, select

from app.auth import get_current_user
from app.config import get_settings
from app.db import get_session
from app.models import (
    ConsumptionLog,
    FastingProfile,
    FoodEntry,
    User,
    UserTargets,
)

router = APIRouter(prefix="/export", tags=["export"])


def _serialize_row(row) -> dict:
    """SQLModel row -> plain dict, with datetimes coerced to ISO strings."""
    out = {}
    for k, v in row.model_dump().items():
        if isinstance(v, datetime):
            out[k] = v.isoformat()
        else:
            out[k] = v
    return out


@router.get("/json")
def export_json(
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> JSONResponse:
    foods = session.exec(select(FoodEntry).where(FoodEntry.user_id == user.id)).all()
    logs = session.exec(select(ConsumptionLog).where(ConsumptionLog.user_id == user.id)).all()
    profiles = session.exec(select(FastingProfile).where(FastingProfile.user_id == user.id)).all()
    targets = session.get(UserTargets, user.id)

    body = {
        "schema_version": 1,
        "exported_at": datetime.now(timezone.utc).isoformat(),
        "user": {"id": user.id, "name": user.name, "created_at": user.created_at.isoformat()},
        "targets": _serialize_row(targets) if targets else None,
        "fasting_profiles": [_serialize_row(p) for p in profiles],
        "food_entries": [_serialize_row(f) for f in foods],
        "consumption_logs": [_serialize_row(l) for l in logs],
    }

    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    return JSONResponse(
        content=body,
        headers={
            "Content-Disposition": f'attachment; filename="ultraprocessed-{today}.json"',
        },
    )


@router.get("/sqlite")
def export_sqlite(
    background: BackgroundTasks,
    user: User = Depends(get_current_user),  # noqa: ARG001 - just to gate the download
) -> FileResponse:
    settings = get_settings()
    url = settings.database_url
    if not url.startswith("sqlite"):
        raise HTTPException(
            status_code=400,
            detail="SQLite export is only available for SQLite-backed deployments.",
        )

    # Extract the on-disk path: "sqlite:///./data/ultraprocessed.db" or
    # "sqlite:////app/data/ultraprocessed.db".
    path_part = url.split("///", 1)[1]
    src_path = Path("/" + path_part) if url.startswith("sqlite:////") else Path(path_part)
    if not src_path.exists():
        raise HTTPException(status_code=500, detail=f"Database file not found at {src_path}")

    # Backup to a tempfile so we get a consistent snapshot even if
    # writes are happening. The backup file is deleted after the
    # response finishes streaming via BackgroundTasks.
    tmp = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
    tmp.close()
    src = sqlite3.connect(str(src_path))
    try:
        dst = sqlite3.connect(tmp.name)
        try:
            src.backup(dst)
        finally:
            dst.close()
    finally:
        src.close()

    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    background.add_task(Path(tmp.name).unlink, missing_ok=True)
    return FileResponse(
        path=tmp.name,
        filename=f"ultraprocessed-{today}.db",
        media_type="application/x-sqlite3",
    )
