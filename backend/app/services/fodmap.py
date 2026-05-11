"""FODMAP classification helpers.

FODMAP = Fermentable Oligo-, Di-, Mono-saccharides And Polyols. These are
short-chain carbohydrates that are poorly absorbed in the small intestine
and are the most consistent food triggers for IBS symptoms.

The categories we care about, with their classic culprits:

    fructans  - wheat, rye, onion, garlic, leek
    gos       - legumes (chickpeas, kidney beans, lentils), pistachios, cashews
    lactose   - milk, soft cheese, yogurt (matured cheese is largely OK)
    fructose  - apple, pear, mango, honey, agave, high-fructose corn syrup
    polyols   - stone fruit, mushrooms, cauliflower, sugar-free gum (sorbitol/mannitol/xylitol)

This module is intentionally a hand-curated keyword map rather than a
full ingredient database; it gives the analyzer a sensible default tag at
scan time, which the user can correct from the food page. The LLM
analyzer prompt should also be updated to emit these tags directly when
it has confidence.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass

from app.models import FodmapLevel


# ---- Keyword -> category map ----
#
# Order matters slightly: longer / more specific keys win. We word-match
# (whole word, case-insensitive) so "garlic powder" hits "garlic" but
# "Bulgarian feta" doesn't accidentally hit "gar". Multi-word keys are
# matched as substrings since they're already specific.

FODMAP_KEYWORDS: dict[str, tuple[str, FodmapLevel]] = {
    # Fructans
    "wheat": ("fructans", FodmapLevel.HIGH),
    "rye": ("fructans", FodmapLevel.HIGH),
    "barley": ("fructans", FodmapLevel.HIGH),
    "onion": ("fructans", FodmapLevel.HIGH),
    "shallot": ("fructans", FodmapLevel.HIGH),
    "leek": ("fructans", FodmapLevel.HIGH),
    "garlic": ("fructans", FodmapLevel.HIGH),
    "spring onion": ("fructans", FodmapLevel.MODERATE),  # green tops are low
    "scallion": ("fructans", FodmapLevel.MODERATE),
    "chicory": ("fructans", FodmapLevel.HIGH),
    "inulin": ("fructans", FodmapLevel.HIGH),
    "couscous": ("fructans", FodmapLevel.HIGH),
    "pasta": ("fructans", FodmapLevel.HIGH),
    "bread": ("fructans", FodmapLevel.HIGH),
    "bagel": ("fructans", FodmapLevel.HIGH),
    "tortilla": ("fructans", FodmapLevel.MODERATE),
    "cracker": ("fructans", FodmapLevel.MODERATE),
    "biscuit": ("fructans", FodmapLevel.MODERATE),
    "pretzel": ("fructans", FodmapLevel.MODERATE),
    "cereal": ("fructans", FodmapLevel.MODERATE),
    # GOS (galacto-oligosaccharides) — legumes
    "chickpea": ("gos", FodmapLevel.HIGH),
    "garbanzo": ("gos", FodmapLevel.HIGH),
    "kidney bean": ("gos", FodmapLevel.HIGH),
    "black bean": ("gos", FodmapLevel.HIGH),
    "baked bean": ("gos", FodmapLevel.HIGH),
    "soya bean": ("gos", FodmapLevel.HIGH),
    "soybean": ("gos", FodmapLevel.HIGH),
    "lentil": ("gos", FodmapLevel.MODERATE),
    "split pea": ("gos", FodmapLevel.HIGH),
    "pistachio": ("gos", FodmapLevel.HIGH),
    "cashew": ("gos", FodmapLevel.HIGH),
    "hummus": ("gos", FodmapLevel.HIGH),
    # Lactose
    "milk": ("lactose", FodmapLevel.HIGH),
    "cream": ("lactose", FodmapLevel.HIGH),
    "yogurt": ("lactose", FodmapLevel.HIGH),
    "yoghurt": ("lactose", FodmapLevel.HIGH),
    "ice cream": ("lactose", FodmapLevel.HIGH),
    "ricotta": ("lactose", FodmapLevel.HIGH),
    "cottage cheese": ("lactose", FodmapLevel.HIGH),
    "mascarpone": ("lactose", FodmapLevel.HIGH),
    "condensed milk": ("lactose", FodmapLevel.HIGH),
    "evaporated milk": ("lactose", FodmapLevel.HIGH),
    "buttermilk": ("lactose", FodmapLevel.HIGH),
    "custard": ("lactose", FodmapLevel.HIGH),
    # Fructose (excess)
    "apple": ("fructose", FodmapLevel.HIGH),
    "pear": ("fructose", FodmapLevel.HIGH),
    "mango": ("fructose", FodmapLevel.HIGH),
    "watermelon": ("fructose", FodmapLevel.HIGH),
    "honey": ("fructose", FodmapLevel.HIGH),
    "agave": ("fructose", FodmapLevel.HIGH),
    "high-fructose corn syrup": ("fructose", FodmapLevel.HIGH),
    "high fructose corn syrup": ("fructose", FodmapLevel.HIGH),
    "asparagus": ("fructose", FodmapLevel.HIGH),
    "sugar snap": ("fructose", FodmapLevel.MODERATE),
    # Polyols
    "cauliflower": ("polyols", FodmapLevel.HIGH),
    "mushroom": ("polyols", FodmapLevel.HIGH),
    "blackberry": ("polyols", FodmapLevel.HIGH),
    "apricot": ("polyols", FodmapLevel.HIGH),
    "cherry": ("polyols", FodmapLevel.HIGH),
    "nectarine": ("polyols", FodmapLevel.HIGH),
    "peach": ("polyols", FodmapLevel.HIGH),
    "plum": ("polyols", FodmapLevel.HIGH),
    "prune": ("polyols", FodmapLevel.HIGH),
    "avocado": ("polyols", FodmapLevel.MODERATE),
    "sorbitol": ("polyols", FodmapLevel.HIGH),
    "mannitol": ("polyols", FodmapLevel.HIGH),
    "xylitol": ("polyols", FodmapLevel.HIGH),
    "maltitol": ("polyols", FodmapLevel.HIGH),
    "isomalt": ("polyols", FodmapLevel.HIGH),
    "sugar-free": ("polyols", FodmapLevel.MODERATE),
    "sugar free": ("polyols", FodmapLevel.MODERATE),
    "diet": ("polyols", FodmapLevel.MODERATE),  # diet drinks often use polyols
}

# Foods that are confidently low FODMAP — used to overrule incidental
# hits ("oat milk" shouldn't be tagged lactose just because "milk" is in
# the name). When any of these match, level downgrades to LOW unless a
# stronger high-FODMAP match wins.
LOW_FODMAP_KEYWORDS: frozenset[str] = frozenset({
    "rice", "rice noodle", "potato", "carrot", "courgette", "zucchini",
    "cucumber", "spinach", "kale", "lettuce", "tomato", "ginger", "egg",
    "chicken breast", "beef", "salmon", "cod", "tuna", "tofu", "tempeh",
    "lactose-free", "lactose free", "almond milk", "rice milk", "oat milk",
    "coconut milk", "sourdough", "gluten-free bread", "gluten free bread",
    "quinoa", "buckwheat", "polenta", "cheddar", "parmesan", "feta",
    "brie", "camembert", "swiss cheese", "halloumi",
})


_LEVEL_RANK = {FodmapLevel.UNKNOWN: 0, FodmapLevel.LOW: 1, FodmapLevel.MODERATE: 2, FodmapLevel.HIGH: 3}


@dataclass(slots=True)
class FodmapClassification:
    level: FodmapLevel
    tags: list[str]  # category keys: fructans, gos, lactose, fructose, polyols
    matched_keywords: list[str]


def classify(name: str, ingredients: list[str] | None = None) -> FodmapClassification:
    """Best-effort FODMAP classification from a food name plus ingredient list.

    Strategy:
      1. Lowercase + tokenize the name and each ingredient.
      2. Walk every keyword; record matches with their (category, level).
      3. Rolled up level = max across matches (HIGH > MODERATE > LOW > UNKNOWN).
      4. If ONLY low-FODMAP keywords match, level = LOW.
      5. Otherwise UNKNOWN.

    Returns the headline level, deduped category tags, and the keywords
    that triggered the match (useful for showing the user *why* their
    food got flagged).
    """
    haystacks = [name.lower()]
    if ingredients:
        haystacks.extend(ing.lower() for ing in ingredients if ing)

    matched_categories: dict[str, FodmapLevel] = {}
    matched_keywords: list[str] = []
    saw_low = False

    for hay in haystacks:
        for kw in LOW_FODMAP_KEYWORDS:
            if _hit(hay, kw):
                saw_low = True
        for kw, (category, level) in FODMAP_KEYWORDS.items():
            if _hit(hay, kw):
                matched_keywords.append(kw)
                prev = matched_categories.get(category)
                if prev is None or _LEVEL_RANK[level] > _LEVEL_RANK[prev]:
                    matched_categories[category] = level

    if matched_categories:
        rolled = max(matched_categories.values(), key=lambda lvl: _LEVEL_RANK[lvl])
        return FodmapClassification(
            level=rolled,
            tags=sorted(matched_categories.keys()),
            matched_keywords=sorted(set(matched_keywords)),
        )
    if saw_low:
        return FodmapClassification(level=FodmapLevel.LOW, tags=[], matched_keywords=[])
    return FodmapClassification(level=FodmapLevel.UNKNOWN, tags=[], matched_keywords=[])


def _hit(haystack: str, keyword: str) -> bool:
    """Whole-word match for short keys, substring for multi-word keys."""
    if " " in keyword or "-" in keyword:
        return keyword in haystack
    return re.search(rf"\b{re.escape(keyword)}\b", haystack) is not None


def parse_tags(json_str: str | None) -> list[str]:
    """Safely parse a fodmap_tags_json blob into a list of category strings."""
    if not json_str:
        return []
    try:
        data = json.loads(json_str)
    except json.JSONDecodeError:
        return []
    if not isinstance(data, list):
        return []
    return [str(t) for t in data if t]


def severity_score(level: FodmapLevel) -> float:
    """Numeric severity for trigger correlation. UNKNOWN counts as a small
    nonzero so foods with no FODMAP info aren't completely invisible to
    the engine — but they get less weight than tagged HIGH items."""
    return {
        FodmapLevel.UNKNOWN: 0.25,
        FodmapLevel.LOW: 0.1,
        FodmapLevel.MODERATE: 0.6,
        FodmapLevel.HIGH: 1.0,
    }[level]
