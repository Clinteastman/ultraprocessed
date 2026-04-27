package com.ultraprocessed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import java.util.Calendar

enum class DateRangePreset(val label: String) {
    Today("Today"),
    Yesterday("Yesterday"),
    Last7("Last 7 days"),
    Last30("Last 30 days")
}

data class DateRangeWindow(
    val preset: DateRangePreset,
    val fromMs: Long,
    val toMs: Long,
    val label: String
)

fun computeRangeWindow(preset: DateRangePreset): DateRangeWindow {
    val now = Calendar.getInstance()
    fun startOfDay(c: Calendar): Long {
        val x = c.clone() as Calendar
        x.set(Calendar.HOUR_OF_DAY, 0); x.set(Calendar.MINUTE, 0)
        x.set(Calendar.SECOND, 0); x.set(Calendar.MILLISECOND, 0)
        return x.timeInMillis
    }
    fun endOfDay(c: Calendar): Long {
        val x = c.clone() as Calendar
        x.set(Calendar.HOUR_OF_DAY, 23); x.set(Calendar.MINUTE, 59)
        x.set(Calendar.SECOND, 59); x.set(Calendar.MILLISECOND, 999)
        return x.timeInMillis
    }

    return when (preset) {
        DateRangePreset.Today ->
            DateRangeWindow(preset, startOfDay(now), endOfDay(now), "Today")
        DateRangePreset.Yesterday -> {
            val y = now.clone() as Calendar
            y.add(Calendar.DAY_OF_YEAR, -1)
            DateRangeWindow(preset, startOfDay(y), endOfDay(y), "Yesterday")
        }
        DateRangePreset.Last7 -> {
            val s = now.clone() as Calendar
            s.add(Calendar.DAY_OF_YEAR, -6)
            DateRangeWindow(preset, startOfDay(s), endOfDay(now), "Last 7 days")
        }
        DateRangePreset.Last30 -> {
            val s = now.clone() as Calendar
            s.add(Calendar.DAY_OF_YEAR, -29)
            DateRangeWindow(preset, startOfDay(s), endOfDay(now), "Last 30 days")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateRangeChips(
    selected: DateRangePreset,
    onSelect: (DateRangePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)
    ) {
        DateRangePreset.entries.forEach { p ->
            val isActive = p == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Tokens.Radius.sm))
                    .background(if (isActive) Semantic.colors.accent else Semantic.colors.surface1)
                    .clickable { onSelect(p) }
                    .padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2)
            ) {
                Text(
                    text = p.label,
                    color = if (isActive) Semantic.colors.inkInverse else Semantic.colors.inkMid,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}
