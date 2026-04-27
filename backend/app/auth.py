"""Per-device bearer-token auth.

Tokens are random 32-byte URL-safe strings. The plaintext token is shown
to the user once at pairing; only its SHA-256 hash is persisted. This
keeps a stolen DB from immediately authenticating future requests.
"""
from __future__ import annotations

import hashlib
import secrets

from fastapi import Depends, Header, HTTPException, status
from sqlmodel import Session, select

from app.db import get_session
from app.models import Device, User

AUTH_HEADER = "Authorization"
BEARER_PREFIX = "Bearer "


def hash_token(raw_token: str) -> str:
    return hashlib.sha256(raw_token.encode("utf-8")).hexdigest()


def generate_token() -> str:
    """Produces a URL-safe random token. ~43 chars, 256 bits of entropy."""
    return secrets.token_urlsafe(32)


def get_current_device(
    authorization: str | None = Header(default=None),
    session: Session = Depends(get_session),
) -> Device:
    if not authorization or not authorization.startswith(BEARER_PREFIX):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid Authorization header.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    raw = authorization[len(BEARER_PREFIX):].strip()
    if not raw:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Empty token.")

    token_hash = hash_token(raw)
    device = session.exec(select(Device).where(Device.token_hash == token_hash)).first()
    if device is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Unknown token.")

    return device


def get_current_user(
    device: Device = Depends(get_current_device),
    session: Session = Depends(get_session),
) -> User:
    user = session.get(User, device.user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Orphaned device.")
    return user


def get_or_create_default_user(session: Session) -> User:
    """For the personal-use MVP we run in single-user mode. The first device
    to pair creates the user; subsequent devices attach to the same user."""
    existing = session.exec(select(User).order_by(User.id)).first()
    if existing is not None:
        return existing
    user = User(name="me")
    session.add(user)
    session.commit()
    session.refresh(user)
    return user
