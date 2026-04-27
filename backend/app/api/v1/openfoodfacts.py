"""Cached Open Food Facts proxy."""
from __future__ import annotations

import re
from datetime import datetime, timedelta, timezone
from typing import Any

import httpx
from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session

from app.auth import get_current_user
from app.config import get_settings
from app.db import get_session
from app.models import OffCache, User

router = APIRouter(prefix="/openfoodfacts", tags=["openfoodfacts"])

_BARCODE_RE = re.compile(r"^\d{6,14}$")
_OFF_FIELDS = (
    "product_name,generic_name,brands,nova_group,nova_groups_tags,"
    "nutriments,serving_size,ingredients_text,ingredients_text_en,"
    "image_url,image_front_url,nutriscore_grade"
)


@router.get("/{barcode}")
async def lookup(
    barcode: str,
    user: User = Depends(get_current_user),  # noqa: ARG001 - keeps endpoint authenticated
    session: Session = Depends(get_session),
) -> dict[str, Any]:
    if not _BARCODE_RE.match(barcode):
        raise HTTPException(status_code=400, detail="barcode must be 6-14 digits")

    settings = get_settings()
    now = datetime.now(timezone.utc)

    cached = session.get(OffCache, barcode)
    if cached and cached.expires_at > now:
        import json

        return json.loads(cached.payload_json)

    url = f"https://world.openfoodfacts.org/api/v2/product/{barcode}.json?fields={_OFF_FIELDS}"
    async with httpx.AsyncClient(timeout=15.0) as client:
        resp = await client.get(url, headers={"User-Agent": settings.off_user_agent})
        if resp.status_code != 200:
            raise HTTPException(status_code=502, detail=f"upstream {resp.status_code}")
        body = resp.json()

    expires_at = now + timedelta(seconds=settings.off_cache_ttl_seconds)
    import json

    if cached:
        cached.payload_json = json.dumps(body)
        cached.fetched_at = now
        cached.expires_at = expires_at
        session.add(cached)
    else:
        session.add(OffCache(
            barcode=barcode,
            payload_json=json.dumps(body),
            fetched_at=now,
            expires_at=expires_at,
        ))
    session.commit()

    return body
