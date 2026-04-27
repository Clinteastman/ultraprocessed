"""Per-user editable targets (calorie target for now; room to grow)."""
from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlmodel import Session

from app.auth import get_current_user
from app.db import get_session
from app.models import User, UserTargets

router = APIRouter(prefix="/targets", tags=["targets"])


class TargetsDto(BaseModel):
    calorie_target_kcal: float = Field(ge=0)


def _get_or_create(session: Session, user_id: int) -> UserTargets:
    row = session.get(UserTargets, user_id)
    if row is None:
        row = UserTargets(user_id=user_id)
        session.add(row)
        session.commit()
        session.refresh(row)
    return row


@router.get("", response_model=TargetsDto)
def get_targets(
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> TargetsDto:
    row = _get_or_create(session, user.id)
    return TargetsDto(calorie_target_kcal=row.calorie_target_kcal)


@router.put("", response_model=TargetsDto)
def put_targets(
    payload: TargetsDto,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> TargetsDto:
    row = _get_or_create(session, user.id)
    row.calorie_target_kcal = payload.calorie_target_kcal
    session.add(row)
    session.commit()
    session.refresh(row)
    return TargetsDto(calorie_target_kcal=row.calorie_target_kcal)
