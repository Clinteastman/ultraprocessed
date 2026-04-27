package com.ultraprocessed.analyzer

/**
 * Provider-agnostic food classification interface. Implementations adapt
 * an LLM provider's API surface into [FoodAnalysis].
 *
 * Two adapters cover most providers:
 *   - [AnthropicAnalyzer] for Claude (default).
 *   - [OpenAICompatibleAnalyzer] for any OpenAI-Chat-Completions-shaped API:
 *     OpenAI, NVIDIA NIM, OpenRouter, Together, Groq, Ollama, LM Studio.
 *
 * Both `analyzeText` (ingredient list extracted via OCR) and `analyzeImage`
 * (camera frame for unlabeled foods) return the same [FoodAnalysis] shape;
 * call sites pick whichever matches their input.
 */
interface FoodAnalyzer {

    /**
     * Classify food from textual input - typically OCR'd ingredient text
     * from a food label, but plain food names work too.
     */
    suspend fun analyzeText(text: String): Result<FoodAnalysis>

    /**
     * Classify food from a JPEG/PNG image. Used when there's no useful
     * text on the package or no package at all (a plate, a piece of fruit).
     */
    suspend fun analyzeImage(
        bytes: ByteArray,
        mediaType: String = "image/jpeg"
    ): Result<FoodAnalysis>
}

/**
 * Failures from analyzers come back as [Result.failure] holding one of these.
 */
sealed class AnalyzerError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class MissingApiKey : AnalyzerError("API key not configured. Add one in Settings.")
    class HttpError(val status: Int, body: String) : AnalyzerError("HTTP $status: ${body.take(500)}")
    class MalformedResponse(detail: String, cause: Throwable? = null) :
        AnalyzerError("Provider returned a response we couldn't parse: $detail", cause)
    class Network(cause: Throwable) : AnalyzerError("Network error: ${cause.message}", cause)
}
