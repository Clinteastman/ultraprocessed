"""Aggregation endpoints powering the web dashboard and HA integration."""
from __future__ import annotations

from datetime import datetime, time, timedelta, timezone

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlmodel import Session, select

from app.auth import get_current_user
from app.db import get_session
from app.models import ConsumptionLog, FoodEntry, User, UserTargets
from app.services.nutrients import (
    DEFAULT_CALORIE_TARGET,
    REFERENCE_DAILY_VALUES,
    adequacy,
    aggregate_nutrients,
)


def _calorie_target(session: Session, user_id: int) -> float:
    row = session.get(UserTargets, user_id)
    return row.calorie_target_kcal if row else DEFAULT_CALORIE_TARGET

router = APIRouter(prefix="/dashboard", tags=["dashboard"])


class NovaBucket(BaseModel):
    meals: int
    calories: float


class NutrientAdequacyDto(BaseModel):
    consumed: float
    reference: float
    pct: float
    direction: str


class AggregateResponse(BaseModel):
    from_: datetime
    to: datetime
    meal_count: int
    calories_consumed: float
    calorie_reference: float
    nova_breakdown: dict[int, NovaBucket]
    nova_average: float | None
    nutrients_consumed: dict[str, float]
    nutrients_adequacy: dict[str, NutrientAdequacyDto]

    model_config = {"populate_by_name": True}


def _aggregate(
    session: Session,
    user_id: int,
    start: datetime,
    end: datetime,
) -> AggregateResponse:
    logs = session.exec(
        select(ConsumptionLog)
        .where(ConsumptionLog.user_id == user_id)
        .where(ConsumptionLog.eaten_at >= start)
        .where(ConsumptionLog.eaten_at <= end)
    ).all()

    food_uuids = {log.food_client_uuid for log in logs}
    foods_by_uuid: dict[str, FoodEntry] = {}
    if food_uuids:
        food_rows = session.exec(
            select(FoodEntry).where(FoodEntry.client_uuid.in_(food_uuids))  # type: ignore[attr-defined]
        ).all()
        foods_by_uuid = {f.client_uuid: f for f in food_rows}

    nova_buckets: dict[int, NovaBucket] = {n: NovaBucket(meals=0, calories=0.0) for n in (1, 2, 3, 4)}
    weighted_nova_sum = 0.0
    weighted_nova_count = 0
    total_kcal = 0.0
    for log in logs:
        food = foods_by_uuid.get(log.food_client_uuid)
        if food is None:
            continue
        nova = max(1, min(4, food.nova_class))
        bucket = nova_buckets[nova]
        bucket.meals += 1
        kcal = log.kcal_consumed_snapshot or 0.0
        bucket.calories = round(bucket.calories + kcal, 2)
        total_kcal += kcal
        weighted_nova_sum += nova * (log.percentage_eaten / 100.0)
        weighted_nova_count += 1

    nova_average = (
        round(weighted_nova_sum / weighted_nova_count, 2) if weighted_nova_count else None
    )

    nutrients_total = aggregate_nutrients(logs)
    adequacy_map = adequacy(nutrients_total)

    return AggregateResponse(
        from_=start,
        to=end,
        meal_count=len(logs),
        calories_consumed=round(total_kcal, 1),
        calorie_reference=_calorie_target(session, user_id),
        nova_breakdown=nova_buckets,
        nova_average=nova_average,
        nutrients_consumed=nutrients_total,
        nutrients_adequacy={
            key: NutrientAdequacyDto(
                consumed=val.consumed,
                reference=val.reference,
                pct=val.pct,
                direction=val.direction,
            )
            for key, val in adequacy_map.items()
            if key in REFERENCE_DAILY_VALUES
        },
    )


@router.get("/today", response_model=AggregateResponse)
def today(
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> AggregateResponse:
    now = datetime.now(timezone.utc)
    start = datetime.combine(now.date(), time.min, tzinfo=timezone.utc)
    end = start + timedelta(days=1)
    return _aggregate(session, user.id, start, end)


@router.get("/range", response_model=AggregateResponse)
def range_aggregate(
    from_: datetime = Query(alias="from"),
    to: datetime = Query(default_factory=lambda: datetime.now(timezone.utc)),
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> AggregateResponse:
    return _aggregate(session, user.id, from_, to)
