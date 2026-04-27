package com.ultraprocessed.sync

import com.ultraprocessed.core.Http
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

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
}
