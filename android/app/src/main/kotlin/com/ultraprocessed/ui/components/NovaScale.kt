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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.UltraprocessedColors
import com.ultraprocessed.theme.novaColor

/**
 * The four NOVA tiers as a horizontal scale, with the current one
 * highlighted. Below: a one-line rubric so the user doesn't need to
 * memorise what the numbers mean.
 *
 * Slot this directly under the big NOVA digit on the result screen.
 */
@Composable
fun NovaScale(
    novaClass: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)
        ) {
            (1..4).forEach { tier ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (tier == novaClass) 12.dp else 6.dp)
                        .clip(RoundedCornerShape(Tokens.Radius.xs))
                        .background(
                            if (tier == novaClass) novaColor(tier)
                            else Semantic.colors.surface3
                        ),
                    contentAlignment = Alignment.Center
                ) { /* visual only */ }
            }
        }

        Spacer(Modifier.height(Tokens.Space.s2))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "1 · whole",
                color = if (novaClass == 1) UltraprocessedColors.Nova1 else Semantic.colors.inkLow,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "2 · ingredient",
                color = if (novaClass == 2) UltraprocessedColors.Nova2 else Semantic.colors.inkLow,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "3 · processed",
                color = if (novaClass == 3) UltraprocessedColors.Nova3 else Semantic.colors.inkLow,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "4 · ultra",
                color = if (novaClass == 4) UltraprocessedColors.Nova4 else Semantic.colors.inkLow,
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(Modifier.height(Tokens.Space.s3))

        Text(
            text = oneLinerFor(novaClass),
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun oneLinerFor(novaClass: Int): String = when (novaClass) {
    1 -> "Real, whole food. Eat freely."
    2 -> "Cooking ingredient. Use as part of meals, not alone."
    3 -> "Lightly processed. Fine in moderation."
    4 -> "Ultra-processed. The kind to limit."
    else -> "Unrecognised."
}
