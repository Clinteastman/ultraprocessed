"""Sensor entities exposed by the Ultraprocessed integration.

Categories:
- Headline: calories today, NOVA average, UPF share, fasting countdowns,
  last meal, etc. Always enabled.
- Per-NOVA-class breakdowns: 4 calorie sensors + 4 meal-count sensors.
- Per-nutrient: one sensor per macro/micro from the backend's RDV table.
  Macros are enabled by default; micros (B-vitamins, trace minerals,
  etc.) are disabled-by-default to keep the entity registry tidy.

All sensors share a single DataUpdateCoordinator that polls
/api/v1/ha/snapshot. No N+1 - sensors are pure derivations of one blob.
"""
from __future__ import annotations

from datetime import datetime
from typing import Any

from homeassistant.components.sensor import (
    SensorDeviceClass,
    SensorEntity,
    SensorEntityDescription,
    SensorStateClass,
)
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity import DeviceInfo, EntityCategory
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from .const import DOMAIN, NUTRIENT_DEFS
from .coordinator import UltraprocessedCoordinator


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    coordinator: UltraprocessedCoordinator = hass.data[DOMAIN][entry.entry_id]

    entities: list[SensorEntity] = []

    # Headline numerics
    entities.extend([
        CaloriesTodaySensor(coordinator, entry),
        CalorieTargetSensor(coordinator, entry),
        CalorieRemainingSensor(coordinator, entry),
        NovaAverageSensor(coordinator, entry),
        Nova4CaloriesSensor(coordinator, entry),
        UpfShareSensor(coordinator, entry),
        MealsTodaySensor(coordinator, entry),
        MinutesSinceLastMealSensor(coordinator, entry),
        LastMealSensor(coordinator, entry),
        NextEatAtSensor(coordinator, entry),
        EatingWindowClosesAtSensor(coordinator, entry),
        ActiveFastingProfileSensor(coordinator, entry),
    ])

    # Per-NOVA-class breakdowns (1..4)
    for cls in (1, 2, 3, 4):
        entities.append(NovaClassCaloriesSensor(coordinator, entry, cls))
        entities.append(NovaClassMealsSensor(coordinator, entry, cls))

    # Per-nutrient sensors (~30, most disabled-by-default)
    for ndef in NUTRIENT_DEFS:
        entities.append(NutrientSensor(coordinator, entry, ndef))

    async_add_entities(entities)


def _device_info(coordinator: UltraprocessedCoordinator, entry: ConfigEntry) -> DeviceInfo:
    """Group every entity under a single 'Ultraprocessed' device row in HA."""
    return DeviceInfo(
        identifiers={(DOMAIN, entry.entry_id)},
        name="Ultraprocessed",
        manufacturer="Ultraprocessed",
        model="Self-hosted backend",
        configuration_url=coordinator.base_url,
    )


class _Base(CoordinatorEntity[UltraprocessedCoordinator], SensorEntity):
    """Shared boilerplate: device grouping + unique_id namespacing."""

    _attr_has_entity_name = True

    def __init__(self, coordinator: UltraprocessedCoordinator, entry: ConfigEntry, key: str) -> None:
        super().__init__(coordinator)
        self._entry = entry
        self._attr_unique_id = f"{entry.entry_id}_{key}"
        self._attr_device_info = _device_info(coordinator, entry)

    @property
    def _data(self) -> dict[str, Any]:
        return self.coordinator.data or {}


# ---- Headline sensors ---------------------------------------------------


class CaloriesTodaySensor(_Base):
    _attr_name = "Calories today"
    _attr_native_unit_of_measurement = "kcal"
    _attr_state_class = SensorStateClass.TOTAL_INCREASING
    _attr_icon = "mdi:fire"

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "calories_today")

    @property
    def native_value(self) -> float | None:
        return self._data.get("calories_today")


