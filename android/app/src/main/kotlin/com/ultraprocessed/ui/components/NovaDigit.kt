package com.ultraprocessed.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.novaColor

/**
 * The signature element of the result screen: a giant Instrument Serif
 * italic NOVA digit on a soft tint, sized to be the loudest thing on the
 * page. Fades + scales in to feel intentional rather than dumped on screen.
 */
@Composable
fun NovaDigit(
    novaClass: Int,
    modifier: Modifier = Modifier,
    appearProgress: Float = 1f,
    sizeDp: Int = 132
) {
    val scale by animateFloatAsState(
        targetValue = 0.85f + 0.15f * appearProgress,
        animationSpec = tween(Tokens.Duration.Base),
        label = "nova-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = appearProgress,
        animationSpec = tween(Tokens.Duration.Base),
        label = "nova-alpha"
    )

    val accent = novaColor(novaClass)

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(accent.copy(alpha = if (Semantic.colors.isDark) 0.16f else 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = novaClass.coerceIn(1, 4).toString(),
            color = accent,
            fontSize = (sizeDp * 0.62f).sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            style = androidx.compose.material3.MaterialTheme.typography.displayLarge.copy(
                fontSize = (sizeDp * 0.62f).sp
            )
        )
    }
}

/** Small NOVA pill used in the history timeline. */
@Composable
fun NovaPill(
    novaClass: Int,
    modifier: Modifier = Modifier
) {
    val accent = novaColor(novaClass)
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 22.dp)
            .clip(RoundedCornerShape(Tokens.Radius.sm))
            .background(accent.copy(alpha = if (Semantic.colors.isDark) 0.18f else 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "NOVA $novaClass",
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
