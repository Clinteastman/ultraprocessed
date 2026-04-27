"""DataUpdateCoordinator backed by the backend's /api/v1/ha/snapshot."""
from __future__ import annotations

import logging
from datetime import timedelta
from typing import Any

import aiohttp
from homeassistant.core import HomeAssistant
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.update_coordinator import DataUpdateCoordinator, UpdateFailed

from .const import DEFAULT_SCAN_INTERVAL_SECONDS, DOMAIN

_LOGGER = logging.getLogger(__name__)


class UltraprocessedCoordinator(DataUpdateCoordinator[dict[str, Any]]):
    """Pulls one snapshot blob per refresh; sensors fan out client-side."""

    def __init__(self, hass: HomeAssistant, base_url: str, token: str) -> None:
        super().__init__(
            hass,
            _LOGGER,
            name=DOMAIN,
            update_interval=timedelta(seconds=DEFAULT_SCAN_INTERVAL_SECONDS),
        )
        self._base_url = base_url.rstrip("/")
        self._token = token
        self._session = async_get_clientsession(hass)

    @property
    def base_url(self) -> str:
        return self._base_url

    async def _async_update_data(self) -> dict[str, Any]:
        url = f"{self._base_url}/api/v1/ha/snapshot"
        headers = {"Authorization": f"Bearer {self._token}"}
        try:
            async with self._session.get(url, headers=headers, timeout=aiohttp.ClientTimeout(total=10)) as resp:
                if resp.status == 401:
                    raise UpdateFailed("Backend rejected the device token (401). Re-pair the integration.")
                if resp.status >= 400:
                    body = (await resp.text())[:200]
                    raise UpdateFailed(f"Backend returned {resp.status}: {body}")
                return await resp.json()
        except aiohttp.ClientError as err:
            raise UpdateFailed(f"Backend unreachable: {err}") from err
