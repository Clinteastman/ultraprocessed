package com.ultraprocessed.analyzer

import android.util.Base64
import com.ultraprocessed.core.Http
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Anthropic Claude adapter. Uses the Messages API with a strict system
 * prompt (see [Prompt.System]) so the model returns JSON-only output.
 *
 * Vision works via image content blocks with base64-encoded source data.
 *
 * @param baseUrl Defaults to api.anthropic.com.
 * @param apiKey  Sent as `x-api-key`.
 * @param model   e.g. `claude-haiku-4-5-20251001`.
 */
class AnthropicAnalyzer(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: HttpClient = Http.create(),
    private val maxTokens: Int = 1024
) : FoodAnalyzer {

    override suspend fun analyzeText(text: String): Result<FoodAnalysis> = runHttp {
        body {
            put("system", Prompt.System)
            put("messages", buildMessageList {
                addUserText(Prompt.forText(text))
            })
        }
    }

    override suspend fun analyzeImage(bytes: ByteArray, mediaType: String): Result<FoodAnalysis> = runHttp {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        body {
            put("system", Prompt.System)
            put("messages", buildMessageList {
                addUserMixed {
                    addText(Prompt.ForImage)
                    addImage(base64, mediaType)
                }
            })
        }
    }

    private inline fun body(block: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            block()
        }

    private suspend inline fun runHttp(crossinline body: () -> JsonObject): Result<FoodAnalysis> {
        if (apiKey.isBlank()) return Result.failure(AnalyzerError.MissingApiKey())
        return try {
            val response: HttpResponse = client.post("${baseUrl.trimEnd('/')}/v1/messages") {
                headers {
                    append("x-api-key", apiKey)
                    append("anthropic-version", ANTHROPIC_VERSION)
                }
                contentType(ContentType.Application.Json)
                setBody(body())
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                Result.failure(AnalyzerError.HttpError(response.status.value, text))
            } else {
                Result.success(parseResponse(text))
            }
        } catch (e: AnalyzerError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AnalyzerError.Network(e))
        }
    }

    private fun parseResponse(raw: String): FoodAnalysis {
        // Anthropic Messages API returns: { "content": [ { "type": "text", "text": "..." }, ... ] }
        val outer = try {
            Http.Json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            throw AnalyzerError.MalformedResponse("not JSON: ${raw.take(200)}", e)
        }
        val content = outer["content"]?.jsonArray
            ?: throw AnalyzerError.MalformedResponse("missing content array")
        val text = content
            .firstOrNull { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "text" }
            ?.let { (it as JsonObject)["text"]?.jsonPrimitive?.content }
            ?: throw AnalyzerError.MalformedResponse("no text content in response")
        return parseFoodAnalysis(text)
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
