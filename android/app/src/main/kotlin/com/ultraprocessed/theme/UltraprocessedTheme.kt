package com.ultraprocessed.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Top-level theme.
 *
 * Material 3 components remain usable, but every Material slot is mapped
 * onto our palette + typography so nothing leaks default Material You
 * blue. Custom semantic colors and tokens are reached via
 * `Semantic.colors.*` and `Tokens.*` rather than `MaterialTheme.colorScheme`.
 */
@Composable
fun UltraprocessedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val semantic = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val typography = DefaultTypography

    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = semantic.accent,
            onPrimary = semantic.inkInverse,
            secondary = semantic.accent,
            onSecondary = semantic.inkInverse,
            tertiary = semantic.accent,
            background = semantic.bg,
            onBackground = semantic.inkHigh,
            surface = semantic.surface1,
            onSurface = semantic.inkHigh,
            surfaceVariant = semantic.surface2,
            onSurfaceVariant = semantic.inkMid,
            outline = semantic.inkLow,
            outlineVariant = semantic.surface3,
            error = semantic.error,
            onError = semantic.inkInverse
        )
    } else {
        lightColorScheme(
            primary = semantic.accent,
            onPrimary = semantic.inkInverse,
            secondary = semantic.accent,
            onSecondary = semantic.inkInverse,
            tertiary = semantic.accent,
            background = semantic.bg,
            onBackground = semantic.inkHigh,
            surface = semantic.surface1,
            onSurface = semantic.inkHigh,
            surfaceVariant = semantic.surface2,
            onSurfaceVariant = semantic.inkMid,
            outline = semantic.inkLow,
            outlineVariant = semantic.surface3,
            error = semantic.error,
            onError = semantic.inkInverse
        )
    }

    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = typography.toMaterial(),
            content = content
        )
    }
}
