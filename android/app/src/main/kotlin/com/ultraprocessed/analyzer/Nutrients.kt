package com.ultraprocessed.analyzer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-100g nutrient breakdown. Every field is optional; the analyzer
 * fills in what it knows and leaves the rest null. Macros are in grams
 * (or as labelled in the field name); minerals and vitamins use the
 * unit baked into the field name (mg / ug = micrograms).
 *
 * The naming intentionally matches what nutrition labels and Open Food
 * Facts use, so mapping is mostly direct.
 */
@Serializable
data class Nutrients(
    // ---- Macronutrients (per 100g) ----
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("saturated_fat_g") val saturatedFatG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("sugar_g") val sugarG: Double? = null,
    @SerialName("fiber_g") val fiberG: Double? = null,
    @SerialName("salt_g") val saltG: Double? = null,
    @SerialName("sodium_mg") val sodiumMg: Double? = null,
    @SerialName("cholesterol_mg") val cholesterolMg: Double? = null,
    @SerialName("omega3_g") val omega3G: Double? = null,

    // ---- Minerals ----
    @SerialName("calcium_mg") val calciumMg: Double? = null,
    @SerialName("iron_mg") val ironMg: Double? = null,
    @SerialName("potassium_mg") val potassiumMg: Double? = null,
    @SerialName("magnesium_mg") val magnesiumMg: Double? = null,
    @SerialName("zinc_mg") val zincMg: Double? = null,
    @SerialName("phosphorus_mg") val phosphorusMg: Double? = null,
    @SerialName("selenium_ug") val seleniumUg: Double? = null,
    @SerialName("iodine_ug") val iodineUg: Double? = null,
    @SerialName("copper_mg") val copperMg: Double? = null,
    @SerialName("manganese_mg") val manganeseMg: Double? = null,

    // ---- Vitamins ----
    @SerialName("vitamin_a_ug") val vitaminAUg: Double? = null,
    @SerialName("vitamin_c_mg") val vitaminCMg: Double? = null,
    @SerialName("vitamin_d_ug") val vitaminDUg: Double? = null,
    @SerialName("vitamin_e_mg") val vitaminEMg: Double? = null,
    @SerialName("vitamin_k_ug") val vitaminKUg: Double? = null,
    @SerialName("vitamin_b1_mg") val vitaminB1Mg: Double? = null,
    @SerialName("vitamin_b2_mg") val vitaminB2Mg: Double? = null,
    @SerialName("vitamin_b3_mg") val vitaminB3Mg: Double? = null,
    @SerialName("vitamin_b6_mg") val vitaminB6Mg: Double? = null,
    @SerialName("vitamin_b12_ug") val vitaminB12Ug: Double? = null,
    @SerialName("folate_ug") val folateUg: Double? = null
) {
    /**
     * Returns a copy where every nutrient field is scaled by `factor`.
     * Used at log time to snapshot what was actually consumed.
     */
    fun scaledBy(factor: Double): Nutrients = Nutrients(
        proteinG = proteinG?.times(factor),
        fatG = fatG?.times(factor),
        saturatedFatG = saturatedFatG?.times(factor),
        carbsG = carbsG?.times(factor),
        sugarG = sugarG?.times(factor),
        fiberG = fiberG?.times(factor),
        saltG = saltG?.times(factor),
        sodiumMg = sodiumMg?.times(factor),
        cholesterolMg = cholesterolMg?.times(factor),
        omega3G = omega3G?.times(factor),
        calciumMg = calciumMg?.times(factor),
        ironMg = ironMg?.times(factor),
        potassiumMg = potassiumMg?.times(factor),
        magnesiumMg = magnesiumMg?.times(factor),
        zincMg = zincMg?.times(factor),
        phosphorusMg = phosphorusMg?.times(factor),
        seleniumUg = seleniumUg?.times(factor),
        iodineUg = iodineUg?.times(factor),
        copperMg = copperMg?.times(factor),
        manganeseMg = manganeseMg?.times(factor),
        vitaminAUg = vitaminAUg?.times(factor),
        vitaminCMg = vitaminCMg?.times(factor),
        vitaminDUg = vitaminDUg?.times(factor),
        vitaminEMg = vitaminEMg?.times(factor),
        vitaminKUg = vitaminKUg?.times(factor),
        vitaminB1Mg = vitaminB1Mg?.times(factor),
        vitaminB2Mg = vitaminB2Mg?.times(factor),
        vitaminB3Mg = vitaminB3Mg?.times(factor),
        vitaminB6Mg = vitaminB6Mg?.times(factor),
        vitaminB12Ug = vitaminB12Ug?.times(factor),
        folateUg = folateUg?.times(factor)
    )

    fun isEmpty(): Boolean = listOfNotNull(
        proteinG, fatG, saturatedFatG, carbsG, sugarG, fiberG, saltG, sodiumMg,
        cholesterolMg, omega3G, calciumMg, ironMg, potassiumMg, magnesiumMg,
        zincMg, phosphorusMg, seleniumUg, iodineUg, copperMg, manganeseMg,
        vitaminAUg, vitaminCMg, vitaminDUg, vitaminEMg, vitaminKUg,
        vitaminB1Mg, vitaminB2Mg, vitaminB3Mg, vitaminB6Mg, vitaminB12Ug, folateUg
    ).isEmpty()
}