class CalorieTargetSensor(_Base):
    _attr_name = "Calorie target"
    _attr_native_unit_of_measurement = "kcal"
    _attr_icon = "mdi:target"
    _attr_entity_category = EntityCategory.DIAGNOSTIC

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "calorie_target")

    @property
    def native_value(self) -> float | None:
        return self._data.get("calorie_target_kcal")


class CalorieRemainingSensor(_Base):
    _attr_name = "Calories remaining"
    _attr_native_unit_of_measurement = "kcal"
    _attr_icon = "mdi:scale-balance"

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "calorie_remaining")

    @property
    def native_value(self) -> float | None:
        return self._data.get("calorie_remaining_kcal")


class NovaAverageSensor(_Base):
    _attr_name = "NOVA average"
    _attr_icon = "mdi:nutrition"
    _attr_state_class = SensorStateClass.MEASUREMENT
    _attr_suggested_display_precision = 2

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "nova_average")

    @property
    def native_value(self) -> float | None:
        return self._data.get("nova_average_today")


class Nova4CaloriesSensor(_Base):
    _attr_name = "Ultra-processed calories"
    _attr_native_unit_of_measurement = "kcal"
    _attr_icon = "mdi:food-off"

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "nova4_calories")

    @property
    def native_value(self) -> float | None:
        return self._data.get("nova4_calories_today")


class UpfShareSensor(_Base):
    _attr_name = "Ultra-processed share"
    _attr_native_unit_of_measurement = "%"
    _attr_icon = "mdi:percent-outline"

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "upf_share")

    @property
    def native_value(self) -> float | None:
        return self._data.get("upf_share_percent")


class MealsTodaySensor(_Base):
    _attr_name = "Meals today"
    _attr_icon = "mdi:silverware-fork-knife"

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "meals_today")

    @property
    def native_value(self) -> int | None:
        return self._data.get("meals_today")


class MinutesSinceLastMealSensor(_Base):
    _attr_name = "Minutes since last meal"
    _attr_native_unit_of_measurement = "min"
    _attr_icon = "mdi:clock-time-four-outline"
    _attr_state_class = SensorStateClass.MEASUREMENT

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "minutes_since_last_meal")

    @property
    def native_value(self) -> float | None:
        return self._data.get("minutes_since_last_meal")


class LastMealSensor(_Base):
    """State = last food name; attributes carry the rest of the meal context."""

    _attr_name = "Last meal"
    _attr_icon = "mdi:silverware-variant"

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "last_meal")

    @property
    def native_value(self) -> str | None:
        meal = self._data.get("last_meal")
        return meal.get("name") if isinstance(meal, dict) else None

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        meal = self._data.get("last_meal") or {}
        return {
            "nova_class": meal.get("nova_class"),
            "kcal": meal.get("kcal"),
            "percentage_eaten": meal.get("percentage_eaten"),
            "eaten_at": meal.get("eaten_at"),
        }


class NextEatAtSensor(_Base):
    _attr_name = "Next eat at"
    _attr_icon = "mdi:silverware-clean"
    _attr_device_class = SensorDeviceClass.TIMESTAMP

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "next_eat_at")

    @property
    def native_value(self) -> datetime | None:
        return _parse_iso(self._data.get("next_eat_at"))


class EatingWindowClosesAtSensor(_Base):
    _attr_name = "Eating window closes at"
    _attr_icon = "mdi:lock-clock"
    _attr_device_class = SensorDeviceClass.TIMESTAMP

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "eating_window_closes_at")

    @property
    def native_value(self) -> datetime | None:
        return _parse_iso(self._data.get("eating_window_closes_at"))


