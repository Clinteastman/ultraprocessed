"""Binary sensor platform: currently_fasting only.

Useful as the trigger for "notify me when the eating window opens"
automations - far cheaper to template against than parsing the
next_eat_at timestamp.
"""
from __future__ import annotations

from typing import Any

from homeassistant.components.binary_sensor import BinarySensorDeviceClass, BinarySensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity import DeviceInfo
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from .const import DOMAIN
from .coordinator import UltraprocessedCoordinator


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    coordinator: UltraprocessedCoordinator = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([FastingBinarySensor(coordinator, entry)])


class FastingBinarySensor(CoordinatorEntity[UltraprocessedCoordinator], BinarySensorEntity):
    _attr_has_entity_name = True
    _attr_name = "Fasting"
    _attr_icon = "mdi:timer-sand"
    _attr_device_class = BinarySensorDeviceClass.RUNNING

    def __init__(self, coordinator: UltraprocessedCoordinator, entry: ConfigEntry) -> None:
        super().__init__(coordinator)
        self._entry = entry
        self._attr_unique_id = f"{entry.entry_id}_fasting"
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, entry.entry_id)},
            name="Ultraprocessed",
            manufacturer="Ultraprocessed",
            model="Self-hosted backend",
            configuration_url=coordinator.base_url,
        )

    @property
    def is_on(self) -> bool | None:
        data = self.coordinator.data or {}
        return data.get("currently_fasting")

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        data = self.coordinator.data or {}
        return {
            "next_eat_at": data.get("next_eat_at"),
            "eating_window_closes_at": data.get("eating_window_closes_at"),
        }
