package com.ultraprocessed.openfoodfacts

import com.ultraprocessed.analyzer.FoodAnalysis
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

        return OpenFoodFactsResult.Found(
            barcode = barcode,
            analysis = product.toFoodAnalysis(),
            imageUrl = product.string("image_url")
                ?: product.string("image_front_url")
        )
    }

    private fun kotlinx.serialization.json.JsonObject.toFoodAnalysis(): FoodAnalysis {
        val name = string("product_name")?.takeIf { it.isNotBlank() }
            ?: string("generic_name")
            ?: "Unknown product"
        val brand = string("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val novaClass = (this["nova_group"]?.let { it.numericOrNull() }
            ?: this["nutriscore_grade"]?.let { null }
            ?: 3.0).toInt().coerceIn(1, 4)
        val novaRationale = string("nova_groups_tags")
            ?.let { "Open Food Facts NOVA group $novaClass ($it)" }
            ?: "Open Food Facts NOVA group $novaClass"
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
            novaClass = novaClass,
            novaRationale = novaRationale,
            kcalPer100g = kcalPer100g,
            kcalPerUnit = null,
            servingDescription = servingSize,
            ingredients = ingredients
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(20),
            confidence = if (this.containsKey("nova_group")) 0.95 else 0.6
        )
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
        val imageUrl: String?
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
