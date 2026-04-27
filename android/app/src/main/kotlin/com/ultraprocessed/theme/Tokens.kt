package com.ultraprocessed.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Easing as ComposeEasing

/**
 * Spacing, radius, motion, and semantic-color tokens shared across the app.
 * Source of truth: docs/design.md and docs/motion.md.
 *
 * Access via `MaterialTheme` extensions: e.g. `Tokens.space5`,
 * `Tokens.motion.standard`. Tokens never reference Material defaults.
 */
object Tokens {

    object Space {
        val s1: Dp = 4.dp
        val s2: Dp = 8.dp
        val s3: Dp = 12.dp
        val s4: Dp = 16.dp
        val s5: Dp = 24.dp
        val s6: Dp = 32.dp
        val s7: Dp = 48.dp
        val s8: Dp = 72.dp
        val s9: Dp = 96.dp

        val screenEdge: Dp = s5
        val screenEdgeHero: Dp = s6
    }

    object Radius {
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 20.dp
        val xl: Dp = 32.dp
    }

    object Duration {
        const val Fast: Int = 120
        const val Base: Int = 220
        const val Slow: Int = 360
        const val Hold: Int = 720
    }

    object Curves {
        val Emphasized: ComposeEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        val Standard: ComposeEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
        val Decelerate: ComposeEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
        val Accelerate: ComposeEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
    }

    object Spring {
        val SpringSnappy = spring<Float>(
            stiffness = 600f,
            dampingRatio = 0.8f
        )
        val SpringSoft = spring<Float>(
            stiffness = 250f,
            dampingRatio = 0.85f
        )
    }
}

/**
 * Composition local exposing the live palette. Composables read colors via
 * `LocalSemanticColors.current.bg` etc, regardless of theme mode.
 */
data class SemanticColors(
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val inkHigh: Color,
    val inkMid: Color,
    val inkLow: Color,
    val inkInverse: Color,
    val accent: Color,
    val accentPressed: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val isDark: Boolean
)

internal val LocalSemanticColors = staticCompositionLocalOf<SemanticColors> {
    error("LocalSemanticColors not provided. Wrap content in UltraprocessedTheme.")
}

object Semantic {
    val colors: SemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSemanticColors.current
}

internal val DarkSemanticColors = SemanticColors(
    bg = UltraprocessedColors.DarkBg,
    surface1 = UltraprocessedColors.DarkSurface1,
    surface2 = UltraprocessedColors.DarkSurface2,
    surface3 = UltraprocessedColors.DarkSurface3,
    inkHigh = UltraprocessedColors.DarkInkHigh,
    inkMid = UltraprocessedColors.DarkInkMid,
    inkLow = UltraprocessedColors.DarkInkLow,
    inkInverse = UltraprocessedColors.DarkInkInverse,
    accent = UltraprocessedColors.AccentDark,
    accentPressed = UltraprocessedColors.AccentDarkPressed,
    success = UltraprocessedColors.Success,
    warning = UltraprocessedColors.Warning,
    error = UltraprocessedColors.Error,
    isDark = true
)

internal val LightSemanticColors = SemanticColors(
    bg = UltraprocessedColors.LightBg,
    surface1 = UltraprocessedColors.LightSurface1,
    surface2 = UltraprocessedColors.LightSurface2,
    surface3 = UltraprocessedColors.LightSurface3,
    inkHigh = UltraprocessedColors.LightInkHigh,
    inkMid = UltraprocessedColors.LightInkMid,
    inkLow = UltraprocessedColors.LightInkLow,
    inkInverse = UltraprocessedColors.LightInkInverse,
    accent = UltraprocessedColors.AccentLight,
    accentPressed = UltraprocessedColors.AccentLightPressed,
    success = UltraprocessedColors.Success,
    warning = UltraprocessedColors.Warning,
    error = UltraprocessedColors.Error,
    isDark = false
)
