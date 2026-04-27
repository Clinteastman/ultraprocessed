@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.ultraprocessed.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.ultraprocessed.R

/**
 * Typography. Two families: Geist for UI, Instrument Serif for editorial moments.
 *
 * Geist is shipped as a single variable font (weight axis), so each
 * weight references the same TTF with a different `FontVariation.weight`.
 * Source files are bundled in res/font/ to avoid the cold-start flicker
 * that downloadable fonts cause.
 */

private val GeistFamily = FontFamily(
    Font(
        resId = R.font.geist_variable,
        weight = FontWeight.W300,
        variationSettings = FontVariation.Settings(FontVariation.weight(300))
    ),
    Font(
        resId = R.font.geist_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.geist_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.geist_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        resId = R.font.geist_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

private val InstrumentSerifFamily = FontFamily(
    Font(R.font.instrument_serif, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(R.font.instrument_serif_italic, weight = FontWeight.Normal, style = FontStyle.Italic)
)

private val tightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private val noPadding = PlatformTextStyle(includeFontPadding = false)

private fun base(
    family: FontFamily,
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    lineHeightMultiplier: Float = 1.4f,
    letterSpacing: Double = 0.0,
    italic: Boolean = false
): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = (size * lineHeightMultiplier).sp,
    fontWeight = weight,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    letterSpacing = (size * letterSpacing).sp,
    platformStyle = noPadding,
    lineHeightStyle = tightLineHeight
)

/**
 * Custom typographic scale. Maps onto Material 3 slots so we can still
 * use Material components, but every slot points at our scale.
 */
data class UltraprocessedTypography(
    val displayXl: TextStyle,
    val displayLg: TextStyle,
    val displayMd: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodySm: TextStyle,
    val mono: TextStyle,
    val overline: TextStyle
) {
    fun toMaterial(): Typography = Typography(
        displayLarge = displayXl,
        displayMedium = displayLg,
        displaySmall = displayMd,
        headlineLarge = displayLg,
        headlineMedium = displayMd,
        headlineSmall = title,
        titleLarge = title,
        titleMedium = body.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = bodySm.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = bodySm,
        labelLarge = bodySm.copy(fontWeight = FontWeight.Medium),
        labelMedium = overline,
        labelSmall = overline
    )
}

internal val DefaultTypography = UltraprocessedTypography(
    displayXl = base(InstrumentSerifFamily, 72, lineHeightMultiplier = 1.0f, letterSpacing = -0.005, italic = true),
    displayLg = base(InstrumentSerifFamily, 48, lineHeightMultiplier = 1.05f, letterSpacing = -0.005),
    displayMd = base(InstrumentSerifFamily, 32, lineHeightMultiplier = 1.1f, letterSpacing = -0.005),
    title = base(GeistFamily, 20, weight = FontWeight.SemiBold, lineHeightMultiplier = 1.2f),
    body = base(GeistFamily, 16, lineHeightMultiplier = 1.4f),
    bodySm = base(GeistFamily, 14, lineHeightMultiplier = 1.4f),
    mono = base(GeistFamily, 14, lineHeightMultiplier = 1.4f),
    overline = base(GeistFamily, 11, weight = FontWeight.Medium, lineHeightMultiplier = 1.2f, letterSpacing = 0.08)
)
