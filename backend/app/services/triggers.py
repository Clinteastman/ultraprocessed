"""Food-trigger correlation engine.

Goal: given a set of IBS symptom/bowel events and the user's food log,
rank the foods most likely to be triggers.

Approach (deliberately simple, transparent, and stable across small
sample sizes):

For every flare event (a symptom_event with severity >= 2, OR a bowel
event with bristol in {1, 6, 7} or urgency >= 2 — i.e. constipation,
diarrhoea, or "had to run"):

  1. Look at every consumption_log in the configurable lookback window
     before the flare (default 30 minutes to 48 hours).

  2. For each food eaten in that window, compute a contribution:

         contribution = recency * fodmap * portion * flare_weight

     where:
       recency      = exp(-hours_before / half_life)  (default half-life 12h)
       fodmap       = severity_score(food.fodmap_level)
                      0.1 (LOW) -> 1.0 (HIGH); UNKNOWN gets 0.25 so
                      uncategorised foods aren't invisible but tagged
                      foods dominate ranking.
       portion      = sqrt(grams_eaten / 100)         (sub-linear)
       flare_weight = severity / 3                    (0..1)

  3. Sum contributions per `food_client_uuid` over all flares in range.

  4. Also count `flare_count` = number of distinct flares the food
     appeared in. Two flares is much weaker than three, etc.

  5. Score = total_contribution * log(1 + flare_count). The log prevents
     a single, well-stocked binge from dominating the ranking.

Caveats this doesn't try to handle (yet):
  - Confounders (you ate a high-FODMAP onion and a low-FODMAP rice in
    the same meal — both get credit).
  - "Innocent" foods that happen to appear before flares often. We mitigate
    this by also returning a `baseline_count` (how often the food appeared
    on non-flare days) so the dashboard can show the *ratio* — but the
    ranking is still by raw score for now.
  - Time-of-day bias (eating any breakfast before a 10am flare is meaningless).

The endpoint is read-only and idempotent; safe to call on every page
load. Cost is O(flares * meals_in_window) which is tiny in practice.
"""
from __future__ import annotations

import math
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

from sqlmodel import Session, select

from app.models import (
    BowelEvent,
    ConsumptionLog,
    FodmapLevel,
    FoodEntry,
    SymptomEvent,
)
from app.services.fodmap import severity_score


# ---- Config ----

DEFAULT_LOOKBACK_HOURS = 48.0
DEFAULT_MIN_LAG_MINUTES = 30.0   # skip foods eaten <30min before a flare; not enough digestion time
DEFAULT_HALF_LIFE_HOURS = 12.0   # gravity of recency on the score
DEFAULT_FLARE_SEVERITY_THRESHOLD = 2  # symptom severity >= 2 counts as flare


# ---- Data classes ----

@dataclass(slots=True)
class FlareEvent:
    """A unified view of a 'bad gut moment' from either symptom or bowel events."""
    occurred_at: datetime
    severity: float  # 0..1 normalized
    kind: str  # 'symptom:BLOATING' / 'bowel:bristol6' etc., for display
    source_uuid: str  # the originating symptom/bowel event uuid


@dataclass(slots=True)
class FoodScore:
    food_client_uuid: str
    food_name: str
    food_brand: str | None
    fodmap_level: FodmapLevel
    fodmap_tags: list[str]
    nova_class: int
    score: float
    flare_count: int
    baseline_count: int
    last_flare_at: datetime | None
    sample_flares: list[str] = field(default_factory=list)  # up to 3 flare descriptors for the UI


# ---- Public API ----

