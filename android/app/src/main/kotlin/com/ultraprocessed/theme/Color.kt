package com.ultraprocessed.theme

import androidx.compose.ui.graphics.Color

/**
 * Custom palette for the Ultraprocessed app.
 *
 * Source of truth: docs/design.md.
 *
 * Dark mode is the primary surface; light mode is offered as a polite
 * alternative. NOVA semantic colors are reserved for the score itself
 * and never used for UI chrome.
 */
object UltraprocessedColors {

    // Dark surface
    val DarkBg = Color(0xFF0B0B0C)
    val DarkSurface1 = Color(0xFF141416)
    val DarkSurface2 = Color(0xFF1C1C1F)
    val DarkSurface3 = Color(0xFF26262A)

    // Dark ink
    val DarkInkHigh = Color(0xFFF5F4EF)
    val DarkInkMid = Color(0xFFB5B3AC)
    val DarkInkLow = Color(0xFF6E6C66)
    val DarkInkInverse = Color(0xFF0B0B0C)

    // Light surface
    val LightBg = Color(0xFFFAF9F4)
    val LightSurface1 = Color(0xFFFFFFFF)
    val LightSurface2 = Color(0xFFF1EFE8)
    val LightSurface3 = Color(0xFFE7E4DA)

    // Light ink
    val LightInkHigh = Color(0xFF14140F)
    val LightInkMid = Color(0xFF555048)
    val LightInkLow = Color(0xFF92897C)
    val LightInkInverse = Color(0xFFFFFFFF)

    // Single brand accent ("fresh leaf")
    val AccentDark = Color(0xFFC7F25C)
    val AccentDarkPressed = Color(0xFFB0D944)
    val AccentLight = Color(0xFF6BAA1E)
    val AccentLightPressed = Color(0xFF558912)

    // NOVA semantic (used only on the score itself)
    val Nova1 = Color(0xFF5BC97D)
    val Nova2 = Color(0xFFC7C354)
    val Nova3 = Color(0xFFE8A04A)
    val Nova4 = Color(0xFFD8543E)

    // Status (mirrors NOVA where it makes sense)
    val Success = Color(0xFF5BC97D)
    val Warning = Color(0xFFE8A04A)
    val Error = Color(0xFFD8543E)
}

/**
 * Returns the NOVA semantic color for a 1..4 class.
 */
fun novaColor(novaClass: Int): Color = when (novaClass) {
    1 -> UltraprocessedColors.Nova1
    2 -> UltraprocessedColors.Nova2
    3 -> UltraprocessedColors.Nova3
    4 -> UltraprocessedColors.Nova4
    else -> UltraprocessedColors.Nova3
}
