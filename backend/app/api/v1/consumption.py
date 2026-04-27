"""ConsumptionLog upsert + queries."""
from __future__ import annotations

from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlmodel import Session, select

from app.auth import get_current_user
from app.db import get_session
from app.models import ConsumptionLog, User

router = APIRouter(prefix="/consumption", tags=["consumption"])


class ConsumptionLogDto(BaseModel):
    client_uuid: str
    food_client_uuid: str
    percentage_eaten: int
    eaten_at: datetime
    lat: float | None = None
    lng: float | None = None
    location_label: str | None = None
    kcal_consumed_snapshot: float | None = None
    nutrients_consumed_json: str | None = None
    created_at: datetime

    def to_model(self, user_id: int) -> ConsumptionLog:
        return ConsumptionLog(
            user_id=user_id,
            client_uuid=self.client_uuid,
            food_client_uuid=self.food_client_uuid,
            percentage_eaten=self.percentage_eaten,
            eaten_at=self.eaten_at,
            lat=self.lat,
            lng=self.lng,
            location_label=self.location_label,
            kcal_consumed_snapshot=self.kcal_consumed_snapshot,
            nutrients_consumed_json=self.nutrients_consumed_json,
            created_at=self.created_at,
        )


@router.post("", response_model=list[ConsumptionLogDto])
def upsert_consumption(
    payload: list[ConsumptionLogDto],
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> list[ConsumptionLogDto]:
    saved: list[ConsumptionLog] = []
    for dto in payload:
        existing = session.exec(
            select(ConsumptionLog).where(ConsumptionLog.client_uuid == dto.client_uuid)
        ).first()
        if existing is None:
            row = dto.to_model(user.id)
            session.add(row)
            saved.append(row)
        else:
            if existing.user_id != user.id:
                raise HTTPException(status_code=403, detail="log owned by another user")
            existing.food_client_uuid = dto.food_client_uuid
            existing.percentage_eaten = dto.percentage_eaten
            existing.eaten_at = dto.eaten_at
            existing.lat = dto.lat
            existing.lng = dto.lng
            existing.location_label = dto.location_label
            existing.kcal_consumed_snapshot = dto.kcal_consumed_snapshot
            existing.nutrients_consumed_json = dto.nutrients_consumed_json
            session.add(existing)
            saved.append(existing)
    session.commit()
    for row in saved:
        session.refresh(row)
    return [_to_dto(row) for row in saved]


@router.get("", response_model=list[ConsumptionLogDto])
def list_consumption(
    from_: datetime | None = Query(default=None, alias="from"),
    to: datetime | None = Query(default=None),
    limit: int = Query(default=500, le=5000),
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> list[ConsumptionLogDto]:
    stmt = select(ConsumptionLog).where(ConsumptionLog.user_id == user.id)
    if from_ is not None:
        stmt = stmt.where(ConsumptionLog.eaten_at >= from_)
    if to is not None:
        stmt = stmt.where(ConsumptionLog.eaten_at <= to)
    stmt = stmt.order_by(ConsumptionLog.eaten_at.desc()).limit(limit)
    return [_to_dto(row) for row in session.exec(stmt).all()]


def _to_dto(row: ConsumptionLog) -> ConsumptionLogDto:
    return ConsumptionLogDto(
        client_uuid=row.client_uuid,
        food_client_uuid=row.food_client_uuid,
        percentage_eaten=row.percentage_eaten,
        eaten_at=row.eaten_at,
        lat=row.lat,
        lng=row.lng,
        location_label=row.location_label,
        kcal_consumed_snapshot=row.kcal_consumed_snapshot,
        nutrients_consumed_json=row.nutrients_consumed_json,
        created_at=row.created_at,
    )
