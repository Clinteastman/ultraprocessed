from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Backend configuration. Read from environment / .env at the repo root."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        env_prefix="ULTRAPROCESSED_",
        extra="ignore",
    )

    # ---- Database ----
    database_url: str = Field(
        default="sqlite:///./data/ultraprocessed.db",
        description="SQLAlchemy URL. Default is on-disk SQLite under ./data.",
    )

    # ---- Server ----
    host: str = "0.0.0.0"
    port: int = 8000
    cors_origins: list[str] = Field(default_factory=lambda: ["*"])

    # ---- Auth ----
    pairing_code_ttl_seconds: int = 300
    token_secret: str = Field(
        default="change-me-in-production",
        description="HMAC secret used to sign device tokens.",
    )

    # ---- Provider relay (optional) ----
    relay_anthropic_api_key: str | None = None
    relay_openai_compatible_api_key: str | None = None
    relay_openai_compatible_base_url: str | None = None

    # ---- Static dashboard ----
    dashboard_dir: Path = Field(
        default=Path("/app/dashboard"),
        description="Directory of built SvelteKit assets. Served at /.",
    )

    # ---- Open Food Facts ----
    off_user_agent: str = "Ultraprocessed/0.1 (https://github.com/Clinteastman/ultraprocessed)"
    off_cache_ttl_seconds: int = 60 * 60 * 24 * 7  # 7 days


@lru_cache
def get_settings() -> Settings:
    return Settings()
