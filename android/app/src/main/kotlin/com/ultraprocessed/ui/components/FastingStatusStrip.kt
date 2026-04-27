package com.ultraprocessed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ultraprocessed.data.entities.FastingProfile
import com.ultraprocessed.domain.FastingDisplay
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.UltraprocessedColors
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun FastingStatusStrip(
    profile: FastingProfile?,
    onTapEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Trigger recomposition every 30s so countdowns update.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }

    val state = remember(profile, nowMs) {
        FastingDisplay.compute(profile, Calendar.getInstance().apply { timeInMillis = nowMs })
    }

    if (state == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Tokens.Radius.md))
                .background(Semantic.colors.surface1)
                .clickable(onClick = onTapEdit)
                .padding(Tokens.Space.s4)
        ) {
            Text(
                text = "No fasting plan set. Tap to configure.",
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val accent = when (state.mood) {
        FastingDisplay.Mood.Eating, FastingDisplay.Mood.Normal -> UltraprocessedColors.Nova1
        FastingDisplay.Mood.Fasting, FastingDisplay.Mood.Restricted -> UltraprocessedColors.Nova3
        FastingDisplay.Mood.None -> UltraprocessedColors.DarkInkLow
    }
    val tintAlpha = if (Semantic.colors.isDark) 0.16f else 0.14f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(accent.copy(alpha = tintAlpha))
            .clickable(onClick = onTapEdit)
            .padding(Tokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.size(Tokens.Space.s3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.title,
                color = accent,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = state.sub,
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
