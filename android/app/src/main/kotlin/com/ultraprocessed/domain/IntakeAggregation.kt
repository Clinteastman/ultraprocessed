package com.ultraprocessed.domain

import com.ultraprocessed.analyzer.Nutrients
import com.ultraprocessed.core.Http
import com.ultraprocessed.data.dao.ConsumptionWithFood

/**
 * Pure aggregation over a list of consumption entries. Mirrors the
 * backend's /dashboard/range output so the phone Home screen can
 * compute the same numbers offline, without round-tripping.
 */
data class IntakeAggregation(
    val mealCount: Int,
    val totalKcal: Double,
    /** Calories per NOVA class 1..4. Empty buckets stay at 0. */
    val novaCalories: Map<Int, Double>,
    val novaMealCounts: Map<Int, Int>,
    /** Weighted NOVA average (1.0..4.0) by kcal share, or null if 0 meals. */
    val novaAverage: Double?,
    val nutrientsConsumed: Nutrients
)

/**
 * Filter [items] to those with `eatenAt` in `[fromMs, toMs]`, then aggregate.
 */
fun aggregate(items: List<ConsumptionWithFood>, fromMs: Long, toMs: Long): IntakeAggregation {
    val inRange = items.filter { it.log.eatenAt in fromMs..toMs }

    val novaCal = mutableMapOf(1 to 0.0, 2 to 0.0, 3 to 0.0, 4 to 0.0)
    val novaCount = mutableMapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0)
    var totalKcal = 0.0
    var weightedSum = 0.0
    var weightSum = 0.0

    val totalsMap = mutableMapOf<String, Double>()

    for (item in inRange) {
        val nova = item.food.novaClass.coerceIn(1, 4)
        val kcal = item.log.kcalConsumedSnapshot ?: 0.0
        novaCal[nova] = (novaCal[nova] ?: 0.0) + kcal
        novaCount[nova] = (novaCount[nova] ?: 0) + 1
        totalKcal += kcal
        if (kcal > 0) {
            weightedSum += nova * kcal
            weightSum += kcal
        }

        item.log.nutrientsConsumedJson?.let { json ->
            val parsed = runCatching {
                Http.Json.decodeFromString(Nutrients.serializer(), json)
            }.getOrNull() ?: return@let
            mergeNutrients(parsed, totalsMap)
        }
    }

    val avg = if (weightSum > 0) weightedSum / weightSum else null

    return IntakeAggregation(
        mealCount = inRange.size,
        totalKcal = totalKcal,
        novaCalories = novaCal,
        novaMealCounts = novaCount,
        novaAverage = avg,
        nutrientsConsumed = totalsMap.toNutrients()
    )
}

private fun mergeNutrients(n: Nutrients, into: MutableMap<String, Double>) {
    fun add(key: String, value: Double?) {
        if (value != null) into[key] = (into[key] ?: 0.0) + value
    }
    add("protein_g", n.proteinG)
    add("fat_g", n.fatG)
    add("saturated_fat_g", n.saturatedFatG)
    add("carbs_g", n.carbsG)
    add("sugar_g", n.sugarG)
    add("fiber_g", n.fiberG)
    add("salt_g", n.saltG)
    add("sodium_mg", n.sodiumMg)
    add("cholesterol_mg", n.cholesterolMg)
    add("omega3_g", n.omega3G)
    add("calcium_mg", n.calciumMg)
    add("iron_mg", n.ironMg)
    add("potassium_mg", n.potassiumMg)
    add("magnesium_mg", n.magnesiumMg)
    add("zinc_mg", n.zincMg)
    add("phosphorus_mg", n.phosphorusMg)
    add("selenium_ug", n.seleniumUg)
    add("iodine_ug", n.iodineUg)
    add("copper_mg", n.copperMg)
    add("manganese_mg", n.manganeseMg)
    add("vitamin_a_ug", n.vitaminAUg)
    add("vitamin_c_mg", n.vitaminCMg)
    add("vitamin_d_ug", n.vitaminDUg)
    add("vitamin_e_mg", n.vitaminEMg)
    add("vitamin_k_ug", n.vitaminKUg)
    add("vitamin_b1_mg", n.vitaminB1Mg)
    add("vitamin_b2_mg", n.vitaminB2Mg)
    add("vitamin_b3_mg", n.vitaminB3Mg)
    add("vitamin_b6_mg", n.vitaminB6Mg)
    add("vitamin_b12_ug", n.vitaminB12Ug)
    add("folate_ug", n.folateUg)
}

private fun Map<String, Double>.toNutrients(): Nutrients = Nutrients(
    proteinG = this["protein_g"],
    fatG = this["fat_g"],
    saturatedFatG = this["saturated_fat_g"],
    carbsG = this["carbs_g"],
    sugarG = this["sugar_g"],
    fiberG = this["fiber_g"],
    saltG = this["salt_g"],
    sodiumMg = this["sodium_mg"],
    cholesterolMg = this["cholesterol_mg"],
    omega3G = this["omega3_g"],
    calciumMg = this["calcium_mg"],
    ironMg = this["iron_mg"],
    potassiumMg = this["potassium_mg"],
    magnesiumMg = this["magnesium_mg"],
    zincMg = this["zinc_mg"],
    phosphorusMg = this["phosphorus_mg"],
    seleniumUg = this["selenium_ug"],
    iodineUg = this["iodine_ug"],
    copperMg = this["copper_mg"],
    manganeseMg = this["manganese_mg"],
    vitaminAUg = this["vitamin_a_ug"],
    vitaminCMg = this["vitamin_c_mg"],
    vitaminDUg = this["vitamin_d_ug"],
    vitaminEMg = this["vitamin_e_mg"],
    vitaminKUg = this["vitamin_k_ug"],
    vitaminB1Mg = this["vitamin_b1_mg"],
    vitaminB2Mg = this["vitamin_b2_mg"],
    vitaminB3Mg = this["vitamin_b3_mg"],
    vitaminB6Mg = this["vitamin_b6_mg"],
    vitaminB12Ug = this["vitamin_b12_ug"],
    folateUg = this["folate_ug"]
)
