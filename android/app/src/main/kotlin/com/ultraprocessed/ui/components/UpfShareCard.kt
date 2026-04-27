package com.ultraprocessed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.UltraprocessedColors
import kotlin.math.roundToInt

@Composable
fun UpfShareCard(
    novaCalories: Map<Int, Double>,
    totalKcal: Double,
    modifier: Modifier = Modifier
) {
    val upfKcal = novaCalories[4] ?: 0.0
    val wholeKcal = novaCalories[1] ?: 0.0
    val upfPct = if (totalKcal > 0) (upfKcal / totalKcal) * 100 else null
    val wholePct = if (totalKcal > 0) (wholeKcal / totalKcal) * 100 else null
    val (color, verdict) = bandFor(upfPct)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s4)
    ) {
        Overline(text = "Ultra-processed share")
        Spacer(Modifier.height(Tokens.Space.s2))

        if (upfPct == null) {
            Text(
                text = "Nothing logged yet.",
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${upfPct.roundToInt()}%",
                color = color,
                fontSize = 56.sp,
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.size(Tokens.Space.s2))
            Text(
                text = "of calories",
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Text(
            text = verdict,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(Tokens.Space.s3))
        StackedBar(novaCalories = novaCalories, totalKcal = totalKcal)

        Spacer(Modifier.height(Tokens.Space.s3))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LegendDot(
                color = UltraprocessedColors.Nova1,
                label = "Whole",
                value = wholePct?.let { "${it.roundToInt()}%" } ?: "—"
            )
            LegendDot(
                color = UltraprocessedColors.Nova4,
                label = "Ultra",
                value = "${upfPct.roundToInt()}%"
            )
        }
        Spacer(Modifier.height(Tokens.Space.s2))
        Text(
            text = "${upfKcal.roundToInt()} kcal of ${totalKcal.roundToInt()} from NOVA 4.",
            color = Semantic.colors.inkLow,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StackedBar(novaCalories: Map<Int, Double>, totalKcal: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(Tokens.Radius.xs))
            .background(Semantic.colors.surface3)
    ) {
        if (totalKcal <= 0) return
        for (cls in 1..4) {
            val kcal = novaCalories[cls] ?: 0.0
            val pct = (kcal / totalKcal).toFloat()
            if (pct > 0) {
                Box(
                    modifier = Modifier
                        .weight(pct, fill = true)
                        .background(novaColorFor(cls))
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(Modifier.size(Tokens.Space.s2))
        Text(
            text = label,
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.size(Tokens.Space.s2))
        Text(
            text = value,
            color = Semantic.colors.inkHigh,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun bandFor(pct: Double?): Pair<Color, String> {
    if (pct == null) return UltraprocessedColors.DarkInkLow to ""
    return when {
        pct < 10 -> UltraprocessedColors.Nova1 to "Hardly any UPF."
        pct < 25 -> UltraprocessedColors.Nova2 to "Low ultra-processed."
        pct < 50 -> UltraprocessedColors.Nova3 to "Moderate UPF share."
        pct < 70 -> UltraprocessedColors.Nova4 to "High ultra-processed."
        else -> UltraprocessedColors.Nova4 to "Mostly ultra-processed."
    }
}

private fun novaColorFor(cls: Int): Color = when (cls) {
    1 -> UltraprocessedColors.Nova1
    2 -> UltraprocessedColors.Nova2
    3 -> UltraprocessedColors.Nova3
    4 -> UltraprocessedColors.Nova4
    else -> UltraprocessedColors.DarkInkLow
}
