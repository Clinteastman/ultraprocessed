package com.ultraprocessed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ultraprocessed.theme.Tokens
import kotlin.math.roundToInt

/**
 * Small pill on the camera screen showing today's running calorie total.
 * Reads directly from local Room - works offline and without a backend.
 */
@Composable
fun TodayChip(kcalToday: Double, mealsToday: Int, modifier: Modifier = Modifier) {
    if (mealsToday == 0) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${kcalToday.roundToInt()} kcal · $mealsToday today",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
