package com.ultraprocessed.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ultraprocessed.data.dao.ConsumptionWithFood
import com.ultraprocessed.data.entities.FastingProfile
import com.ultraprocessed.data.entities.ScheduleType
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.UltraprocessedColors
import com.ultraprocessed.theme.novaColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 24h horizontal pattern view of *today*. Shows the eating window (for
 * TRE schedules) shaded; meal markers as small dots colored by NOVA;
 * a vertical "now" line so progress through the day is at-a-glance.
 *
 * Hidden when the selected range isn't a single day.
 */
@Composable
fun EatingTimeline(
    items: List<ConsumptionWithFood>,
    fasting: FastingProfile?,
    windowFromMs: Long,
    windowToMs: Long,
    modifier: Modifier = Modifier
) {
    val dayStart = remember(windowFromMs) {
        Calendar.getInstance().apply {
            timeInMillis = windowFromMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dayEnd = dayStart + 24L * 60 * 60 * 1000

    // Only show for a single-day range. Multi-day ranges would need a
    // different layout and we'd rather not draw a misleading 24h ruler.
    val singleDay = (windowToMs - windowFromMs) <= 24L * 60 * 60 * 1000 + 1000
    if (!singleDay) return

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val showNow = nowMs in dayStart..dayEnd

    val eatingStart = fasting?.takeIf { isTre(it.scheduleType) }?.eatingWindowStartMinutes
    val eatingEnd = fasting?.takeIf { isTre(it.scheduleType) }?.eatingWindowEndMinutes

    val eatingTint = UltraprocessedColors.Nova1.copy(alpha = if (Semantic.colors.isDark) 0.22f else 0.20f)
    val baseBg = Semantic.colors.surface2
    val nowColor = Semantic.colors.inkHigh
    val tickColor = Semantic.colors.inkLow.copy(alpha = 0.4f)

    Column(modifier = modifier.fillMaxWidth()) {
        Overline(text = "Today's pattern")
        Spacer(Modifier.height(Tokens.Space.s2))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(baseBg)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 6.dp)) {
                val w = size.width
                val h = size.height

                // Eating-window band.
                if (eatingStart != null && eatingEnd != null && eatingEnd > eatingStart) {
                    val xStart = w * (eatingStart / 1440f)
                    val xEnd = w * (eatingEnd / 1440f)
                    drawRect(
                        color = eatingTint,
                        topLeft = Offset(xStart, 4f),
                        size = Size(xEnd - xStart, h - 8f)
                    )
                }

                // Hour ticks (every 6h: 06, 12, 18) for orientation.
                listOf(6, 12, 18).forEach { hour ->
                    val x = w * (hour * 60f / 1440f)
                    drawLine(
                        color = tickColor,
                        start = Offset(x, h - 4f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                }

                // Meal markers.
                items.forEach { item ->
                    val mealMs = item.log.eatenAt
                    if (mealMs in dayStart..dayEnd) {
                        val frac = (mealMs - dayStart).toFloat() / (dayEnd - dayStart).toFloat()
                        val cx = w * frac
                        val color = novaColor(item.food.novaClass)
                        drawCircle(
                            color = color,
                            radius = 5f,
                            center = Offset(cx, h / 2f)
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.3f),
                            radius = 5f,
                            center = Offset(cx, h / 2f),
                            style = Stroke(width = 1f)
                        )
                    }
                }

                // "Now" line.
                if (showNow) {
                    val nowFrac = (nowMs - dayStart).toFloat() / (dayEnd - dayStart).toFloat()
                    val nx = w * nowFrac
                    drawLine(
                        color = nowColor,
                        start = Offset(nx, 2f),
                        end = Offset(nx, h - 2f),
                        strokeWidth = 2f
                    )
                }
            }
        }
        Spacer(Modifier.height(Tokens.Space.s1))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatHour(dayStart, 0),
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "12:00",
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "23:59",
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun isTre(t: ScheduleType): Boolean = when (t) {
    ScheduleType.SIXTEEN_EIGHT,
    ScheduleType.EIGHTEEN_SIX,
    ScheduleType.TWENTY_FOUR,
    ScheduleType.OMAD,
    ScheduleType.CUSTOM -> true
    else -> false
}

private fun formatHour(baseMs: Long, addHours: Int): String {
    val c = Calendar.getInstance().apply {
        timeInMillis = baseMs
        add(Calendar.HOUR_OF_DAY, addHours)
    }
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(c.timeInMillis))
}
