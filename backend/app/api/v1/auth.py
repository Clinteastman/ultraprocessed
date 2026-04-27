"""Pairing + token issuance.

Personal-use single-user MVP: any caller can create a device (no admin
gate). Cloudflare Access / network-level controls are expected to wrap
the deployment when public-facing.
"""
from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlmodel import Session

from app.auth import (
    generate_token,
    get_current_device,
    get_or_create_default_user,
    hash_token,
)
from app.db import get_session
from app.models import Device

router = APIRouter(prefix="/auth", tags=["auth"])


class PairRequest(BaseModel):
    device_name: str = "phone"


class PairResponse(BaseModel):
    device_id: int
    user_id: int
    token: str
    """The plaintext token. Stored on the phone in EncryptedSharedPreferences."""


@router.post("/token", response_model=PairResponse, status_code=201)
def issue_token(
    payload: PairRequest,
    session: Session = Depends(get_session),
) -> PairResponse:
    user = get_or_create_default_user(session)

    raw = generate_token()
    device = Device(
        user_id=user.id,
        name=payload.device_name,
        token_hash=hash_token(raw),
    )
    session.add(device)
    session.commit()
    session.refresh(device)

    return PairResponse(device_id=device.id, user_id=user.id, token=raw)


class WhoAmIResponse(BaseModel):
    device_id: int
    user_id: int
    device_name: str


@router.get("/whoami", response_model=WhoAmIResponse)
def whoami(device: Device = Depends(get_current_device), session: Session = Depends(get_session)) -> WhoAmIResponse:
    device.last_seen_at = datetime.now(timezone.utc)
    session.add(device)
    session.commit()
    return WhoAmIResponse(device_id=device.id, user_id=device.user_id, device_name=device.name)
