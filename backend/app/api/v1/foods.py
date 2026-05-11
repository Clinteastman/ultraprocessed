"""FoodEntry CRUD + bulk upsert for sync."""
from __future__ import annotations

import json
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlmodel import Session, select

from app.auth import get_current_user
from app.db import get_session
from app.models import FodmapLevel, FoodEntry, FoodSource, User
from app.services import fodmap as fodmap_svc

router = APIRouter(prefix="/foods", tags=["foods"])


class FoodEntryDto(BaseModel):
    """Wire format for sync. Mirrors the phone's Room columns 1:1."""

    client_uuid: str
    name: str
    brand: str | None = None
    barcode: str | None = None
    nova_class: int
    nova_rationale: str
    kcal_per_100g: float | None = None
    kcal_per_unit: float | None = None
    grams_per_unit: float | None = None
    package_grams: float | None = None
    serving_description: str | None = None
    image_url: str | None = None
    ingredients_json: str = "[]"
    nutrients_json: str | None = None
    fodmap_level: FodmapLevel = FodmapLevel.UNKNOWN
    fodmap_tags_json: str = "[]"
    fodmap_notes: str | None = None
    source: FoodSource
    confidence: float = 1.0
    created_at: datetime
    updated_at: datetime

    def to_model(self, user_id: int) -> FoodEntry:
        return FoodEntry(
            user_id=user_id,
            client_uuid=self.client_uuid,
            name=self.name,
            brand=self.brand,
            barcode=self.barcode,
            nova_class=self.nova_class,
            nova_rationale=self.nova_rationale,
            kcal_per_100g=self.kcal_per_100g,
            kcal_per_unit=self.kcal_per_unit,
            grams_per_unit=self.grams_per_unit,
            package_grams=self.package_grams,
            serving_description=self.serving_description,
            image_url=self.image_url,
            ingredients_json=self.ingredients_json,
            nutrients_json=self.nutrients_json,
            fodmap_level=self.fodmap_level,
            fodmap_tags_json=self.fodmap_tags_json,
            fodmap_notes=self.fodmap_notes,
            source=self.source,
            confidence=self.confidence,
            created_at=self.created_at,
            updated_at=self.updated_at,
        )


class FodmapPatchDto(BaseModel):
    """User-driven correction to a food's FODMAP classification."""
    fodmap_level: FodmapLevel
    fodmap_tags: list[str] = []
    fodmap_notes: str | None = None


def _auto_classify(dto: FoodEntryDto) -> tuple[FodmapLevel, str]:
    """If the client didn't supply FODMAP info, classify from the name +
    ingredients. Returns (level, tags_json). Never overrides explicit
    client input — UNKNOWN with empty tags means 'please classify me'."""
    if dto.fodmap_level != FodmapLevel.UNKNOWN or dto.fodmap_tags_json not in (None, "", "[]"):
        return dto.fodmap_level, dto.fodmap_tags_json
    try:
        ingredients = json.loads(dto.ingredients_json) if dto.ingredients_json else []
    except json.JSONDecodeError:
        ingredients = []
    if not isinstance(ingredients, list):
        ingredients = []
    classification = fodmap_svc.classify(dto.name, [str(i) for i in ingredients])
    return classification.level, json.dumps(classification.tags)


