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
from app.models import ConsumptionLog, FastingProfile, FoodEntry, User, UserTargets
from app.services.nutrients import REFERENCE_DAILY_VALUES, aggregate_nutrients

router = APIRouter(prefix="/ha", tags=["ha"])


class LastMealDto(BaseModel):
    name: str
    nova_class: int
    eaten_at: datetime
    kcal: float | None
    percentage_eaten: int


class FastingProfileSnapshot(BaseModel):
    name: str
    schedule_type: str
    eating_window_start_minutes: int
    eating_window_end_minutes: int
    restricted_days_mask: int = 0
    restricted_kcal_target: int | None = None


class HaSnapshot(BaseModel):
    # Headline calorie / NOVA totals.
    calories_today: float
    calorie_target_kcal: float
    calorie_remaining_kcal: float
    nova_average_today: float | None
    nova4_calories_today: float
    upf_share_percent: float | None
    meals_today: int

    # Per-NOVA-class breakdowns. Keyed by str class number ("1".."4")
    # since JSON object keys must be strings; HA sensors split these out.
    nova_calories: dict[str, float]
    nova_meal_counts: dict[str, int]

    # Last meal context.
    last_meal: LastMealDto | None
    last_meal_at: datetime | None
    minutes_since_last_meal: float | None

    # Fasting state.
    currently_fasting: bool
    next_eat_at: datetime | None
    eating_window_closes_at: datetime | None
    active_fasting_profile: FastingProfileSnapshot | None

    # Nutrients (full set; HA exposes one sensor per key).
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
    nova_calories: dict[str, float] = {"1": 0.0, "2": 0.0, "3": 0.0, "4": 0.0}
    nova_counts: dict[str, int] = {"1": 0, "2": 0, "3": 0, "4": 0}
    nova_weighted_sum = 0.0
    nova_weight = 0.0
    for log in logs:
        food = foods_by_uuid.get(log.food_client_uuid)
        if food is None:
            continue
        kcal = log.kcal_consumed_snapshot or 0.0
        calories_today += kcal
        cls_key = str(max(1, min(4, food.nova_class)))
        nova_calories[cls_key] += kcal
        nova_counts[cls_key] += 1
        # Calorie-weighted average is what the dashboard uses; fall back to
        # percentage_eaten weighting when kcal isn't known so we don't lose
        # the data point entirely.
        if kcal > 0:
            nova_weighted_sum += food.nova_class * kcal
            nova_weight += kcal
        else:
            share = log.percentage_eaten / 100.0
            nova_weighted_sum += food.nova_class * share
            nova_weight += share

    nova_average = round(nova_weighted_sum / nova_weight, 2) if nova_weight > 0 else None
    upf_share = (
        round((nova_calories["4"] / calories_today) * 100, 1)
        if calories_today > 0
        else None
    )

    targets = session.get(UserTargets, user.id)
    target_kcal = targets.calorie_target_kcal if targets else 2000.0
    remaining_kcal = round(target_kcal - calories_today, 1)

    last_log = session.exec(
        select(ConsumptionLog)
        .where(ConsumptionLog.user_id == user.id)
        .order_by(ConsumptionLog.eaten_at.desc())
        .limit(1)
    ).first()

    last_meal: LastMealDto | None = None
    last_meal_at: datetime | None = None
    minutes_since_last_meal: float | None = None
    if last_log is not None:
        last_meal_at = last_log.eaten_at
        # Older rows may have naive datetimes from earlier writes; coerce
        # to UTC so the subtraction below doesn't blow up.
        if last_meal_at.tzinfo is None:
            last_meal_at = last_meal_at.replace(tzinfo=timezone.utc)
        minutes_since_last_meal = round((now - last_meal_at).total_seconds() / 60.0, 1)
        food = foods_by_uuid.get(last_log.food_client_uuid)
        if food is None:
            food = session.exec(
                select(FoodEntry).where(FoodEntry.client_uuid == last_log.food_client_uuid)
            ).first()
        if food is not None:
            last_meal = LastMealDto(
                name=food.name,
                nova_class=food.nova_class,
                eaten_at=last_meal_at,
                kcal=last_log.kcal_consumed_snapshot,
                percentage_eaten=last_log.percentage_eaten,
            )

    profile = session.exec(
        select(FastingProfile)
        .where(FastingProfile.user_id == user.id)
        .where(FastingProfile.active == True)  # noqa: E712
    ).first()
    currently_fasting = False
    next_eat_at: datetime | None = None
    eating_window_closes_at: datetime | None = None
    profile_snapshot: FastingProfileSnapshot | None = None
    if profile is not None:
        currently_fasting, next_eat_at = compute_window(
            now=now,
            last_meal_at=last_meal_at,
            eating_start_min=profile.eating_window_start_minutes,
            eating_end_min=profile.eating_window_end_minutes,
        )
        if not currently_fasting:
            today_midnight = now.replace(hour=0, minute=0, second=0, microsecond=0)
            eating_window_closes_at = today_midnight + timedelta(
                minutes=profile.eating_window_end_minutes
            )
        profile_snapshot = FastingProfileSnapshot(
            name=profile.name,
            schedule_type=profile.schedule_type.value,
            eating_window_start_minutes=profile.eating_window_start_minutes,
            eating_window_end_minutes=profile.eating_window_end_minutes,
            restricted_days_mask=profile.restricted_days_mask or 0,
            restricted_kcal_target=profile.restricted_kcal_target,
        )

    nutrients_today = aggregate_nutrients(logs)
    return HaSnapshot(
        calories_today=round(calories_today, 1),
        calorie_target_kcal=round(target_kcal, 1),
        calorie_remaining_kcal=remaining_kcal,
        nova_average_today=nova_average,
        nova4_calories_today=round(nova_calories["4"], 1),
        upf_share_percent=upf_share,
        meals_today=len(logs),
        nova_calories={k: round(v, 1) for k, v in nova_calories.items()},
        nova_meal_counts=nova_counts,
        last_meal=last_meal,
        last_meal_at=last_meal_at,
        minutes_since_last_meal=minutes_since_last_meal,
        currently_fasting=currently_fasting,
        next_eat_at=next_eat_at,
        eating_window_closes_at=eating_window_closes_at,
        active_fasting_profile=profile_snapshot,
        nutrients_today=nutrients_today,
        nutrients_reference=REFERENCE_DAILY_VALUES,
    )
