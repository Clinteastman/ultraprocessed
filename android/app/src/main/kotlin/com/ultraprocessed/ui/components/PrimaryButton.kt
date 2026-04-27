package com.ultraprocessed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens

/**
 * Wide accent-coloured primary action button. Used for the "I ate it" CTA
 * and any other major affirmative action.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(
                if (enabled) Semantic.colors.accent else Semantic.colors.surface3
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Tokens.Space.s5),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) Semantic.colors.inkInverse else Semantic.colors.inkLow,
            fontWeight = FontWeight.SemiBold,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
        )
    }
}

/** Subtle secondary action; outlined-ish on dark, ghost on light. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Semantic.colors.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.Space.s5),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Semantic.colors.inkHigh,
            fontWeight = FontWeight.Medium,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}
