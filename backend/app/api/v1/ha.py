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
from app.models import (
    BowelEvent,
    ConsumptionLog,
    DailyCheckin,
    FastingProfile,
    FoodEntry,
    SymptomEvent,
    SymptomKind,
    User,
    UserTargets,
)
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

    # ---- IBS / gut state ----
    bowel_count_today: int
    last_bowel_at: datetime | None
    minutes_since_last_bowel: float | None
    last_bristol: int | None
    bristol_average_today: float | None
    bowel_flares_today: int  # bristol in {1,2,6,7} OR urgency>=2 OR pain>=2
    symptom_count_today: int
    symptom_severity_max_today: int
    last_symptom_kind: str | None
    last_symptom_at: datetime | None
    minutes_since_last_symptom: float | None
    bloating_today: int
    heartburn_today: int
    gut_score_today: int | None
    period_today: bool


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

    # ---- IBS / gut state ----
    bowel_today_rows = session.exec(
        select(BowelEvent)
        .where(BowelEvent.user_id == user.id)
        .where(BowelEvent.occurred_at >= start)
        .where(BowelEvent.occurred_at <= end)
    ).all()
    last_bowel = session.exec(
        select(BowelEvent)
        .where(BowelEvent.user_id == user.id)
        .order_by(BowelEvent.occurred_at.desc())
        .limit(1)
    ).first()
    last_bowel_at: datetime | None = None
    minutes_since_last_bowel: float | None = None
    last_bristol: int | None = None
    if last_bowel is not None:
        last_bowel_at = last_bowel.occurred_at
        if last_bowel_at.tzinfo is None:
            last_bowel_at = last_bowel_at.replace(tzinfo=timezone.utc)
        minutes_since_last_bowel = round((now - last_bowel_at).total_seconds() / 60.0, 1)
        last_bristol = last_bowel.bristol
    bristol_average_today = (
        round(sum(b.bristol for b in bowel_today_rows) / len(bowel_today_rows), 2)
        if bowel_today_rows
        else None
    )
    bowel_flares_today = sum(
        1 for b in bowel_today_rows
        if b.bristol in (1, 2, 6, 7) or b.urgency >= 2 or b.pain >= 2
    )

    symptom_today_rows = session.exec(
        select(SymptomEvent)
        .where(SymptomEvent.user_id == user.id)
        .where(SymptomEvent.occurred_at >= start)
        .where(SymptomEvent.occurred_at <= end)
    ).all()
    last_symptom = session.exec(
        select(SymptomEvent)
        .where(SymptomEvent.user_id == user.id)
        .order_by(SymptomEvent.occurred_at.desc())
        .limit(1)
    ).first()
    last_symptom_kind: str | None = None
    last_symptom_at: datetime | None = None
    minutes_since_last_symptom: float | None = None
    if last_symptom is not None:
        last_symptom_kind = last_symptom.kind.value
        last_symptom_at = last_symptom.occurred_at
        if last_symptom_at.tzinfo is None:
            last_symptom_at = last_symptom_at.replace(tzinfo=timezone.utc)
        minutes_since_last_symptom = round((now - last_symptom_at).total_seconds() / 60.0, 1)
    symptom_severity_max = max((s.severity for s in symptom_today_rows), default=0)

    today_iso = now.date().isoformat()
    checkin = session.exec(
        select(DailyCheckin)
        .where(DailyCheckin.user_id == user.id)
        .where(DailyCheckin.day == today_iso)
    ).first()

    # If no daily check-in yet, fall back to the worst symptom of that
    # kind logged today so the sensors aren't always zero.
    bloating_today = checkin.bloating_overall if checkin else max(
        (s.severity for s in symptom_today_rows if s.kind == SymptomKind.BLOATING),
        default=0,
    )
    heartburn_today = checkin.heartburn_overall if checkin else max(
        (s.severity for s in symptom_today_rows if s.kind == SymptomKind.HEARTBURN),
        default=0,
    )
    gut_score_today = checkin.gut_score if checkin else None
    period_today = checkin.period if checkin else False

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
        bowel_count_today=len(bowel_today_rows),
        last_bowel_at=last_bowel_at,
        minutes_since_last_bowel=minutes_since_last_bowel,
        last_bristol=last_bristol,
        bristol_average_today=bristol_average_today,
        bowel_flares_today=bowel_flares_today,
        symptom_count_today=len(symptom_today_rows),
        symptom_severity_max_today=symptom_severity_max,
        last_symptom_kind=last_symptom_kind,
        last_symptom_at=last_symptom_at,
        minutes_since_last_symptom=minutes_since_last_symptom,
        bloating_today=bloating_today,
        heartburn_today=heartburn_today,
        gut_score_today=gut_score_today,
        period_today=period_today,
    )
