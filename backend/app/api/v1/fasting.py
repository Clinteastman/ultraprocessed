"""Fasting profiles + computed state."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlmodel import Session, select

from app.auth import get_current_user
from app.db import get_session
from app.models import ConsumptionLog, FastingProfile, ScheduleType, User

router = APIRouter(prefix="/fasting", tags=["fasting"])


class FastingProfileDto(BaseModel):
    id: int | None = None
    name: str
    schedule_type: ScheduleType
    eating_window_start_minutes: int
    eating_window_end_minutes: int
    active: bool = True


class FastingState(BaseModel):
    currently_fasting: bool
    next_eat_at: datetime | None
    last_meal_at: datetime | None
    profile: FastingProfileDto | None


@router.get("/profile", response_model=FastingProfileDto | None)
def get_active_profile(
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> FastingProfileDto | None:
    row = session.exec(
        select(FastingProfile)
        .where(FastingProfile.user_id == user.id)
        .where(FastingProfile.active == True)  # noqa: E712
    ).first()
    return _to_dto(row) if row else None


@router.put("/profile", response_model=FastingProfileDto)
def upsert_profile(
    payload: FastingProfileDto,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> FastingProfileDto:
    if payload.eating_window_start_minutes < 0 or payload.eating_window_start_minutes > 1440:
        raise HTTPException(status_code=400, detail="eating_window_start_minutes out of range")
    if payload.eating_window_end_minutes < 0 or payload.eating_window_end_minutes > 1440:
        raise HTTPException(status_code=400, detail="eating_window_end_minutes out of range")

    if payload.active:
        # Deactivate everything else first - only one active profile per user.
        for other in session.exec(
            select(FastingProfile).where(FastingProfile.user_id == user.id)
        ).all():
            other.active = False
            session.add(other)

    row = FastingProfile(
        user_id=user.id,
        name=payload.name,
        schedule_type=payload.schedule_type,
        eating_window_start_minutes=payload.eating_window_start_minutes,
        eating_window_end_minutes=payload.eating_window_end_minutes,
        active=payload.active,
    )
    session.add(row)
    session.commit()
    session.refresh(row)
    return _to_dto(row)


@router.get("/state", response_model=FastingState)
def get_state(
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> FastingState:
    profile = session.exec(
        select(FastingProfile)
        .where(FastingProfile.user_id == user.id)
        .where(FastingProfile.active == True)  # noqa: E712
    ).first()

    last_meal = session.exec(
        select(ConsumptionLog)
        .where(ConsumptionLog.user_id == user.id)
        .order_by(ConsumptionLog.eaten_at.desc())
        .limit(1)
    ).first()

    last_meal_at = last_meal.eaten_at if last_meal else None
    if profile is None:
        return FastingState(
            currently_fasting=False,
            next_eat_at=None,
            last_meal_at=last_meal_at,
            profile=None,
        )

    now = datetime.now(timezone.utc)
    currently, next_eat = compute_window(
        now=now,
        last_meal_at=last_meal_at,
        eating_start_min=profile.eating_window_start_minutes,
        eating_end_min=profile.eating_window_end_minutes,
    )
    return FastingState(
        currently_fasting=currently,
        next_eat_at=next_eat,
        last_meal_at=last_meal_at,
        profile=_to_dto(profile),
    )


def compute_window(
    now: datetime,
    last_meal_at: datetime | None,
    eating_start_min: int,
    eating_end_min: int,
) -> tuple[bool, datetime | None]:
    """Returns (currently_fasting, next_eat_at).

    Eating window is defined as minutes-after-midnight in the user's local
    time. We treat `now` as already in the user's local timezone (the phone
    sends UTC; for a single-user MVP we accept the inevitable drift)."""
    minutes_now = now.hour * 60 + now.minute
    in_window = eating_start_min <= minutes_now <= eating_end_min
    if in_window:
        return False, None
    today = now.replace(hour=0, minute=0, second=0, microsecond=0)
    if minutes_now < eating_start_min:
        next_eat = today + timedelta(minutes=eating_start_min)
    else:
        next_eat = today + timedelta(days=1, minutes=eating_start_min)
    return True, next_eat


def _to_dto(row: FastingProfile | None) -> FastingProfileDto | None:
    if row is None:
        return None
    return FastingProfileDto(
        id=row.id,
        name=row.name,
        schedule_type=row.schedule_type,
        eating_window_start_minutes=row.eating_window_start_minutes,
        eating_window_end_minutes=row.eating_window_end_minutes,
        active=row.active,
    )
