package com.ultraprocessed.analyzer

import android.util.Base64
import com.ultraprocessed.core.Http
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
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
 * OpenAI-compatible adapter. Speaks Chat Completions, so it works with:
 *   - OpenAI (https://api.openai.com/v1)
 *   - NVIDIA NIM (https://integrate.api.nvidia.com/v1)
 *   - OpenRouter (https://openrouter.ai/api/v1)
 *   - Together (https://api.together.xyz/v1)
 *   - Groq (https://api.groq.com/openai/v1)
 *   - Ollama (http://localhost:11434/v1)
 *   - LM Studio (http://localhost:1234/v1)
 *
 * Image content uses the OpenAI `image_url` shape with a data URL so we
 * never hit a remote image host.
 *
 * @param baseUrl Provider's base URL, including `/v1` if applicable.
 * @param apiKey  Sent via `Authorization: Bearer`. Blank is allowed for
 *                local providers (Ollama / LM Studio).
 * @param model   Model identifier; varies by provider.
 */
class OpenAICompatibleAnalyzer(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: HttpClient = Http.create(),
    private val maxTokens: Int = 1024
) : FoodAnalyzer {

    override suspend fun analyzeText(text: String): Result<FoodAnalysis> = runHttp {
        body {
            put("messages", buildMessageList {
                addSystem(Prompt.System)
                addUserText(Prompt.forText(text))
            })
        }
    }

    override suspend fun analyzeImage(bytes: ByteArray, mediaType: String): Result<FoodAnalysis> = runHttp {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUrl = "data:$mediaType;base64,$base64"
        body {
            put("messages", buildMessageList {
                addSystem(Prompt.System)
                addUserMixed {
                    addText(Prompt.ForImage)
                    addImageUrl(dataUrl)
                }
            })
        }
    }

    private inline fun body(block: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", 0.1)
            put("response_format", buildJsonObject { put("type", "json_object") })
            block()
        }

    private suspend inline fun runHttp(crossinline body: () -> JsonObject): Result<FoodAnalysis> {
        if (apiKey.isBlank() && !isLocalProvider()) {
            return Result.failure(AnalyzerError.MissingApiKey())
        }
        return try {
            val response: HttpResponse = client.post("${baseUrl.trimEnd('/')}/chat/completions") {
                if (apiKey.isNotBlank()) bearerAuth(apiKey)
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

    private fun isLocalProvider(): Boolean =
        baseUrl.startsWith("http://localhost") || baseUrl.startsWith("http://127.0.0.1")

    private fun parseResponse(raw: String): FoodAnalysis {
        // OpenAI Chat Completions: { "choices": [ { "message": { "role": "assistant", "content": "..." } } ] }
        val outer = try {
            Http.Json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            throw AnalyzerError.MalformedResponse("not JSON: ${raw.take(200)}", e)
        }
        val choices = outer["choices"]?.jsonArray
            ?: throw AnalyzerError.MalformedResponse("missing choices array")
        val first = choices.firstOrNull() as? JsonObject
            ?: throw AnalyzerError.MalformedResponse("empty choices")
        val message = first["message"] as? JsonObject
            ?: throw AnalyzerError.MalformedResponse("missing message in choice")
        val content = message["content"]?.jsonPrimitive?.content
            ?: throw AnalyzerError.MalformedResponse("missing content in message")
        return parseFoodAnalysis(content)
    }
}
