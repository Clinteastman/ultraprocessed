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
    Last30("Last 30 days"),
    Custom("Custom")
}

data class DateRangeWindow(
    val preset: DateRangePreset,
    val fromMs: Long,
    val toMs: Long,
    val label: String
)

private fun startOfDay(c: Calendar): Long {
    val x = c.clone() as Calendar
    x.set(Calendar.HOUR_OF_DAY, 0); x.set(Calendar.MINUTE, 0)
    x.set(Calendar.SECOND, 0); x.set(Calendar.MILLISECOND, 0)
    return x.timeInMillis
}

private fun endOfDay(c: Calendar): Long {
    val x = c.clone() as Calendar
    x.set(Calendar.HOUR_OF_DAY, 23); x.set(Calendar.MINUTE, 59)
    x.set(Calendar.SECOND, 59); x.set(Calendar.MILLISECOND, 999)
    return x.timeInMillis
}

private fun ms(d: Long): Calendar = Calendar.getInstance().apply { timeInMillis = d }

fun computeRangeWindow(
    preset: DateRangePreset,
    customFromMs: Long? = null,
    customToMs: Long? = null
): DateRangeWindow {
    val now = Calendar.getInstance()
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
        DateRangePreset.Custom -> {
            val from = customFromMs ?: startOfDay(now)
            val to = customToMs ?: endOfDay(now)
            DateRangeWindow(preset, from, to, formatCustom(from, to))
        }
    }
}

private fun formatCustom(fromMs: Long, toMs: Long): String {
    val from = ms(fromMs); val to = ms(toMs)
    val sameDay =
        from.get(Calendar.YEAR) == to.get(Calendar.YEAR) &&
        from.get(Calendar.MONTH) == to.get(Calendar.MONTH) &&
        from.get(Calendar.DAY_OF_MONTH) == to.get(Calendar.DAY_OF_MONTH)
    return if (sameDay) {
        java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(fromMs))
    } else {
        val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
        val full = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
        "${fmt.format(java.util.Date(fromMs))} - ${full.format(java.util.Date(toMs))}"
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
