package com.ultraprocessed.sync

import com.ultraprocessed.data.entities.FodmapLevel
import com.ultraprocessed.data.entities.FoodSource
import com.ultraprocessed.data.entities.SymptomKind
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
    @SerialName("fodmap_level") val fodmapLevel: FodmapLevel = FodmapLevel.UNKNOWN,
    @SerialName("fodmap_tags_json") val fodmapTagsJson: String = "[]",
    @SerialName("fodmap_notes") val fodmapNotes: String? = null,
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

// ---- IBS DTOs ----

@Serializable
data class BowelEventDto(
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("occurred_at") val occurredAt: String,
    val bristol: Int,
    val urgency: Int = 0,
    val completeness: Int = 2,
    val pain: Int = 0,
    val blood: Boolean = false,
    val mucus: Boolean = false,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SymptomEventDto(
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("occurred_at") val occurredAt: String,
    val kind: SymptomKind,
    val severity: Int,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    val location: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class DailyCheckinDto(
    @SerialName("client_uuid") val clientUuid: String,
    val day: String,
    @SerialName("bloating_overall") val bloatingOverall: Int = 0,
    @SerialName("heartburn_overall") val heartburnOverall: Int = 0,
    @SerialName("gut_score") val gutScore: Int? = null,
    val mood: Int? = null,
    val stress: Int? = null,
    val period: Boolean = false,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class TriggerScoreDto(
    @SerialName("food_client_uuid") val foodClientUuid: String,
    @SerialName("food_name") val foodName: String,
    @SerialName("food_brand") val foodBrand: String? = null,
    @SerialName("fodmap_level") val fodmapLevel: FodmapLevel = FodmapLevel.UNKNOWN,
    @SerialName("fodmap_tags") val fodmapTags: List<String> = emptyList(),
    @SerialName("nova_class") val novaClass: Int,
    val score: Double,
    @SerialName("flare_count") val flareCount: Int,
    @SerialName("baseline_count") val baselineCount: Int,
    @SerialName("last_flare_at") val lastFlareAt: String? = null,
    @SerialName("sample_flares") val sampleFlares: List<String> = emptyList(),
)

@Serializable
data class TriggerReportDto(
    @SerialName("from_dt") val fromDt: String,
    @SerialName("to_dt") val toDt: String,
    @SerialName("lookback_hours") val lookbackHours: Double,
    @SerialName("half_life_hours") val halfLifeHours: Double,
    @SerialName("flare_severity_threshold") val flareSeverityThreshold: Int,
    @SerialName("flares_considered") val flaresConsidered: Int,
    @SerialName("foods_scored") val foodsScored: Int,
    val foods: List<TriggerScoreDto>,
)