class ActiveFastingProfileSensor(_Base):
    """State = profile name (e.g. '16:8'); attrs hold the schedule details."""

    _attr_name = "Active fasting profile"
    _attr_icon = "mdi:timer-sand"
    _attr_entity_category = EntityCategory.DIAGNOSTIC

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "active_fasting_profile")

    @property
    def native_value(self) -> str | None:
        profile = self._data.get("active_fasting_profile")
        return profile.get("name") if isinstance(profile, dict) else None

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        profile = self._data.get("active_fasting_profile") or {}
        return {
            "schedule_type": profile.get("schedule_type"),
            "eating_window_start_minutes": profile.get("eating_window_start_minutes"),
            "eating_window_end_minutes": profile.get("eating_window_end_minutes"),
            "restricted_days_mask": profile.get("restricted_days_mask"),
            "restricted_kcal_target": profile.get("restricted_kcal_target"),
        }


# ---- Per-NOVA-class breakdowns ------------------------------------------


_NOVA_LABELS = {
    1: "whole",
    2: "processed culinary",
    3: "processed",
    4: "ultra-processed",
}


class NovaClassCaloriesSensor(_Base):
    _attr_native_unit_of_measurement = "kcal"
    _attr_state_class = SensorStateClass.TOTAL_INCREASING

    def __init__(self, coordinator, entry, cls: int) -> None:
        super().__init__(coordinator, entry, f"nova{cls}_calories")
        self._cls = cls
        self._attr_name = f"NOVA {cls} calories"
        self._attr_icon = "mdi:food-apple" if cls == 1 else "mdi:food"
        self._attr_entity_registry_enabled_default = True

    @property
    def native_value(self) -> float | None:
        breakdown = self._data.get("nova_calories") or {}
        v = breakdown.get(str(self._cls))
        return float(v) if v is not None else None

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        return {"label": _NOVA_LABELS[self._cls]}


class NovaClassMealsSensor(_Base):
    _attr_state_class = SensorStateClass.TOTAL_INCREASING

    def __init__(self, coordinator, entry, cls: int) -> None:
        super().__init__(coordinator, entry, f"nova{cls}_meals")
        self._cls = cls
        self._attr_name = f"NOVA {cls} meals"
        self._attr_icon = "mdi:counter"
        # Off by default; the calorie counts are usually the more useful read.
        self._attr_entity_registry_enabled_default = False

    @property
    def native_value(self) -> int | None:
        counts = self._data.get("nova_meal_counts") or {}
        v = counts.get(str(self._cls))
        return int(v) if v is not None else None

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        return {"label": _NOVA_LABELS[self._cls]}


# ---- Per-nutrient sensors -----------------------------------------------


class NutrientSensor(_Base):
    """One sensor per nutrient. State is grams/mg/µg consumed today;
    attributes carry the reference value and percent-of-RDV so users can
    template against e.g. 'sodium > 100% RDV' without a second sensor."""

    _attr_state_class = SensorStateClass.TOTAL_INCREASING

    def __init__(self, coordinator, entry, ndef) -> None:
        super().__init__(coordinator, entry, f"nutrient_{ndef.key}")
        self._ndef = ndef
        self._attr_name = ndef.name
        self._attr_native_unit_of_measurement = ndef.unit
        self._attr_icon = "mdi:nutrition"
        self._attr_entity_registry_enabled_default = ndef.default_enabled

    @property
    def native_value(self) -> float | None:
        return (self._data.get("nutrients_today") or {}).get(self._ndef.key, 0.0)

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        ref = (self._data.get("nutrients_reference") or {}).get(self._ndef.key)
        consumed = (self._data.get("nutrients_today") or {}).get(self._ndef.key, 0.0)
        pct = round((consumed / ref) * 100, 1) if ref else None
        return {
            "reference_daily_value": ref,
            "percent_rdv": pct,
        }


# ---- Helpers ------------------------------------------------------------


def _parse_iso(value: Any) -> datetime | None:
    """Snapshot ISO strings -> aware datetimes. None / unparseable -> None."""
    if not value or not isinstance(value, str):
        return None
    try:
        # Python's fromisoformat handles offsets; fall back to a Z->+00:00 swap.
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
