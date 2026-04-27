package com.ultraprocessed.openfoodfacts

import com.ultraprocessed.analyzer.FoodAnalysis
import com.ultraprocessed.analyzer.Nutrients
import com.ultraprocessed.core.Http
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Looks up products by barcode against the Open Food Facts public API.
 * Free, no API key required. Includes NOVA classification, nutrition, and
 * ingredient text for tens of millions of products globally.
 *
 * Open Food Facts asks for a descriptive User-Agent so they can throttle
 * misbehaving clients, so we send one.
 */
class OpenFoodFactsClient(
    private val client: HttpClient = Http.create(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = DEFAULT_USER_AGENT
) {

    /**
     * Returns the analysis if the barcode resolves to a known product, or
     * `null` if Open Food Facts has no record. Failures bubble up as
     * exceptions so the caller can decide whether to fall back to OCR/LLM.
     */
    suspend fun lookup(barcode: String): OpenFoodFactsResult {
        require(barcode.matches(Regex("\\d{6,14}"))) { "barcode must be 6-14 digits" }

        val url = "${baseUrl.trimEnd('/')}/api/v2/product/$barcode.json?fields=$FIELDS"
        val response: HttpResponse = client.get(url) {
            header("User-Agent", userAgent)
        }
        if (!response.status.isSuccess()) {
            return OpenFoodFactsResult.NetworkError(
                "Open Food Facts returned ${response.status.value}"
            )
        }
        val raw = response.bodyAsText()
        val outer = Http.Json.parseToJsonElement(raw).jsonObject

        val status = outer["status"]?.jsonPrimitive?.intOrNull ?: 0
        if (status != 1) return OpenFoodFactsResult.NotFound

        val product = outer["product"]?.jsonObject
            ?: return OpenFoodFactsResult.NotFound

        val novaPresent = product["nova_group"]?.numericOrNull() != null
        val analysis = product.toFoodAnalysis(novaPresent)
        val nameKnown = analysis.name.isNotBlank() && analysis.name != "Unknown product"

        return OpenFoodFactsResult.Found(
            barcode = barcode,
            analysis = analysis,
            imageUrl = product.string("image_url") ?: product.string("image_front_url"),
            hasReliableNova = novaPresent,
            nameKnown = nameKnown
        )
    }

    private fun kotlinx.serialization.json.JsonObject.toFoodAnalysis(novaPresent: Boolean): FoodAnalysis {
        val name = string("product_name")?.takeIf { it.isNotBlank() }
            ?: string("generic_name")
            ?: "Unknown product"
        val brand = string("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val novaClass = if (novaPresent) {
            (this["nova_group"]!!.numericOrNull() ?: 3.0).toInt().coerceIn(1, 4)
        } else {
            // Don't pretend; the caller will route to LLM classification.
            // We pick 0 as a sentinel and fix up downstream.
            0
        }
        val novaRationale = if (novaPresent) {
            string("nova_groups_tags")
                ?.let { "Open Food Facts NOVA group $novaClass ($it)" }
                ?: "Open Food Facts NOVA group $novaClass"
        } else {
            "Open Food Facts has no NOVA classification for this product."
        }
        val nutriments = this["nutriments"]?.jsonObject
        val kcalPer100g = nutriments?.numeric("energy-kcal_100g")
            ?: nutriments?.numeric("energy-kcal")
        val servingSize = string("serving_size")
        val ingredients = string("ingredients_text_en")
            ?: string("ingredients_text")
            ?: ""

        return FoodAnalysis(
            name = name,
            brand = brand,
            novaClass = novaClass.coerceAtLeast(1),
            novaRationale = novaRationale,
            kcalPer100g = kcalPer100g,
            kcalPerUnit = null,
            servingDescription = servingSize,
            ingredients = ingredients
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(20),
            confidence = if (novaPresent) 0.95 else 0.4,
            nutrientsPer100g = nutriments?.toNutrients()
        )
    }

    /**
     * Maps Open Food Facts' `nutriments` block onto our [Nutrients] model.
     * OFF stores most micronutrients in grams per 100g; we convert to
     * mg / ug to match nutrition labels.
     */
    private fun kotlinx.serialization.json.JsonObject.toNutrients(): Nutrients? {
        // Macros (already in g or mg)
        val protein = numeric("proteins_100g")
        val fat = numeric("fat_100g")
        val saturatedFat = numeric("saturated-fat_100g")
        val carbs = numeric("carbohydrates_100g")
        val sugar = numeric("sugars_100g")
        val fiber = numeric("fiber_100g")
        val salt = numeric("salt_100g")
        val sodium = numeric("sodium_100g")?.times(1000)  // OFF sodium is g/100g
        val cholesterol = numeric("cholesterol_100g")?.times(1000)
        val omega3 = numeric("omega-3-fat_100g")

        // Minerals (OFF stores in g/100g; convert to mg or ug)
        val calcium = numeric("calcium_100g")?.times(1000)
        val iron = numeric("iron_100g")?.times(1000)
        val potassium = numeric("potassium_100g")?.times(1000)
        val magnesium = numeric("magnesium_100g")?.times(1000)
        val zinc = numeric("zinc_100g")?.times(1000)
        val phosphorus = numeric("phosphorus_100g")?.times(1000)
        val selenium = numeric("selenium_100g")?.times(1_000_000)  // g -> ug
        val iodine = numeric("iodine_100g")?.times(1_000_000)
        val copper = numeric("copper_100g")?.times(1000)
        val manganese = numeric("manganese_100g")?.times(1000)

        // Vitamins
        val vitaminA = numeric("vitamin-a_100g")?.times(1_000_000)
        val vitaminC = numeric("vitamin-c_100g")?.times(1000)
        val vitaminD = numeric("vitamin-d_100g")?.times(1_000_000)
        val vitaminE = numeric("vitamin-e_100g")?.times(1000)
        val vitaminK = numeric("vitamin-k_100g")?.times(1_000_000)
        val vitaminB1 = numeric("vitamin-b1_100g")?.times(1000)
        val vitaminB2 = numeric("vitamin-b2_100g")?.times(1000)
        val vitaminB3 = numeric("vitamin-pp_100g")?.times(1000)
        val vitaminB6 = numeric("vitamin-b6_100g")?.times(1000)
        val vitaminB12 = numeric("vitamin-b12_100g")?.times(1_000_000)
        val folate = numeric("vitamin-b9_100g")?.times(1_000_000)

        val n = Nutrients(
            proteinG = protein, fatG = fat, saturatedFatG = saturatedFat,
            carbsG = carbs, sugarG = sugar, fiberG = fiber, saltG = salt,
            sodiumMg = sodium, cholesterolMg = cholesterol, omega3G = omega3,
            calciumMg = calcium, ironMg = iron, potassiumMg = potassium,
            magnesiumMg = magnesium, zincMg = zinc, phosphorusMg = phosphorus,
            seleniumUg = selenium, iodineUg = iodine, copperMg = copper, manganeseMg = manganese,
            vitaminAUg = vitaminA, vitaminCMg = vitaminC, vitaminDUg = vitaminD,
            vitaminEMg = vitaminE, vitaminKUg = vitaminK,
            vitaminB1Mg = vitaminB1, vitaminB2Mg = vitaminB2, vitaminB3Mg = vitaminB3,
            vitaminB6Mg = vitaminB6, vitaminB12Ug = vitaminB12, folateUg = folate
        )
        return if (n.isEmpty()) null else n
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun kotlinx.serialization.json.JsonObject.numeric(key: String): Double? =
        (this[key] as? JsonPrimitive)?.numericOrNull()

    private fun JsonElement.numericOrNull(): Double? = when (this) {
        is JsonPrimitive -> doubleOrNull ?: contentOrNull?.toDoubleOrNull()
        JsonNull -> null
        else -> null
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://world.openfoodfacts.org"
        const val DEFAULT_USER_AGENT = "Ultraprocessed/0.1 (https://github.com/Clinteastman/ultraprocessed)"
        const val FIELDS = "product_name,generic_name,brands,nova_group,nova_groups_tags," +
            "nutriments,serving_size,ingredients_text,ingredients_text_en,image_url,image_front_url,nutriscore_grade"
    }
}

/**
 * Outcome of a barcode lookup. The caller chooses whether to fall back to
 * OCR/LLM analysis if [NotFound] or [NetworkError] is returned.
 */
sealed class OpenFoodFactsResult {
    data class Found(
        val barcode: String,
        val analysis: FoodAnalysis,
        val imageUrl: String?,
        /** True when OFF had a real NOVA group; false when we're guessing. */
        val hasReliableNova: Boolean,
        /** True when product_name or generic_name resolved to something usable. */
        val nameKnown: Boolean
    ) : OpenFoodFactsResult()

    data object NotFound : OpenFoodFactsResult()

    data class NetworkError(val message: String) : OpenFoodFactsResult()
}

/**
 * Cached lookup table seed, used by the (later) backend cache and tests.
 */
@Serializable
internal data class OffSnapshot(
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    @SerialName("nova_group") val novaGroup: Int? = null,
    @SerialName("ingredients_text") val ingredientsText: String? = null
)