def compute_triggers(
    *,
    session: Session,
    user_id: int,
    from_dt: datetime,
    to_dt: datetime,
    lookback_hours: float = DEFAULT_LOOKBACK_HOURS,
    min_lag_minutes: float = DEFAULT_MIN_LAG_MINUTES,
    half_life_hours: float = DEFAULT_HALF_LIFE_HOURS,
    severity_threshold: int = DEFAULT_FLARE_SEVERITY_THRESHOLD,
    limit: int = 25,
) -> list[FoodScore]:
    """Rank foods by their estimated implication in flares between
    `from_dt` and `to_dt`. Foods are looked back `lookback_hours` before
    each flare."""

    # 1. Collect flares in range
    flares = _collect_flares(
        session=session,
        user_id=user_id,
        from_dt=from_dt,
        to_dt=to_dt,
        severity_threshold=severity_threshold,
    )
    if not flares:
        return []

    # 2. Pull every consumption log that *might* feed any flare. We scan
    # the union of the lookback windows: from (earliest_flare - lookback)
    # to (latest_flare). Cheaper than per-flare queries.
    earliest_food_at = min(f.occurred_at for f in flares) - timedelta(hours=lookback_hours)
    latest_food_at = max(f.occurred_at for f in flares)
    consumption_rows = session.exec(
        select(ConsumptionLog)
        .where(ConsumptionLog.user_id == user_id)
        .where(ConsumptionLog.eaten_at >= earliest_food_at)
        .where(ConsumptionLog.eaten_at <= latest_food_at)
    ).all()

    # 3. Pull the food metadata for those logs in one query.
    food_uuids = {log.food_client_uuid for log in consumption_rows}
    foods_by_uuid: dict[str, FoodEntry] = {}
    if food_uuids:
        rows = session.exec(
            select(FoodEntry).where(FoodEntry.client_uuid.in_(food_uuids))  # type: ignore[attr-defined]
        ).all()
        foods_by_uuid = {f.client_uuid: f for f in rows}

    # 4. Bucket logs by hour-of-eating for fast lookup. We don't need
    # cleverness here — just iterate, the dataset is small.
    scores: dict[str, _AccumScore] = defaultdict(_AccumScore)
    flares_with_food: set[str] = set()  # flare uuids that had any food in window

    lookback = timedelta(hours=lookback_hours)
    min_lag = timedelta(minutes=min_lag_minutes)

    for flare in flares:
        flare_window_start = flare.occurred_at - lookback
        flare_window_end = flare.occurred_at - min_lag
        flare_label = _flare_label(flare)
        for log in consumption_rows:
            eaten_at = _aware(log.eaten_at)
            if not (flare_window_start <= eaten_at <= flare_window_end):
                continue
            flares_with_food.add(flare.source_uuid)
            food = foods_by_uuid.get(log.food_client_uuid)
            if food is None:
                continue
            hours_before = (flare.occurred_at - eaten_at).total_seconds() / 3600.0
            recency = math.exp(-hours_before / half_life_hours)
            fodmap = severity_score(food.fodmap_level)
            grams = log.grams_eaten or _portion_grams(log, food)
            portion = math.sqrt(max(grams, 1.0) / 100.0)
            contribution = recency * fodmap * portion * flare.severity
            acc = scores[log.food_client_uuid]
            acc.score += contribution
            acc.flare_uuids.add(flare.source_uuid)
            if acc.last_flare_at is None or flare.occurred_at > acc.last_flare_at:
                acc.last_flare_at = flare.occurred_at
            if len(acc.sample_flares) < 3 and flare_label not in acc.sample_flares:
                acc.sample_flares.append(flare_label)

    # 5. Compute baseline counts: how often each food appeared in the
    # range *outside* a flare window. This isn't used in the score
    # itself, but the dashboard can use it to flag "you eat X often
    # without consequences -> probably innocent".
    flare_windows = [
        (f.occurred_at - lookback, f.occurred_at) for f in flares
    ]
    baseline_counts: dict[str, int] = defaultdict(int)
    for log in consumption_rows:
        eaten_at = _aware(log.eaten_at)
        in_any_window = any(start <= eaten_at <= end for start, end in flare_windows)
        if not in_any_window:
            baseline_counts[log.food_client_uuid] += 1

    # 6. Apply log(1 + flare_count) damping and assemble result rows.
    out: list[FoodScore] = []
    for food_uuid, acc in scores.items():
        food = foods_by_uuid.get(food_uuid)
        if food is None:
            continue
        flare_count = len(acc.flare_uuids)
        damped = acc.score * math.log1p(flare_count)
        out.append(
            FoodScore(
                food_client_uuid=food_uuid,
                food_name=food.name,
                food_brand=food.brand,
                fodmap_level=food.fodmap_level,
                fodmap_tags=_parse_tags(food.fodmap_tags_json),
                nova_class=food.nova_class,
                score=round(damped, 3),
                flare_count=flare_count,
                baseline_count=baseline_counts.get(food_uuid, 0),
                last_flare_at=acc.last_flare_at,
                sample_flares=acc.sample_flares,
            )
        )

    out.sort(key=lambda s: s.score, reverse=True)
    return out[:limit]


