package com.ultraprocessed.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ultraprocessed.theme.Semantic

/**
 * Tracked, uppercased micro-label. Section headers, status strips,
 * wordmark - anything that wants to feel like editorial chrome.
 */
@Composable
fun Overline(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Semantic.colors.inkMid
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.88.sp,
        fontSize = 11.sp,
        style = MaterialTheme.typography.labelMedium
    )
}
