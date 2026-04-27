package com.ultraprocessed.analyzer

/**
 * Shared prompt strings used by both analyzer adapters. The system prompt
 * locks the model to a strict JSON shape and grounds it in the NOVA
 * classification system so different providers return comparable scores.
 */
internal object Prompt {

    /**
     * Concise NOVA primer + JSON schema enforcement. Designed to fit in a
     * single system message; both Claude and OpenAI-compatible providers
     * accept this verbatim.
     */
    val System: String = """
        You are an expert food classifier using the NOVA framework. Given a food
        item, return a JSON object that matches this exact schema and nothing else:

        {
          "name": string,
          "brand": string or null,
          "novaClass": integer (1, 2, 3, or 4),
          "novaRationale": string (one or two short sentences explaining the class),
          "kcalPer100g": number or null,
          "kcalPerUnit": number or null,
          "servingDescription": string or null,
          "ingredients": array of strings (most processed/notable items first; can be empty),
          "confidence": number between 0 and 1
        }

        NOVA reference:
          1 - Unprocessed or minimally processed (whole foods, plain milk, simple grains).
          2 - Processed culinary ingredients (oils, butter, salt, sugar used in cooking).
          3 - Processed (canned vegetables in brine, simple cheeses, cured meats with few additives).
          4 - Ultra-processed (formulations with cosmetic additives like emulsifiers,
              flavour enhancers, modified starches, hydrogenated oils, protein isolates).

        Rules:
          - Output JSON only. No prose, no Markdown, no code fences.
          - If you cannot identify the food at all, set name to "Unknown food",
            novaClass to 3, novaRationale to a brief explanation, and confidence to 0.2.
          - If only a category is identifiable (e.g. "fresh apple"), it's fine to
            leave brand null and provide a typical kcalPerUnit estimate.
          - If specific ingredients (e.g. emulsifier E471, soy lecithin, modified starch)
            justify a NOVA 4 verdict, list them first in ingredients.
          - For unbranded raw foods (apple, banana, plain rice), confidence may be 0.9+;
            for ambiguous mixed dishes from a photo, keep confidence below 0.6.
    """.trimIndent()

    /**
     * Wraps OCR'd label text in a clear instruction header.
     */
    fun forText(text: String): String {
        val truncated = text.take(MAX_LABEL_CHARS)
        return "Classify the following food based on its ingredient label or description:\n\n$truncated"
    }

    /**
     * Wraps an image-only analysis in an instruction header so the model
     * understands no label text is included.
     */
    val ForImage: String = "Classify the food shown in this image. " +
        "If a label is visible, read it; if not, classify what you see (e.g. a plate, fruit, packaged item)."

    private const val MAX_LABEL_CHARS = 4_000
}
