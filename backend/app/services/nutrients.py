"""Nutrient aggregation + EU NRV reference values.

Reference values are EU Nutrient Reference Values per Regulation
(EU) No 1169/2011 Annex XIII for adults. Vitamin D follows the more
recent UK SACN / EFSA recommendation of 10ug/day (the regulation lists
5ug, but most modern guidance has moved up).

Sodium and salt are deliberately set to the WHO upper-limit guidance
(<=2g sodium / <=5g salt per day); calories don't have a fixed NRV but
2000 kcal is the EU labelling reference.
"""
from __future__ import annotations

import json
from collections.abc import Iterable
from dataclasses import dataclass

from app.models import ConsumptionLog


# ---- Reference daily values (adult) ----

REFERENCE_DAILY_VALUES: dict[str, float] = {
    # Macros
    "protein_g": 50.0,
    "fat_g": 70.0,
    "saturated_fat_g": 20.0,
    "carbs_g": 260.0,
    "sugar_g": 90.0,
    "fiber_g": 30.0,
    "salt_g": 5.0,
    "sodium_mg": 2000.0,
    "cholesterol_mg": 300.0,
    "omega3_g": 1.6,
    # Minerals
    "calcium_mg": 800.0,
    "iron_mg": 14.0,
    "potassium_mg": 3500.0,
    "magnesium_mg": 375.0,
    "zinc_mg": 10.0,
    "phosphorus_mg": 700.0,
    "selenium_ug": 55.0,
    "iodine_ug": 150.0,
    "copper_mg": 1.0,
    "manganese_mg": 2.0,
    # Vitamins
    "vitamin_a_ug": 800.0,
    "vitamin_c_mg": 80.0,
    "vitamin_d_ug": 10.0,
    "vitamin_e_mg": 12.0,
    "vitamin_k_ug": 75.0,
    "vitamin_b1_mg": 1.1,
    "vitamin_b2_mg": 1.4,
    "vitamin_b3_mg": 16.0,
    "vitamin_b6_mg": 1.4,
    "vitamin_b12_ug": 2.5,
    "folate_ug": 200.0,
}

# Nutrients where exceeding the reference is undesirable (so the dashboard
# flags "high" instead of "great"). All others flag "low" when below RDV.
UPPER_LIMIT_NUTRIENTS: frozenset[str] = frozenset({
    "saturated_fat_g",
    "sugar_g",
    "salt_g",
    "sodium_mg",
    "cholesterol_mg",
})

# Calories aren't a nutrient with an NRV but we expose a default for context.
DEFAULT_CALORIE_TARGET: float = 2000.0


@dataclass(slots=True)
class NutrientAdequacy:
    consumed: float
    reference: float
    pct: float
    direction: str  # "low" | "ok" | "high"


def aggregate_nutrients(logs: Iterable[ConsumptionLog]) -> dict[str, float]:
    """Sums all nutrients_consumed_json blobs across the supplied logs.
    Missing fields are treated as zero. Returns flat dict keyed by the
    snake_case nutrient name."""
    totals: dict[str, float] = {k: 0.0 for k in REFERENCE_DAILY_VALUES}
    for log in logs:
        blob = log.nutrients_consumed_json
        if not blob:
            continue
        try:
            data = json.loads(blob)
        except json.JSONDecodeError:
            continue
        for key, value in data.items():
            if value is None:
                continue
            if not isinstance(value, (int, float)):
                continue
            if key in totals:
                totals[key] += float(value)
    # Prune zero entries so the response stays tight.
    return {k: round(v, 3) for k, v in totals.items() if v > 0}


def adequacy(totals: dict[str, float]) -> dict[str, NutrientAdequacy]:
    """Compares totals against REFERENCE_DAILY_VALUES."""
    out: dict[str, NutrientAdequacy] = {}
    for key, ref in REFERENCE_DAILY_VALUES.items():
        consumed = totals.get(key, 0.0)
        pct = consumed / ref if ref > 0 else 0.0
        if key in UPPER_LIMIT_NUTRIENTS:
            direction = "high" if pct > 1.0 else ("ok" if pct >= 0.5 else "low")
        else:
            direction = "low" if pct < 0.5 else ("ok" if pct < 1.5 else "high")
        out[key] = NutrientAdequacy(
            consumed=round(consumed, 3),
            reference=ref,
            pct=round(pct, 3),
            direction=direction,
        )
    return out
