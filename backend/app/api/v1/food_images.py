"""Food image upload endpoint.

Accepts a JPEG via multipart form, writes it to disk under
``settings.images_dir / {user_id} / {client_uuid}.jpg``, and sets the
owning FoodEntry's ``image_url`` to a path the dashboard can fetch
back via the ``/images`` static mount. Idempotent: re-uploading the
same client_uuid overwrites.
"""
from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from sqlmodel import Session, select

from app.auth import get_current_user
from app.config import get_settings
from app.db import get_session
from app.models import FoodEntry, User

router = APIRouter(prefix="/foods", tags=["foods"])

_MAX_BYTES = 8 * 1024 * 1024  # 8 MB; phone re-encodes scans well under this.
_ALLOWED_TYPES = {"image/jpeg", "image/jpg", "image/pjpeg"}


@router.post("/{client_uuid}/image", status_code=204)
async def upload_food_image(
    client_uuid: str,
    file: UploadFile = File(...),
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> None:
    food = session.exec(
        select(FoodEntry).where(FoodEntry.client_uuid == client_uuid)
    ).first()
    if food is None:
        raise HTTPException(status_code=404, detail="food not found; push food entry first")
    if food.user_id != user.id:
        raise HTTPException(status_code=403, detail="not yours")

    if file.content_type and file.content_type.lower() not in _ALLOWED_TYPES:
        raise HTTPException(status_code=415, detail=f"unsupported media type: {file.content_type}")

    body = await file.read()
    if len(body) == 0:
        raise HTTPException(status_code=400, detail="empty body")
    if len(body) > _MAX_BYTES:
        raise HTTPException(status_code=413, detail=f"image too large ({len(body)} bytes)")

    images_dir: Path = get_settings().images_dir
    user_dir = images_dir / str(user.id)
    user_dir.mkdir(parents=True, exist_ok=True)
    out = user_dir / f"{client_uuid}.jpg"
    out.write_bytes(body)

    food.image_url = f"/images/{user.id}/{client_uuid}.jpg"
    session.add(food)
    session.commit()
