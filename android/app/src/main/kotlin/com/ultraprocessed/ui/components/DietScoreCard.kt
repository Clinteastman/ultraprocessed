package com.ultraprocessed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.UltraprocessedColors

/**
 * Compact NOVA-score-of-your-diet card for the phone home screen.
 * Mirrors the dashboard's DietScoreCard but takes less vertical space.
 */
@Composable
fun DietScoreCard(
    novaAverage: Double?,
    mealCount: Int,
    modifier: Modifier = Modifier
) {
    val accent = if (novaAverage != null) colorFor(novaAverage) else UltraprocessedColors.DarkInkLow
    val verdict = if (novaAverage != null) verdictFor(novaAverage) else ""

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s4)
    ) {
        Overline(text = "Diet NOVA score")
        Spacer(Modifier.height(Tokens.Space.s2))

        if (novaAverage == null || mealCount == 0) {
            Text(
                text = "Nothing logged yet.",
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = String.format("%.1f", novaAverage),
                color = accent,
                fontSize = 56.sp,
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.size(Tokens.Space.s2))
            Text(
                text = "/ 4",
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Text(
            text = verdict,
            color = accent,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(Tokens.Space.s3))
        ScaleStrip(novaAverage = novaAverage, accent = accent)

        Spacer(Modifier.height(Tokens.Space.s2))
        Text(
            text = "Weighted by calories across $mealCount meal${if (mealCount == 1) "" else "s"}.",
            color = Semantic.colors.inkLow,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ScaleStrip(novaAverage: Double, accent: Color) {
    val pct = ((novaAverage.coerceIn(1.0, 4.0) - 1.0) / 3.0).toFloat()
    val markerSize: Dp = 14.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(markerSize)
    ) {
        val markerOffsetX = ((maxWidth - markerSize) * pct)
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .height(8.dp)
                .clip(RoundedCornerShape(Tokens.Radius.xs))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            UltraprocessedColors.Nova1,
                            UltraprocessedColors.Nova2,
                            UltraprocessedColors.Nova3,
                            UltraprocessedColors.Nova4
                        )
                    )
                )
        )
        // Marker
        Box(
            modifier = Modifier
                .offset(x = markerOffsetX)
                .size(markerSize)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, accent, CircleShape)
        )
    }
}

private fun colorFor(avg: Double): Color = when {
    avg < 1.5 -> UltraprocessedColors.Nova1
    avg < 2.5 -> UltraprocessedColors.Nova2
    avg < 3.5 -> UltraprocessedColors.Nova3
    else -> UltraprocessedColors.Nova4
}

private fun verdictFor(avg: Double): String = when {
    avg < 1.4 -> "Mostly whole foods."
    avg < 1.8 -> "Lots of whole foods."
    avg < 2.4 -> "Some processing."
    avg < 2.8 -> "Mainly processed."
    avg < 3.4 -> "Heavy on processed."
    avg < 3.7 -> "Mostly ultra-processed."
    else -> "Ultra-processed dominated."
}
