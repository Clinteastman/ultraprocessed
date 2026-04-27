package com.ultraprocessed.theme

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme

/**
 * Real implementation lands in the theme task. This stub keeps the scaffold
 * compilable while we wire dependencies; replaced by the proper Compose theme
 * with custom palette, typography, and tokens.
 */
@Composable
fun UltraprocessedTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
