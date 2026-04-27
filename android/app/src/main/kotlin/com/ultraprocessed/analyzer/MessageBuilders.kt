package com.ultraprocessed.analyzer

import com.ultraprocessed.core.Http
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Tiny shared builders for the JSON message lists both Claude and the
 * OpenAI-compatible providers consume. Kept private to the analyzer package.
 */

internal fun buildMessageList(block: MessageListBuilder.() -> Unit): JsonArray =
    MessageListBuilder().apply(block).build()

internal class MessageListBuilder {
    private val messages = mutableListOf<JsonObject>()

    fun addSystem(text: String) {
        messages += buildJsonObject {
            put("role", "system")
            put("content", text)
        }
    }

    fun addUserText(text: String) {
        messages += buildJsonObject {
            put("role", "user")
            put("content", text)
        }
    }

    inline fun addUserMixed(block: MixedContentBuilder.() -> Unit) {
        val content = MixedContentBuilder().apply(block).build()
        messages += buildJsonObject {
            put("role", "user")
            put("content", content)
        }
    }

    fun build(): JsonArray = JsonArray(messages)
}

internal class MixedContentBuilder {
    private val parts = mutableListOf<JsonObject>()

    fun addText(text: String) {
        parts += buildJsonObject {
            put("type", "text")
            put("text", text)
        }
    }

    /** Anthropic-shape image content. */
    fun addImage(base64: String, mediaType: String) {
        parts += buildJsonObject {
            put("type", "image")
            put("source", buildJsonObject {
                put("type", "base64")
                put("media_type", mediaType)
                put("data", base64)
            })
        }
    }

    /** OpenAI-shape image content (used by OpenAICompatibleAnalyzer). */
    fun addImageUrl(dataUrl: String) {
        parts += buildJsonObject {
            put("type", "image_url")
            put("image_url", buildJsonObject {
                put("url", dataUrl)
            })
        }
    }

    fun build(): JsonArray = JsonArray(parts)
}

/**
 * Parses the model-emitted JSON into a [FoodAnalysis]. Tolerates the
 * common LLM mishaps: leading/trailing prose, code-fenced JSON.
 */
internal fun parseFoodAnalysis(raw: String): FoodAnalysis {
    val cleaned = stripCodeFences(raw).trim()
    val firstBrace = cleaned.indexOf('{')
    val lastBrace = cleaned.lastIndexOf('}')
    if (firstBrace == -1 || lastBrace == -1 || lastBrace < firstBrace) {
        throw AnalyzerError.MalformedResponse("no JSON object found in: ${raw.take(200)}")
    }
    val candidate = cleaned.substring(firstBrace, lastBrace + 1)
    return try {
        Http.Json.decodeFromString(FoodAnalysis.serializer(), candidate)
    } catch (e: Exception) {
        throw AnalyzerError.MalformedResponse(
            "couldn't decode FoodAnalysis: ${e.message}; got ${candidate.take(200)}",
            e
        )
    }
}

private fun stripCodeFences(input: String): String {
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(input)
    return fenced?.groupValues?.get(1) ?: input
}