@router.post("", response_model=list[FoodEntryDto])
def upsert_foods(
    payload: list[FoodEntryDto],
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> list[FoodEntryDto]:
    if not payload:
        return []

    saved: list[FoodEntry] = []
    for dto in payload:
        # Auto-classify FODMAP if the client didn't provide it. Cheap
        # keyword match; users can correct via PATCH /foods/{uuid}/fodmap.
        auto_level, auto_tags_json = _auto_classify(dto)

        existing = session.exec(
            select(FoodEntry).where(FoodEntry.client_uuid == dto.client_uuid)
        ).first()
        if existing is None:
            row = dto.to_model(user.id)
            row.fodmap_level = auto_level
            row.fodmap_tags_json = auto_tags_json
            session.add(row)
            saved.append(row)
        else:
            if existing.user_id != user.id:
                raise HTTPException(status_code=403, detail=f"client_uuid {dto.client_uuid} owned by another user")
            # Last-writer-wins on updated_at.
            if dto.updated_at >= existing.updated_at:
                existing.name = dto.name
                existing.brand = dto.brand
                existing.barcode = dto.barcode
                existing.nova_class = dto.nova_class
                existing.nova_rationale = dto.nova_rationale
                existing.kcal_per_100g = dto.kcal_per_100g
                existing.kcal_per_unit = dto.kcal_per_unit
                existing.grams_per_unit = dto.grams_per_unit
                existing.package_grams = dto.package_grams
                existing.serving_description = dto.serving_description
                existing.image_url = dto.image_url
                existing.ingredients_json = dto.ingredients_json
                existing.nutrients_json = dto.nutrients_json
                # Don't clobber a user-corrected FODMAP entry with an
                # auto-classified UNKNOWN. Only overwrite when the
                # incoming DTO actually carries FODMAP info.
                if dto.fodmap_level != FodmapLevel.UNKNOWN or dto.fodmap_tags_json not in (None, "", "[]"):
                    existing.fodmap_level = dto.fodmap_level
                    existing.fodmap_tags_json = dto.fodmap_tags_json
                    existing.fodmap_notes = dto.fodmap_notes
                elif existing.fodmap_level == FodmapLevel.UNKNOWN:
                    existing.fodmap_level = auto_level
                    existing.fodmap_tags_json = auto_tags_json
                existing.source = dto.source
                existing.confidence = dto.confidence
                existing.updated_at = dto.updated_at
                session.add(existing)
            saved.append(existing)
    session.commit()
    for row in saved:
        session.refresh(row)
    return [_to_dto(row) for row in saved]


@router.get("", response_model=list[FoodEntryDto])
def list_foods(
    since: datetime | None = Query(default=None),
    limit: int = Query(default=200, le=2000),
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> list[FoodEntryDto]:
    stmt = select(FoodEntry).where(FoodEntry.user_id == user.id).order_by(FoodEntry.updated_at.desc())
    if since is not None:
        stmt = stmt.where(FoodEntry.updated_at > since)
    stmt = stmt.limit(limit)
    return [_to_dto(row) for row in session.exec(stmt).all()]


@router.get("/by-uuid/{client_uuid}", response_model=FoodEntryDto)
def get_food(
    client_uuid: str,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> FoodEntryDto:
    row = session.exec(
        select(FoodEntry)
        .where(FoodEntry.client_uuid == client_uuid)
        .where(FoodEntry.user_id == user.id)
    ).first()
    if row is None:
        raise HTTPException(status_code=404, detail="not found")
    return _to_dto(row)


@router.delete("/{client_uuid}", status_code=204)
def delete_food(
    client_uuid: str,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> None:
    row = session.exec(
        select(FoodEntry).where(FoodEntry.client_uuid == client_uuid)
    ).first()
    if row is None:
        return None
    if row.user_id != user.id:
        raise HTTPException(status_code=403, detail="not yours")
    session.delete(row)
    session.commit()


@router.patch("/by-uuid/{client_uuid}/fodmap", response_model=FoodEntryDto)
def patch_fodmap(
    client_uuid: str,
    payload: FodmapPatchDto,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> FoodEntryDto:
    row = session.exec(
        select(FoodEntry)
        .where(FoodEntry.client_uuid == client_uuid)
        .where(FoodEntry.user_id == user.id)
    ).first()
    if row is None:
        raise HTTPException(status_code=404, detail="not found")
    row.fodmap_level = payload.fodmap_level
    row.fodmap_tags_json = json.dumps(sorted({t.lower() for t in payload.fodmap_tags if t}))
    row.fodmap_notes = payload.fodmap_notes
    row.updated_at = datetime.now(timezone.utc)
    session.add(row)
    session.commit()
    session.refresh(row)
    return _to_dto(row)


def _to_dto(row: FoodEntry) -> FoodEntryDto:
    return FoodEntryDto(
        client_uuid=row.client_uuid,
        name=row.name,
        brand=row.brand,
        barcode=row.barcode,
        nova_class=row.nova_class,
        nova_rationale=row.nova_rationale,
        kcal_per_100g=row.kcal_per_100g,
        kcal_per_unit=row.kcal_per_unit,
        grams_per_unit=row.grams_per_unit,
        package_grams=row.package_grams,
        serving_description=row.serving_description,
        image_url=row.image_url,
        ingredients_json=row.ingredients_json,
        nutrients_json=row.nutrients_json,
        fodmap_level=row.fodmap_level,
        fodmap_tags_json=row.fodmap_tags_json,
        fodmap_notes=row.fodmap_notes,
        source=row.source,
        confidence=row.confidence,
        created_at=row.created_at,
        updated_at=row.updated_at,
    )
