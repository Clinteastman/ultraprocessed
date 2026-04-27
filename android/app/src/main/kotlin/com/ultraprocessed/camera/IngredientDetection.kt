package com.ultraprocessed.camera

/**
 * Cheap heuristic: does this OCR'd text look like a food ingredient list?
 * If yes, we send the text path to the LLM (cheap and accurate). If no,
 * we send the image path (vision).
 *
 * Two clear signals: an "ingredients:" header in any major language, OR
 * comma-separated tokens with at least one of the common processing
 * markers (E-numbers, "syrup", "starch", "flavour", etc.).
 */
object IngredientDetection {

    private val IngredientHeader = Regex(
        "(?i)\\b(ingredients?|ingr[eé]dients?|inhaltsstoffe|ingredientes|ingredienti)\\b"
    )

    private val ENumber = Regex("(?i)\\bE\\s?[0-9]{3}[a-z]?\\b")

    private val ProcessMarkers = listOf(
        "syrup", "starch", "modified starch", "isolate", "concentrate",
        "emulsifier", "stabiliser", "stabilizer", "flavouring", "flavoring",
        "lecithin", "monoglyceride", "diglyceride", "preservative",
        "colour", "color", "thickener", "high fructose"
    )

    /**
     * Returns true if the text reads like a food ingredient list.
     *
     * `text` is OCR output, which can be noisy: line breaks where there
     * shouldn't be any, mis-recognised characters, etc. Only depend on
     * stable, common patterns.
     */
    fun looksLikeIngredients(text: String): Boolean {
        if (text.isBlank() || text.length < MIN_LENGTH) return false
        val lower = text.lowercase()

        if (IngredientHeader.containsMatchIn(text)) return true
        if (ENumber.containsMatchIn(text)) return true

        val markerCount = ProcessMarkers.count { lower.contains(it) }
        if (markerCount >= 2) return true

        val commas = text.count { it == ',' }
        return commas >= 5 && markerCount >= 1
    }

    private const val MIN_LENGTH = 12
}
