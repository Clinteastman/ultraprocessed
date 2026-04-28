package com.ultraprocessed.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.UltraprocessedColors
import com.ultraprocessed.ui.components.Overline

/**
 * Plain-language reference for how the app reads its own data: what
 * NOVA scores mean, how fasting schedules work, what the restricted-day
 * kcal cap is, and why we lead with processing rather than calories.
 *
 * Reachable from Settings -> "How this app works".
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Semantic.colors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Space.s5)
                .padding(bottom = Tokens.Space.s8)
        ) {
            Spacer(Modifier.height(Tokens.Space.s7))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clickable(onClick = onBack),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Semantic.colors.inkHigh
                    )
                }
                Spacer(Modifier.size(Tokens.Space.s2))
                Text(
                    text = "How this app works",
                    style = MaterialTheme.typography.displaySmall,
                    color = Semantic.colors.inkHigh
                )
            }

            Spacer(Modifier.height(Tokens.Space.s6))

            HelpSection(
                heading = "Why we lead with processing",
                body = "Calories are necessary but not sufficient. Two days of 2000 kcal can be very different for your body if one is whole foods and the other is ultra-processed. Hero metrics on this app put NOVA score and UPF share first; calories are a supporting stat, not the headline."
            )

            HelpSection(heading = "NOVA classes") {
                NovaRow(
                    n = 1,
                    label = "Unprocessed / minimally processed",
                    blurb = "Whole foods, fresh fruit, vegetables, eggs, plain meat, fish, milk."
                )
                NovaRow(
                    n = 2,
                    label = "Processed culinary ingredients",
                    blurb = "Oils, butter, sugar, salt, vinegar. Used to cook, not eaten alone."
                )
                NovaRow(
                    n = 3,
                    label = "Processed foods",
                    blurb = "Bread, cheese, tinned veg in salt, smoked meat, fresh pasta. Recognisable food + a few ingredients."
                )
                NovaRow(
                    n = 4,
                    label = "Ultra-processed foods (UPF)",
                    blurb = "Long ingredient lists with industrial additives, emulsifiers, sweeteners. Soft drinks, mass-market biscuits, shaped meat, ready meals."
                )
            }

            HelpSection(
                heading = "How is the daily NOVA score worked out?",
                body = "It's a kcal-weighted average of every item logged that day. A 200 kcal slice of toast (NOVA 3) and a 50 kcal apple (NOVA 1) average to ~2.6, not 2.0 - the toast is doing more of the dietary work that day. Lower is better."
            )

            HelpSection(
                heading = "UPF share %",
                body = "What fraction of today's calories came from NOVA 4 items. Often more actionable than the average score - cutting one biscuit can move this by 10 percentage points even if your average barely shifts."
            )

            HelpSection(heading = "Fasting schedules") {
                Bullet("**Time-restricted (TRE)** - 16:8, 18:6, 20:4, OMAD: you pick a daily eating window. Outside it, you fast.")
                Bullet("**Multi-day patterns** - 5:2, 4:3, ADF: you pick which weekdays are restricted. On a restricted day, you cap calories (or fast fully); the other days are normal eating.")
                Bullet("**Custom** - either kind, set by hand: pick an eating window AND/OR restricted-day mask.")
            }

            HelpSection(
                heading = "What's the \"500 kcal cap\" about?",
                body = "On a 5:2-style restricted day, the convention is to eat ~500 kcal (women) / ~600 kcal (men) - roughly 25% of typical maintenance. The home strip shows that cap so you know your target on a restricted day. Toggle \"Full fast\" in Settings if you want a water-only day instead."
            )

            HelpSection(
                heading = "Eating window for restricted-day schedules",
                body = "On normal (non-restricted) days for a 5:2/4:3/ADF schedule, the app applies whatever eating window you've set as a TRE-style routine. So you can run \"5:2 + 16:8 on the other days\" out of one profile."
            )

            HelpSection(
                heading = "Where data lives",
                body = "Everything on this device is in a local SQLite database. Logging works fully offline. If you've paired a backend, syncs happen automatically (push + pull) so the dashboard, Home Assistant, and other devices stay in step. Pull-to-refresh on the home screen forces a sync."
            )

            HelpSection(
                heading = "Locations",
                body = "When you log an item, the app tries to capture a fresh GPS fix. If the device can't supply one or you haven't granted permission, it falls back to the \"Home\" coords you've saved in Settings. The \"Tag past items as Home\" button backfills any older logs without a location."
            )

            Spacer(Modifier.height(Tokens.Space.s6))
        }
    }
}

@Composable
private fun HelpSection(heading: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.s5)) {
        Overline(text = heading)
        Spacer(Modifier.height(Tokens.Space.s2))
        Text(
            text = body,
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun HelpSection(heading: String, content: @Composable ColumnContent.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.s5)) {
        Overline(text = heading)
        Spacer(Modifier.height(Tokens.Space.s2))
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            ColumnContent.content()
        }
    }
}

private object ColumnContent

@Composable
private fun ColumnContent.Bullet(text: String) {
    val display = text.replace("**", "")
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "•  ",
            color = Semantic.colors.inkLow,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = display,
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ColumnContent.NovaRow(n: Int, label: String, blurb: String) {
    val color = when (n) {
        1 -> UltraprocessedColors.Nova1
        2 -> UltraprocessedColors.Nova2
        3 -> UltraprocessedColors.Nova3
        else -> UltraprocessedColors.Nova4
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(color.copy(alpha = if (Semantic.colors.isDark) 0.16f else 0.14f))
            .padding(Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = n.toString(),
                color = Semantic.colors.inkInverse,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(Tokens.Space.s3))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = blurb,
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
