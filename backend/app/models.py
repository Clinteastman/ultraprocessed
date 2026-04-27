"""SQLModel tables - matches the phone's Room schema 1:1 so sync is mechanical."""
from __future__ import annotations

from datetime import datetime, timezone
from enum import Enum

from sqlmodel import Field, SQLModel


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class FoodSource(str, Enum):
    BARCODE = "BARCODE"
    OCR = "OCR"
    LLM = "LLM"
    MANUAL = "MANUAL"


class SyncState(str, Enum):
    PENDING = "PENDING"
    SYNCED = "SYNCED"
    ERROR = "ERROR"


class ScheduleType(str, Enum):
    SIXTEEN_EIGHT = "SIXTEEN_EIGHT"
    EIGHTEEN_SIX = "EIGHTEEN_SIX"
    TWENTY_FOUR = "TWENTY_FOUR"
    OMAD = "OMAD"
    CUSTOM = "CUSTOM"


# ---- Auth ----

class User(SQLModel, table=True):
    __tablename__ = "users"

    id: int | None = Field(default=None, primary_key=True)
    name: str
    created_at: datetime = Field(default_factory=_utcnow)


class Device(SQLModel, table=True):
    __tablename__ = "devices"

    id: int | None = Field(default=None, primary_key=True)
    user_id: int = Field(foreign_key="users.id", index=True)
    name: str
    token_hash: str = Field(index=True, unique=True)
    created_at: datetime = Field(default_factory=_utcnow)
    last_seen_at: datetime | None = None


# ---- Domain ----

class FoodEntry(SQLModel, table=True):
    __tablename__ = "food_entries"

    id: int | None = Field(default=None, primary_key=True)
    user_id: int = Field(foreign_key="users.id", index=True)
    client_uuid: str = Field(index=True, unique=True)

    name: str
    brand: str | None = None
    barcode: str | None = Field(default=None, index=True)

    nova_class: int
    nova_rationale: str

    kcal_per_100g: float | None = None
    kcal_per_unit: float | None = None
    serving_description: str | None = None

    image_url: str | None = None
    ingredients_json: str = "[]"
    nutrients_json: str | None = None

    source: FoodSource
    confidence: float = 1.0

    created_at: datetime = Field(default_factory=_utcnow)
    updated_at: datetime = Field(default_factory=_utcnow)


class ConsumptionLog(SQLModel, table=True):
    __tablename__ = "consumption_logs"

    id: int | None = Field(default=None, primary_key=True)
    user_id: int = Field(foreign_key="users.id", index=True)
    client_uuid: str = Field(index=True, unique=True)
    food_client_uuid: str = Field(index=True)

    percentage_eaten: int
    eaten_at: datetime = Field(index=True)

    lat: float | None = None
    lng: float | None = None
    location_label: str | None = None

    kcal_consumed_snapshot: float | None = None
    nutrients_consumed_json: str | None = None

    created_at: datetime = Field(default_factory=_utcnow)


class FastingProfile(SQLModel, table=True):
    __tablename__ = "fasting_profiles"

    id: int | None = Field(default=None, primary_key=True)
    user_id: int = Field(foreign_key="users.id", index=True)
    name: str
    schedule_type: ScheduleType
    eating_window_start_minutes: int
    eating_window_end_minutes: int
    active: bool = False


# ---- Open Food Facts cache ----

class OffCache(SQLModel, table=True):
    __tablename__ = "off_cache"

    barcode: str = Field(primary_key=True)
    payload_json: str
    fetched_at: datetime = Field(default_factory=_utcnow)
    expires_at: datetime