# ---- Internals ----

@dataclass(slots=True)
class _AccumScore:
    score: float = 0.0
    flare_uuids: set[str] = field(default_factory=set)
    last_flare_at: datetime | None = None
    sample_flares: list[str] = field(default_factory=list)


def _collect_flares(
    *,
    session: Session,
    user_id: int,
    from_dt: datetime,
    to_dt: datetime,
    severity_threshold: int,
) -> list[FlareEvent]:
    flares: list[FlareEvent] = []

    symptoms = session.exec(
        select(SymptomEvent)
        .where(SymptomEvent.user_id == user_id)
        .where(SymptomEvent.occurred_at >= from_dt)
        .where(SymptomEvent.occurred_at <= to_dt)
        .where(SymptomEvent.severity >= severity_threshold)
    ).all()
    for s in symptoms:
        flares.append(
            FlareEvent(
                occurred_at=_aware(s.occurred_at),
                severity=s.severity / 3.0,
                kind=f"symptom:{s.kind.value}",
                source_uuid=s.client_uuid,
            )
        )

    bowels = session.exec(
        select(BowelEvent)
        .where(BowelEvent.user_id == user_id)
        .where(BowelEvent.occurred_at >= from_dt)
        .where(BowelEvent.occurred_at <= to_dt)
    ).all()
    for b in bowels:
        is_flare = b.bristol in (1, 2, 6, 7) or b.urgency >= 2 or b.pain >= 2
        if not is_flare:
            continue
        # Severity rolls Bristol extremity + urgency + pain into 0..1.
        bristol_extremity = abs(4 - b.bristol) / 3.0  # 4 is normal; 1 or 7 are extreme
        sev = max(bristol_extremity, b.urgency / 3.0, b.pain / 3.0)
        flares.append(
            FlareEvent(
                occurred_at=_aware(b.occurred_at),
                severity=sev,
                kind=f"bowel:bristol{b.bristol}",
                source_uuid=b.client_uuid,
            )
        )

    flares.sort(key=lambda f: f.occurred_at)
    return flares


def _flare_label(f: FlareEvent) -> str:
    """Short human label for the dashboard, e.g. 'Bloating Tue 14:00'."""
    when = f.occurred_at.strftime("%a %H:%M")
    pretty = f.kind.split(":", 1)[-1].replace("BRISTOL", "Bristol ").lower()
    return f"{pretty.capitalize()} {when}"


def _portion_grams(log: ConsumptionLog, food: FoodEntry) -> float:
    """Best-effort gram estimate when grams_eaten isn't recorded."""
    if log.grams_eaten:
        return log.grams_eaten
    if food.grams_per_unit:
        return float(food.grams_per_unit) * (log.percentage_eaten / 100.0)
    if food.package_grams:
        return float(food.package_grams) * (log.percentage_eaten / 100.0)
    return 100.0 * (log.percentage_eaten / 100.0)


def _aware(dt: datetime) -> datetime:
    return dt if dt.tzinfo is not None else dt.replace(tzinfo=timezone.utc)


def _parse_tags(json_str: str | None) -> list[str]:
    if not json_str:
        return []
    import json as _json
    try:
        data = _json.loads(json_str)
    except _json.JSONDecodeError:
        return []
    if not isinstance(data, list):
        return []
    return [str(t) for t in data if t]
