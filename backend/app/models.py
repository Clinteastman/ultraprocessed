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
    # Time-restricted eating (daily eating window)
    SIXTEEN_EIGHT = "SIXTEEN_EIGHT"
    EIGHTEEN_SIX = "EIGHTEEN_SIX"
    TWENTY_FOUR = "TWENTY_FOUR"  # 20:4 (Warrior diet)
    OMAD = "OMAD"
    # Multi-day calorie-restriction patterns
    FIVE_TWO = "FIVE_TWO"      # 5 normal + 2 restricted days/week
    FOUR_THREE = "FOUR_THREE"  # 4 normal + 3 restricted
    ADF = "ADF"                # Alternate-day fasting
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
    grams_per_unit: float | None = None
    package_grams: float | None = None
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
    grams_eaten: float | None = None
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

    # Time-restricted-eating window (only meaningful for TRE schedules
    # and CUSTOM; ignored for FIVE_TWO / FOUR_THREE / ADF).
    eating_window_start_minutes: int
    eating_window_end_minutes: int

    # Multi-day patterns: bitmask of days that are restricted-calorie.
    # Bit 0 = Monday, bit 6 = Sunday. 0 = not used (TRE).
    restricted_days_mask: int = 0

    # Calorie cap for a restricted day. Common defaults: 500 (women) or
    # 600 (men) on 5:2; ~25% of TDEE for ADF. Null if not applicable.
    restricted_kcal_target: int | None = None

    active: bool = False


class UserTargets(SQLModel, table=True):
    __tablename__ = "user_targets"

    user_id: int = Field(foreign_key="users.id", primary_key=True)
    calorie_target_kcal: float = 2000.0


# ---- Open Food Facts cache ----

class OffCache(SQLModel, table=True):
    __tablename__ = "off_cache"

    barcode: str = Field(primary_key=True)
    payload_json: str
    fetched_at: datetime = Field(default_factory=_utcnow)
    expires_at: datetime
