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
          1 - Unprocessed or minimally processed: whole foods exactly as they grow or
              with only physical cleaning/cutting. Fresh fruit, fresh vegetables, raw
              meat, raw fish, plain milk, plain yoghurt, plain grains, eggs, nuts.
          2 - Processed culinary ingredients: oils, butter, salt, sugar, vinegar - the
              things you cook WITH, not eat alone.
          3 - Processed: a few-ingredient combinations of NOVA 1 + NOVA 2 (canned
              vegetables in brine, simple cheeses, cured meats, fresh bread).
          4 - Ultra-processed: industrial formulations with cosmetic additives
              (emulsifiers, flavour enhancers, modified starches, hydrogenated oils,
              protein isolates, artificial sweeteners, colours).

        Rules:
          - Output JSON only. No prose, no Markdown, no code fences.
          - WHOLE FOODS ARE NOVA 1. A fresh mango from Brazil is NOVA 1. An apple is
            NOVA 1. A piece of raw chicken is NOVA 1. Plain rice is NOVA 1. Country
            stickers, supermarket logos, or PLU labels do NOT make a whole food more
            processed. Use confidence 0.9+ for any clearly recognizable whole food.
          - Always set `name` to the food itself when you can tell what it is, even
            without brand or origin. "Mango", "Apple", "Banana" - not "Unknown food".
          - For packaged items, use the ingredient list to decide. Multiple cosmetic
            additives (E-numbers, flavour enhancers, emulsifiers, sweeteners) -> NOVA 4.
            One or two simple ingredients (e.g. "milk, salt, rennet") -> NOVA 3 or lower.
          - Only return name="Unknown food", novaClass=3, confidence=0.2 when you
            genuinely cannot identify what's in the image at all (blurry, unclear,
            no recognizable shape).
          - For unbranded items, leave brand null. Provide a typical kcalPer100g and
            kcalPerUnit when you can (e.g., a medium apple ≈ 95 kcal).
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
