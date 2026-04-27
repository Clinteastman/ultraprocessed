package com.ultraprocessed.analyzer

import kotlinx.serialization.Serializable

/**
 * Structured result from a [FoodAnalyzer]. Designed to round-trip through
 * any provider (Claude, OpenAI-compatible, Ollama, etc.) by being plain
 * JSON-shaped data the model is asked to return verbatim.
 *
 * The schema is deliberately tight: small enough to fit in any model's
 * structured-output guarantees, expressive enough to render the result UI.
 */
@Serializable
data class FoodAnalysis(
    /** Specific product or food name. Free-form, e.g. "Tesco Granola" or "apple". */
    val name: String,

    /** Brand if applicable. Null for unbranded items (raw produce, etc.). */
    val brand: String? = null,

    /** NOVA classification group: 1, 2, 3, or 4. */
    val novaClass: Int,

    /** Plain-English rationale; surfaces why a food earned its NOVA grade. */
    val novaRationale: String,

    /** Energy density per 100g if known. */
    val kcalPer100g: Double? = null,

    /** Calories for one typical unit (one apple, one bar). */
    val kcalPerUnit: Double? = null,

    /** Weight in grams of one typical unit/serving (e.g. 180 for a medium apple). */
    @kotlinx.serialization.SerialName("grams_per_unit")
    val gramsPerUnit: Double? = null,

    /** Total package weight in grams (when scanning a multi-serving pack). */
    @kotlinx.serialization.SerialName("package_grams")
    val packageGrams: Double? = null,

    /** Description of "one unit" (e.g. "1 medium apple, ~180g"). */
    val servingDescription: String? = null,

    /** Best-effort ingredients list (LLM-condensed; not the literal label). */
    val ingredients: List<String> = emptyList(),

    /** 0.0..1.0 model confidence in the classification. */
    val confidence: Double = 1.0,

    /**
     * Up to ~3 plausible alternative identifications. The result UI shows
     * these as selectable chips so the user can override when the model's
     * primary guess is wrong (e.g. it called a mango an apple).
     */
    val alternatives: List<FoodAlternative> = emptyList(),

    /**
     * Per-100g nutrient breakdown (macros + micros). Every field inside
     * is optional; the analyzer fills in whatever it knows.
     */
    @kotlinx.serialization.SerialName("nutrients_per_100g")
    val nutrientsPer100g: Nutrients? = null
)

@Serializable
data class FoodAlternative(
    val name: String,
    val novaClass: Int,
    val kcalPer100g: Double? = null,
    val kcalPerUnit: Double? = null
)
