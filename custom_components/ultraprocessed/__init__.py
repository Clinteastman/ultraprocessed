"""Ultraprocessed Home Assistant integration.

Wires the config flow's backend URL + bearer token into a shared
DataUpdateCoordinator and forwards setup to the sensor / binary_sensor
platforms.
"""
from __future__ import annotations

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant

from .const import CONF_BASE_URL, CONF_TOKEN, DOMAIN, PLATFORMS
from .coordinator import UltraprocessedCoordinator


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    coordinator = UltraprocessedCoordinator(
        hass=hass,
        base_url=entry.data[CONF_BASE_URL],
        token=entry.data[CONF_TOKEN],
    )
    await coordinator.async_config_entry_first_refresh()
    hass.data.setdefault(DOMAIN, {})[entry.entry_id] = coordinator
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    unloaded = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unloaded:
        hass.data[DOMAIN].pop(entry.entry_id, None)
    return unloaded
