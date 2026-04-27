"""Constants for the Ultraprocessed integration.

Single source of truth for the entity catalogue. The sensor platform
fans these out into one entity per row, sharing one DataUpdateCoordinator
backed by the backend's /api/v1/ha/snapshot endpoint.
"""
from __future__ import annotations

from dataclasses import dataclass

DOMAIN = "ultraprocessed"
PLATFORMS = ["sensor", "binary_sensor"]

CONF_BASE_URL = "base_url"
CONF_TOKEN = "token"

DEFAULT_SCAN_INTERVAL_SECONDS = 300  # 5 minutes; cheap snapshot endpoint.


@dataclass(frozen=True, slots=True)
class NutrientDef:
    """One sensor per macronutrient / micronutrient.

    `default_enabled=False` hides the entity in the registry by default
    so installing the integration doesn't pollute the user's entity list
    with 30+ rarely-checked micros. They can enable per-sensor in HA's
    UI when they want to graph one.
    """
    key: str
    name: str
    unit: str
    default_enabled: bool


# Mirrors backend/app/services/nutrients.py REFERENCE_DAILY_VALUES.
NUTRIENT_DEFS: tuple[NutrientDef, ...] = (
    # Macros (enabled by default)
    NutrientDef("protein_g", "Protein", "g", True),
    NutrientDef("fat_g", "Fat", "g", True),
    NutrientDef("saturated_fat_g", "Saturated fat", "g", True),
    NutrientDef("carbs_g", "Carbs", "g", True),
    NutrientDef("sugar_g", "Sugar", "g", True),
    NutrientDef("fiber_g", "Fiber", "g", True),
    NutrientDef("salt_g", "Salt", "g", True),
    NutrientDef("sodium_mg", "Sodium", "mg", True),
    # Other macros / lipids (off by default)
    NutrientDef("cholesterol_mg", "Cholesterol", "mg", False),
    NutrientDef("omega3_g", "Omega-3", "g", False),
    # Minerals (off by default)
    NutrientDef("calcium_mg", "Calcium", "mg", False),
    NutrientDef("iron_mg", "Iron", "mg", False),
    NutrientDef("potassium_mg", "Potassium", "mg", False),
    NutrientDef("magnesium_mg", "Magnesium", "mg", False),
    NutrientDef("zinc_mg", "Zinc", "mg", False),
    NutrientDef("phosphorus_mg", "Phosphorus", "mg", False),
    NutrientDef("selenium_ug", "Selenium", "µg", False),
    NutrientDef("iodine_ug", "Iodine", "µg", False),
    NutrientDef("copper_mg", "Copper", "mg", False),
    NutrientDef("manganese_mg", "Manganese", "mg", False),
    # Vitamins (off by default)
    NutrientDef("vitamin_a_ug", "Vitamin A", "µg", False),
    NutrientDef("vitamin_c_mg", "Vitamin C", "mg", False),
    NutrientDef("vitamin_d_ug", "Vitamin D", "µg", False),
    NutrientDef("vitamin_e_mg", "Vitamin E", "mg", False),
    NutrientDef("vitamin_k_ug", "Vitamin K", "µg", False),
    NutrientDef("vitamin_b1_mg", "Vitamin B1", "mg", False),
    NutrientDef("vitamin_b2_mg", "Vitamin B2", "mg", False),
    NutrientDef("vitamin_b3_mg", "Vitamin B3", "mg", False),
    NutrientDef("vitamin_b6_mg", "Vitamin B6", "mg", False),
    NutrientDef("vitamin_b12_ug", "Vitamin B12", "µg", False),
    NutrientDef("folate_ug", "Folate", "µg", False),
)
