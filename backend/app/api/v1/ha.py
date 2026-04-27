"""Compact snapshot endpoint for the Home Assistant DataUpdateCoordinator.

Designed to be cheap to poll: a single call returns everything the HA
integration's sensors need so we never trigger N round-trips per refresh.
"""
from __future__ import annotations

from datetime import datetime, time, timedelta, timezone

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlmodel import Session, select

from app.api.v1.fasting import compute_window
from app.auth import get_current_user
from app.db import get_session
from app.models import ConsumptionLog, FastingProfile, FoodEntry, User
from app.services.nutrients import REFERENCE_DAILY_VALUES, aggregate_nutrients

router = APIRouter(prefix="/ha", tags=["ha"])


class LastMealDto(BaseModel):
    name: str
    nova_class: int
    eaten_at: datetime
    kcal: float | None
    percentage_eaten: int


class HaSnapshot(BaseModel):
    calories_today: float
    nova_average_today: float | None
    nova4_calories_today: float
    meals_today: int
    last_meal: LastMealDto | None
    currently_fasting: bool
    next_eat_at: datetime | None
    nutrients_today: dict[str, float]
    nutrients_reference: dict[str, float]


@router.get("/snapshot", response_model=HaSnapshot)
def snapshot(
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> HaSnapshot:
    now = datetime.now(timezone.utc)
    start = datetime.combine(now.date(), time.min, tzinfo=timezone.utc)
    end = start + timedelta(days=1)

    logs = session.exec(
        select(ConsumptionLog)
        .where(ConsumptionLog.user_id == user.id)
        .where(ConsumptionLog.eaten_at >= start)
        .where(ConsumptionLog.eaten_at <= end)
    ).all()
    food_uuids = {log.food_client_uuid for log in logs}
    foods_by_uuid: dict[str, FoodEntry] = {}
    if food_uuids:
        rows = session.exec(
            select(FoodEntry).where(FoodEntry.client_uuid.in_(food_uuids))  # type: ignore[attr-defined]
        ).all()
        foods_by_uuid = {f.client_uuid: f for f in rows}

    calories_today = 0.0
    nova4_calories = 0.0
    nova_weighted_sum = 0.0
    nova_count = 0
    for log in logs:
        food = foods_by_uuid.get(log.food_client_uuid)
        if food is None:
            continue
        kcal = log.kcal_consumed_snapshot or 0.0
        calories_today += kcal
        if food.nova_class == 4:
            nova4_calories += kcal
        nova_weighted_sum += food.nova_class * (log.percentage_eaten / 100.0)
        nova_count += 1

    nova_average = round(nova_weighted_sum / nova_count, 2) if nova_count else None

    last_log = session.exec(
        select(ConsumptionLog)
        .where(ConsumptionLog.user_id == user.id)
        .order_by(ConsumptionLog.eaten_at.desc())
        .limit(1)
    ).first()

    last_meal = None
    if last_log is not None:
        food = foods_by_uuid.get(last_log.food_client_uuid)
        if food is None:
            food = session.exec(
                select(FoodEntry).where(FoodEntry.client_uuid == last_log.food_client_uuid)
            ).first()
        if food is not None:
            last_meal = LastMealDto(
                name=food.name,
                nova_class=food.nova_class,
                eaten_at=last_log.eaten_at,
                kcal=last_log.kcal_consumed_snapshot,
                percentage_eaten=last_log.percentage_eaten,
            )

    profile = session.exec(
        select(FastingProfile)
        .where(FastingProfile.user_id == user.id)
        .where(FastingProfile.active == True)  # noqa: E712
    ).first()
    currently_fasting = False
    next_eat_at = None
    if profile is not None:
        last_meal_at = last_log.eaten_at if last_log else None
        currently_fasting, next_eat_at = compute_window(
            now=now,
            last_meal_at=last_meal_at,
            eating_start_min=profile.eating_window_start_minutes,
            eating_end_min=profile.eating_window_end_minutes,
        )

    nutrients_today = aggregate_nutrients(logs)
    return HaSnapshot(
        calories_today=round(calories_today, 1),
        nova_average_today=nova_average,
        nova4_calories_today=round(nova4_calories, 1),
        meals_today=len(logs),
        last_meal=last_meal,
        currently_fasting=currently_fasting,
        next_eat_at=next_eat_at,
        nutrients_today=nutrients_today,
        nutrients_reference=REFERENCE_DAILY_VALUES,
    )
