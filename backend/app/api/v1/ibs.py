"""IBS tracking endpoints: bowel events, symptom events, daily check-ins.

All three follow the same `client_uuid`-keyed upsert pattern used by
ConsumptionLog so the Android app can sync mechanically and reconcile
offline writes.
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlmodel import Session, select

from app.auth import get_current_user
from app.db import get_session
from app.models import BowelEvent, DailyCheckin, FodmapLevel, SymptomEvent, SymptomKind, User
from app.services import triggers as triggers_svc

router = APIRouter(prefix="/ibs", tags=["ibs"])


# ---------- Bowel events ----------

class BowelEventDto(BaseModel):
    client_uuid: str
    occurred_at: datetime
    bristol: int
    urgency: int = 0
    completeness: int = 2
    pain: int = 0
    blood: bool = False
    mucus: bool = False
    notes: str | None = None
    created_at: datetime

    def to_model(self, user_id: int) -> BowelEvent:
        return BowelEvent(
            user_id=user_id,
            client_uuid=self.client_uuid,
            occurred_at=self.occurred_at,
            bristol=self.bristol,
            urgency=self.urgency,
            completeness=self.completeness,
            pain=self.pain,
            blood=self.blood,
            mucus=self.mucus,
            notes=self.notes,
            created_at=self.created_at,
        )


def _bowel_to_dto(row: BowelEvent) -> BowelEventDto:
    return BowelEventDto(
        client_uuid=row.client_uuid,
        occurred_at=row.occurred_at,
        bristol=row.bristol,
        urgency=row.urgency,
        completeness=row.completeness,
        pain=row.pain,
        blood=row.blood,
        mucus=row.mucus,
        notes=row.notes,
        created_at=row.created_at,
    )


@router.post("/bowel", response_model=list[BowelEventDto])
def upsert_bowel(
    payload: list[BowelEventDto],
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> list[BowelEventDto]:
    saved: list[BowelEvent] = []
    for dto in payload:
        if not 1 <= dto.bristol <= 7:
            raise HTTPException(status_code=422, detail=f"bristol must be 1..7, got {dto.bristol}")
        existing = session.exec(
            select(BowelEvent).where(BowelEvent.client_uuid == dto.client_uuid)
        ).first()
        if existing is None:
            row = dto.to_model(user.id)
            session.add(row)
            saved.append(row)
        else:
            if existing.user_id != user.id:
                raise HTTPException(status_code=403, detail="event owned by another user")
            existing.occurred_at = dto.occurred_at
            existing.bristol = dto.bristol
            existing.urgency = dto.urgency
            existing.completeness = dto.completeness
            existing.pain = dto.pain
            existing.blood = dto.blood
            existing.mucus = dto.mucus
            existing.notes = dto.notes
            session.add(existing)
            saved.append(existing)
    session.commit()
    for row in saved:
        session.refresh(row)
    return [_bowel_to_dto(r) for r in saved]


@router.delete("/bowel/{client_uuid}", status_code=204)
def delete_bowel(
    client_uuid: str,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> None:
    row = session.exec(
        select(BowelEvent).where(BowelEvent.client_uuid == client_uuid)
    ).first()
    if row is None:
        return None
    if row.user_id != user.id:
        raise HTTPException(status_code=403, detail="not yours")
    session.delete(row)
    session.commit()


@router.get("/bowel", response_model=list[BowelEventDto])
def list_bowel(
    from_: datetime | None = Query(default=None, alias="from"),
    to: datetime | None = Query(default=None),
    limit: int = Query(default=500, le=5000),
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> list[BowelEventDto]:
    stmt = select(BowelEvent).where(BowelEvent.user_id == user.id)
    if from_ is not None:
        stmt = stmt.where(BowelEvent.occurred_at >= from_)
    if to is not None:
        stmt = stmt.where(BowelEvent.occurred_at <= to)
    stmt = stmt.order_by(BowelEvent.occurred_at.desc()).limit(limit)
    return [_bowel_to_dto(r) for r in session.exec(stmt).all()]


# ---------- Symptom events ----------

class SymptomEventDto(BaseModel):
    client_uuid: str
    occurred_at: datetime
    kind: SymptomKind
    severity: int
    duration_minutes: int | None = None
    location: str | None = None
    notes: str | None = None
    created_at: datetime

    def to_model(self, user_id: int) -> SymptomEvent:
        return SymptomEvent(
            user_id=user_id,
            client_uuid=self.client_uuid,
            occurred_at=self.occurred_at,
            kind=self.kind,
            severity=self.severity,
            duration_minutes=self.duration_minutes,
            location=self.location,
            notes=self.notes,
            created_at=self.created_at,
        )


def _symptom_to_dto(row: SymptomEvent) -> SymptomEventDto:
    return SymptomEventDto(
        client_uuid=row.client_uuid,
        occurred_at=row.occurred_at,
        kind=row.kind,
        severity=row.severity,
        duration_minutes=row.duration_minutes,
        location=row.location,
        notes=row.notes,
        created_at=row.created_at,
    )


@router.post("/symptoms", response_model=list[SymptomEventDto])
def upsert_symptoms(
    payload: list[SymptomEventDto],
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> list[SymptomEventDto]:
    saved: list[SymptomEvent] = []
    for dto in payload:
        if not 0 <= dto.severity <= 3:
            raise HTTPException(status_code=422, detail=f"severity must be 0..3, got {dto.severity}")
        existing = session.exec(
            select(SymptomEvent).where(SymptomEvent.client_uuid == dto.client_uuid)
        ).first()
        if existing is None:
            row = dto.to_model(user.id)
            session.add(row)
            saved.append(row)
        else:
            if existing.user_id != user.id:
                raise HTTPException(status_code=403, detail="event owned by another user")
            existing.occurred_at = dto.occurred_at
            existing.kind = dto.kind
            existing.severity = dto.severity
            existing.duration_minutes = dto.duration_minutes
            existing.location = dto.location
            existing.notes = dto.notes
            session.add(existing)
            saved.append(existing)
    session.commit()
    for row in saved:
        session.refresh(row)
    return [_symptom_to_dto(r) for r in saved]


@router.delete("/symptoms/{client_uuid}", status_code=204)
def delete_symptom(
    client_uuid: str,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> None:
    row = session.exec(
        select(SymptomEvent).where(SymptomEvent.client_uuid == client_uuid)
    ).first()
    if row is None:
        return None
    if row.user_id != user.id:
        raise HTTPException(status_code=403, detail="not yours")
    session.delete(row)
    session.commit()


@router.get("/symptoms", response_model=list[SymptomEventDto])
def list_symptoms(
    from_: datetime | None = Query(default=None, alias="from"),
    to: datetime | None = Query(default=None),
    kind: SymptomKind | None = Query(default=None),
    limit: int = Query(default=500, le=5000),
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> list[SymptomEventDto]:
    stmt = select(SymptomEvent).where(SymptomEvent.user_id == user.id)
    if from_ is not None:
        stmt = stmt.where(SymptomEvent.occurred_at >= from_)
    if to is not None:
        stmt = stmt.where(SymptomEvent.occurred_at <= to)
    if kind is not None:
        stmt = stmt.where(SymptomEvent.kind == kind)
    stmt = stmt.order_by(SymptomEvent.occurred_at.desc()).limit(limit)
    return [_symptom_to_dto(r) for r in session.exec(stmt).all()]


# ---------- Daily check-ins ----------

class DailyCheckinDto(BaseModel):
    client_uuid: str
    day: str  # 'YYYY-MM-DD'
    bloating_overall: int = 0
    heartburn_overall: int = 0
    gut_score: int | None = None
    mood: int | None = None
    stress: int | None = None
    period: bool = False
    notes: str | None = None
    created_at: datetime
    updated_at: datetime

    def to_model(self, user_id: int) -> DailyCheckin:
        return DailyCheckin(
            user_id=user_id,
            client_uuid=self.client_uuid,
            day=self.day,
            bloating_overall=self.bloating_overall,
            heartburn_overall=self.heartburn_overall,
            gut_score=self.gut_score,
            mood=self.mood,
            stress=self.stress,
            period=self.period,
            notes=self.notes,
            created_at=self.created_at,
            updated_at=self.updated_at,
        )


def _checkin_to_dto(row: DailyCheckin) -> DailyCheckinDto:
    return DailyCheckinDto(
        client_uuid=row.client_uuid,
        day=row.day,
        bloating_overall=row.bloating_overall,
        heartburn_overall=row.heartburn_overall,
        gut_score=row.gut_score,
        mood=row.mood,
        stress=row.stress,
        period=row.period,
        notes=row.notes,
        created_at=row.created_at,
        updated_at=row.updated_at,
    )


@router.post("/daily", response_model=list[DailyCheckinDto])
def upsert_daily(
    payload: list[DailyCheckinDto],
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> list[DailyCheckinDto]:
    saved: list[DailyCheckin] = []
    for dto in payload:
        existing = session.exec(
            select(DailyCheckin).where(DailyCheckin.client_uuid == dto.client_uuid)
        ).first()
        if existing is None:
            # Also dedupe by (user, day) — if the user logs from two devices
            # for the same calendar date, keep one row and update it.
            same_day = session.exec(
                select(DailyCheckin)
                .where(DailyCheckin.user_id == user.id)
                .where(DailyCheckin.day == dto.day)
            ).first()
            if same_day is not None:
                same_day.bloating_overall = dto.bloating_overall
                same_day.heartburn_overall = dto.heartburn_overall
                same_day.gut_score = dto.gut_score
                same_day.mood = dto.mood
                same_day.stress = dto.stress
                same_day.period = dto.period
                same_day.notes = dto.notes
                same_day.updated_at = dto.updated_at
                session.add(same_day)
                saved.append(same_day)
            else:
                row = dto.to_model(user.id)
                session.add(row)
                saved.append(row)
        else:
            if existing.user_id != user.id:
                raise HTTPException(status_code=403, detail="check-in owned by another user")
            existing.day = dto.day
            existing.bloating_overall = dto.bloating_overall
            existing.heartburn_overall = dto.heartburn_overall
            existing.gut_score = dto.gut_score
            existing.mood = dto.mood
            existing.stress = dto.stress
            existing.period = dto.period
            existing.notes = dto.notes
            existing.updated_at = dto.updated_at
            session.add(existing)
            saved.append(existing)
    session.commit()
    for row in saved:
        session.refresh(row)
    return [_checkin_to_dto(r) for r in saved]


@router.get("/daily", response_model=list[DailyCheckinDto])
def list_daily(
    from_day: str | None = Query(default=None, alias="from"),
    to_day: str | None = Query(default=None, alias="to"),
    limit: int = Query(default=400, le=4000),
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> list[DailyCheckinDto]:
    stmt = select(DailyCheckin).where(DailyCheckin.user_id == user.id)
    if from_day is not None:
        stmt = stmt.where(DailyCheckin.day >= from_day)
    if to_day is not None:
        stmt = stmt.where(DailyCheckin.day <= to_day)
    stmt = stmt.order_by(DailyCheckin.day.desc()).limit(limit)
    return [_checkin_to_dto(r) for r in session.exec(stmt).all()]


@router.delete("/daily/{client_uuid}", status_code=204)
def delete_daily(
    client_uuid: str,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> None:
    row = session.exec(
        select(DailyCheckin).where(DailyCheckin.client_uuid == client_uuid)
    ).first()
    if row is None:
        return None
    if row.user_id != user.id:
        raise HTTPException(status_code=403, detail="not yours")
    session.delete(row)
    session.commit()


# ---------- Trigger correlation ----------

class TriggerScoreDto(BaseModel):
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
    sample_flares: list[str]


class TriggerReportDto(BaseModel):
    from_dt: datetime
    to_dt: datetime
    lookback_hours: float
    half_life_hours: float
    flare_severity_threshold: int
    flares_considered: int
    foods_scored: int
    foods: list[TriggerScoreDto]


@router.get("/triggers", response_model=TriggerReportDto)
def trigger_report(
    days: int = Query(default=14, ge=1, le=180),
    lookback_hours: float = Query(default=triggers_svc.DEFAULT_LOOKBACK_HOURS, ge=2, le=120),
    half_life_hours: float = Query(default=triggers_svc.DEFAULT_HALF_LIFE_HOURS, ge=1, le=72),
    severity_threshold: int = Query(default=triggers_svc.DEFAULT_FLARE_SEVERITY_THRESHOLD, ge=1, le=3),
    limit: int = Query(default=25, ge=1, le=100),
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> TriggerReportDto:
    """Rank foods by their estimated implication in IBS flares over the
    last `days` days. See `services/triggers.py` for the full algorithm."""
    now = datetime.now(timezone.utc)
    from_dt = now - timedelta(days=days)
    foods = triggers_svc.compute_triggers(
        session=session,
        user_id=user.id,
        from_dt=from_dt,
        to_dt=now,
        lookback_hours=lookback_hours,
        half_life_hours=half_life_hours,
        severity_threshold=severity_threshold,
        limit=limit,
    )
    return TriggerReportDto(
        from_dt=from_dt,
        to_dt=now,
        lookback_hours=lookback_hours,
        half_life_hours=half_life_hours,
        flare_severity_threshold=severity_threshold,
        flares_considered=_count_flares(session, user.id, from_dt, now, severity_threshold),
        foods_scored=len(foods),
        foods=[
            TriggerScoreDto(
                food_client_uuid=f.food_client_uuid,
                food_name=f.food_name,
                food_brand=f.food_brand,
                fodmap_level=f.fodmap_level,
                fodmap_tags=f.fodmap_tags,
                nova_class=f.nova_class,
                score=f.score,
                flare_count=f.flare_count,
                baseline_count=f.baseline_count,
                last_flare_at=f.last_flare_at,
                sample_flares=f.sample_flares,
            )
            for f in foods
        ],
    )


def _count_flares(
    session: Session,
    user_id: int,
    from_dt: datetime,
    to_dt: datetime,
    severity_threshold: int,
) -> int:
    n_symptoms = len(session.exec(
        select(SymptomEvent)
        .where(SymptomEvent.user_id == user_id)
        .where(SymptomEvent.occurred_at >= from_dt)
        .where(SymptomEvent.occurred_at <= to_dt)
        .where(SymptomEvent.severity >= severity_threshold)
    ).all())
    bowels = session.exec(
        select(BowelEvent)
        .where(BowelEvent.user_id == user_id)
        .where(BowelEvent.occurred_at >= from_dt)
        .where(BowelEvent.occurred_at <= to_dt)
    ).all()
    n_bowels = sum(1 for b in bowels if b.bristol in (1, 2, 6, 7) or b.urgency >= 2 or b.pain >= 2)
    return n_symptoms + n_bowels
