package com.ultraprocessed.sync

import com.ultraprocessed.core.Http
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Thin Ktor wrapper over the Ultraprocessed backend's REST surface.
 * One instance per (baseUrl + token) pair; instantiate lazily because
 * the user can change either at any time via Settings.
 */
class BackendClient(
    private val baseUrl: String,
    private val token: String,
    private val client: HttpClient = Http.create()
) {
    suspend fun health(): Boolean {
        if (baseUrl.isBlank()) return false
        return runCatching {
            val resp = client.get("${baseUrl.trimEnd('/')}/api/v1/health")
            resp.status.isSuccess()
        }.getOrDefault(false)
    }

    suspend fun whoami(): Result<String> = runCatching {
        val resp = client.get("${baseUrl.trimEnd('/')}/api/v1/auth/whoami") {
            bearerAuth(token)
        }
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}: ${resp.bodyAsText().take(200)}")
        resp.bodyAsText()
    }

    suspend fun pushFoods(foods: List<FoodEntryDto>): Result<Unit> = runCatching {
        if (foods.isEmpty()) return@runCatching
        val resp = client.post("${baseUrl.trimEnd('/')}/api/v1/foods") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(foods)
        }
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}: ${resp.bodyAsText().take(200)}")
    }

    suspend fun pushConsumption(logs: List<ConsumptionLogDto>): Result<Unit> = runCatching {
        if (logs.isEmpty()) return@runCatching
        val resp = client.post("${baseUrl.trimEnd('/')}/api/v1/consumption") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(logs)
        }
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}: ${resp.bodyAsText().take(200)}")
    }

    /**
     * Issues a fresh device token. The phone calls this once during the
     * pairing flow in Settings; the returned token replaces whatever was
     * stored before.
     */
    suspend fun pair(deviceName: String): Result<TokenResponse> = runCatching {
        val resp = client.post("${baseUrl.trimEnd('/')}/api/v1/auth/token") {
            contentType(ContentType.Application.Json)
            setBody(TokenRequest(deviceName))
        }
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}: ${resp.bodyAsText().take(200)}")
        resp.body<TokenResponse>()
    }

    /** Push a fasting profile to the backend. Backend stores one active profile per user. */
    suspend fun putFastingProfile(profile: FastingProfileDto): Result<Unit> = runCatching {
        val resp = client.put("${baseUrl.trimEnd('/')}/api/v1/fasting/profile") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(profile)
        }
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}: ${resp.bodyAsText().take(200)}")
    }

    /** Upload a JPEG image for a food, identified by its client_uuid. */
    suspend fun uploadFoodImage(clientUuid: String, jpegBytes: ByteArray): Result<Unit> = runCatching {
        val resp = client.post("${baseUrl.trimEnd('/')}/api/v1/foods/$clientUuid/image") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(formData {
                append("file", jpegBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"$clientUuid.jpg\"")
                })
            }))
        }
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}: ${resp.bodyAsText().take(200)}")
    }
}

@Serializable
data class FastingProfileDto(
    val name: String,
    @SerialName("schedule_type") val scheduleType: String,
    @SerialName("eating_window_start_minutes") val eatingWindowStartMinutes: Int,
    @SerialName("eating_window_end_minutes") val eatingWindowEndMinutes: Int,
    @SerialName("restricted_days_mask") val restrictedDaysMask: Int = 0,
    @SerialName("restricted_kcal_target") val restrictedKcalTarget: Int? = null,
    val active: Boolean
)
