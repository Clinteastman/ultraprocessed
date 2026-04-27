package com.ultraprocessed.sync

import com.ultraprocessed.data.entities.FoodSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for sync. Matches the backend's Pydantic DTOs 1:1
 * (see backend/app/api/v1/foods.py and consumption.py).
 *
 * Datetimes are ISO-8601 with timezone; we use String here so the
 * client doesn't depend on kotlinx-datetime for a one-off serialisation.
 */
@Serializable
data class FoodEntryDto(
    @SerialName("client_uuid") val clientUuid: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    @SerialName("nova_class") val novaClass: Int,
    @SerialName("nova_rationale") val novaRationale: String,
    @SerialName("kcal_per_100g") val kcalPer100g: Double? = null,
    @SerialName("kcal_per_unit") val kcalPerUnit: Double? = null,
    @SerialName("grams_per_unit") val gramsPerUnit: Double? = null,
    @SerialName("package_grams") val packageGrams: Double? = null,
    @SerialName("serving_description") val servingDescription: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("ingredients_json") val ingredientsJson: String = "[]",
    @SerialName("nutrients_json") val nutrientsJson: String? = null,
    val source: FoodSource,
    val confidence: Double = 1.0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ConsumptionLogDto(
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("food_client_uuid") val foodClientUuid: String,
    @SerialName("percentage_eaten") val percentageEaten: Int,
    @SerialName("grams_eaten") val gramsEaten: Double? = null,
    @SerialName("eaten_at") val eatenAt: String,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("location_label") val locationLabel: String? = null,
    @SerialName("kcal_consumed_snapshot") val kcalConsumedSnapshot: Double? = null,
    @SerialName("nutrients_consumed_json") val nutrientsConsumedJson: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class TokenRequest(
    @SerialName("device_name") val deviceName: String
)

@Serializable
data class TokenResponse(
    @SerialName("device_id") val deviceId: Int,
    @SerialName("user_id") val userId: Int,
    val token: String
)
