package com.ultraprocessed.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Shared Ktor HttpClient. Strict JSON, lenient on unknown keys (providers
 * add fields all the time), and short timeouts so a stalled provider
 * never holds the camera screen.
 */
object Http {

    val Json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    fun create(): HttpClient = HttpClient(Android) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(Json)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }

        install(Logging) {
            level = LogLevel.NONE
        }
    }
}
