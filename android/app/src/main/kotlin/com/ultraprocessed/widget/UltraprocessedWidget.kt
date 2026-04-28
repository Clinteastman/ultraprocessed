package com.ultraprocessed.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ultraprocessed.MainActivity
import com.ultraprocessed.R
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.data.dao.ConsumptionWithFood
import com.ultraprocessed.data.entities.FastingProfile
import com.ultraprocessed.data.entities.ScheduleType
import com.ultraprocessed.domain.aggregate
import com.ultraprocessed.theme.UltraprocessedColors
import kotlinx.coroutines.flow.first
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Slick Glance-based home-screen widget styled to inherit the device's
 * Material You / Samsung One UI palette. NOVA semantic colors (green,
 * lime, orange, red) are preserved deliberately - the rest of the
 * chrome (background, surfaces, ink, accents) follows the system.
 *
 * Three blocks stacked:
 *  - Hero NOVA score with UPF % share + last-eaten relative time.
 *  - Today's eating timeline (24h bar with eating window + meal markers).
 *  - Square quick-action tiles: Scan, Search, Open. Monochrome icon
 *    centred, label centred below. Inner corners + outer radius use
 *    the system's `system_app_widget_*_radius` so the widget reads as
 *    "native" alongside Gemini, Calendar, etc.
 */
class UltraprocessedWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as UltraprocessedApplication).container

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24L * 60 * 60 * 1000 - 1
        val nowMs = System.currentTimeMillis()

        val items = container.consumptionRepository.observeRecent(limit = 200).first()
        val todayItems = items.filter { it.log.eatenAt in dayStart..dayEnd }
        val agg = aggregate(todayItems, dayStart, dayEnd)

        val novaAvg = agg.novaAverage
        val totalKcal = agg.totalKcal
        val nova4Cal = agg.novaCalories[4] ?: 0.0
        val upfShare = if (totalKcal > 0) (nova4Cal / totalKcal * 100.0).roundToInt() else 0
        val itemCount = agg.mealCount

        val fasting = container.fastingRepository.getActive()
        val palette = SystemPalette.read(context)
        val timelineBmp = renderTimeline(
            items = todayItems,
            fasting = fasting,
            nowMs = nowMs,
            dayStartMs = dayStart,
            dayEndMs = dayEnd,
            palette = palette,
            widthPx = 600,
            heightPx = 56
        )

        val lastEatenMs = items.firstOrNull()?.log?.eatenAt
        val lastEatenLabel = formatRelative(nowMs, lastEatenMs)

        provideContent {
            GlanceTheme {
                WidgetContent(
                    novaAvg = novaAvg,
                    upfShare = upfShare,
                    totalKcal = totalKcal,
                    itemCount = itemCount,
                    timelineBmp = timelineBmp,
                    lastEatenLabel = lastEatenLabel
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    novaAvg: Double?,
    upfShare: Int,
    totalKcal: Double,
    itemCount: Int,
    timelineBmp: Bitmap,
    lastEatenLabel: String
) {
    val colors = GlanceTheme.colors
    val novaAccent = if (novaAvg != null) novaColorForAverage(novaAvg) else null

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .padding(14.dp)
    ) {
        // Hero row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier
                    .size(52.dp)
                    .background(novaAccent?.let { staticColor(it) } ?: colors.surfaceVariant)
                    .cornerRadius(android.R.dimen.system_app_widget_inner_radius),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = novaAvg?.let { String.format("%.1f", it) } ?: "-",
                    style = TextStyle(
                        color = if (novaAccent != null) staticColor(InkOnNova) else colors.onSurfaceVariant,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "NOVA today",
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = if (itemCount == 0) "Nothing logged" else "$upfShare% UPF",
                    style = TextStyle(
                        color = colors.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = "${totalKcal.roundToInt()} kcal · $itemCount item${if (itemCount == 1) "" else "s"} · last $lastEatenLabel",
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }
        }

        Spacer(GlanceModifier.height(10.dp))

        // 24h timeline strip. FillBounds so the bitmap stretches to the
        // full widget width - the dots/ticks are positional rather than
        // circular reference shapes, so a horizontal scale is fine.
        Image(
            provider = ImageProvider(timelineBmp),
            contentDescription = "Today's eating pattern",
            contentScale = androidx.glance.layout.ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().height(20.dp)
        )

        Spacer(GlanceModifier.height(10.dp))

        // Square action tiles. defaultWeight() on the Row makes the tile
        // strip absorb whatever vertical space is left in the widget so
        // there's no dead band at the bottom on taller resizes.
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            ActionTile(
                label = "Scan",
                iconRes = R.drawable.ic_widget_camera,
                emphasis = TileEmphasis.Primary,
                route = "scan",
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(GlanceModifier.width(10.dp))
            ActionTile(
                label = "Search",
                iconRes = R.drawable.ic_widget_search,
                emphasis = TileEmphasis.Surface,
                route = "search",
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(GlanceModifier.width(10.dp))
            ActionTile(
                label = "Open",
                iconRes = R.drawable.ic_widget_open,
                emphasis = TileEmphasis.Surface,
                route = "home",
                modifier = GlanceModifier.defaultWeight()
            )
        }
    }
}

private enum class TileEmphasis { Primary, Surface }

@Composable
private fun ActionTile(
    label: String,
    iconRes: Int,
    emphasis: TileEmphasis,
    route: String,
    modifier: GlanceModifier
) {
    val colors = GlanceTheme.colors
    val (fill, ink) = when (emphasis) {
        TileEmphasis.Primary -> colors.primaryContainer to colors.onPrimaryContainer
        TileEmphasis.Surface -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(fill)
            .cornerRadius(android.R.dimen.system_app_widget_inner_radius)
            .clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(RouteParam to route)
                )
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(26.dp),
            colorFilter = ColorFilter.tint(ink)
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = label,
            style = TextStyle(
                color = ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

private fun novaColorForAverage(avg: Double): Color = when {
    avg <= 1.5 -> UltraprocessedColors.Nova1
    avg <= 2.5 -> UltraprocessedColors.Nova2
    avg <= 3.5 -> UltraprocessedColors.Nova3
    else -> UltraprocessedColors.Nova4
}

/** Static [androidx.glance.color.ColorProvider] used for the NOVA tile -
 *  semantic colour that mustn't shift with system theming. */
private fun staticColor(c: Color) = androidx.glance.color.ColorProvider(day = c, night = c)

/** Ink that always reads on the NOVA tile, regardless of which class. */
private val InkOnNova = Color(0xFF0B1020)

private fun isTre(t: ScheduleType): Boolean = when (t) {
    ScheduleType.SIXTEEN_EIGHT,
    ScheduleType.EIGHTEEN_SIX,
    ScheduleType.TWENTY_FOUR,
    ScheduleType.OMAD,
    ScheduleType.CUSTOM -> true
    else -> false
}

/**
 * Static palette resolved once per render from the system's Material You
 * tokens. We need real ints (not Compose [Color]) to draw with [Paint].
 */
private data class SystemPalette(
    val surface: Int,
    val onSurfaceLow: Int,
    val onSurface: Int,
    val accent: Int,
    val accentBand: Int,
) {
    companion object {
        fun read(context: Context): SystemPalette {
            // Material You system_* color resources land in API 31. On
            // older devices we use a sensible dark palette so the widget
            // still reads cleanly. The timeline uses a tinted accent
            // surface (rather than a neutral) so it visibly contrasts
            // with the widget's neutral background.
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val surface = ContextCompat.getColor(context, android.R.color.system_accent2_700)
                val onSurfaceLow = ContextCompat.getColor(context, android.R.color.system_accent2_200)
                val onSurface = ContextCompat.getColor(context, android.R.color.system_neutral1_50)
                val accent = ContextCompat.getColor(context, android.R.color.system_accent1_200)
                val accentBand = (accent and 0x00FFFFFF) or 0x55000000
                SystemPalette(surface, onSurfaceLow, onSurface, accent, accentBand)
            } else {
                SystemPalette(
                    surface = 0xFF2C3540.toInt(),
                    onSurfaceLow = 0xFF8A93A0.toInt(),
                    onSurface = 0xFFE8ECF1.toInt(),
                    accent = 0xFFB2FF5C.toInt(),
                    accentBand = 0x55B2FF5C
                )
            }
        }
    }
}

/**
 * Renders today's eating pattern as a small bitmap. Glance can't render
 * a Compose Canvas, so we hand-draw onto an Android Canvas and pass the
 * Bitmap through Glance's ImageProvider.
 */
private fun renderTimeline(
    items: List<ConsumptionWithFood>,
    fasting: FastingProfile?,
    nowMs: Long,
    dayStartMs: Long,
    dayEndMs: Long,
    palette: SystemPalette,
    widthPx: Int,
    heightPx: Int
): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val w = widthPx.toFloat()
    val h = heightPx.toFloat()

    val paint = Paint().apply { isAntiAlias = true }

    // Background.
    paint.color = palette.surface
    canvas.drawRoundRect(RectF(0f, 0f, w, h), 12f, 12f, paint)

    // Eating window band (TRE schedules only).
    if (fasting != null && fasting.active && isTre(fasting.scheduleType)) {
        val start = fasting.eatingWindowStartMinutes.coerceIn(0, 1440)
        val end = fasting.eatingWindowEndMinutes.coerceIn(0, 1440)
        if (end > start) {
            val xStart = w * (start / 1440f)
            val xEnd = w * (end / 1440f)
            paint.color = palette.accentBand
            canvas.drawRoundRect(RectF(xStart, 6f, xEnd, h - 6f), 6f, 6f, paint)
        }
    }

    // Hour ticks (06, 12, 18) for orientation.
    paint.color = palette.onSurfaceLow
    paint.strokeWidth = 1.5f
    listOf(6, 12, 18).forEach { hour ->
        val x = w * (hour * 60f / 1440f)
        canvas.drawLine(x, h - 4f, x, h, paint)
    }

    // Meal markers, NOVA-coloured.
    val totalSpan = (dayEndMs - dayStartMs).coerceAtLeast(1L).toFloat()
    items.forEach { item ->
        val ms = item.log.eatenAt
        if (ms in dayStartMs..dayEndMs) {
            val frac = ((ms - dayStartMs).toFloat() / totalSpan).coerceIn(0f, 1f)
            val cx = w * frac
            paint.color = novaColorForClass(item.food.novaClass).toArgb()
            canvas.drawCircle(cx, h / 2f, 5.5f, paint)
            paint.color = palette.surface
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            canvas.drawCircle(cx, h / 2f, 5.5f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    // "Now" line on top.
    if (nowMs in dayStartMs..dayEndMs) {
        val frac = ((nowMs - dayStartMs).toFloat() / totalSpan).coerceIn(0f, 1f)
        val nx = w * frac
        paint.color = palette.onSurface
        paint.strokeWidth = 2.5f
        canvas.drawLine(nx, 3f, nx, h - 3f, paint)
    }

    return bmp
}

private fun novaColorForClass(c: Int): Color = when (c) {
    1 -> UltraprocessedColors.Nova1
    2 -> UltraprocessedColors.Nova2
    3 -> UltraprocessedColors.Nova3
    else -> UltraprocessedColors.Nova4
}

private fun formatRelative(nowMs: Long, lastMs: Long?): String {
    if (lastMs == null) return "never"
    val diffMin = ((nowMs - lastMs) / 60_000L).coerceAtLeast(0)
    return when {
        diffMin < 1 -> "now"
        diffMin < 60 -> "${diffMin}m ago"
        diffMin < 60 * 24 -> "${diffMin / 60}h ago"
        else -> "${diffMin / (60 * 24)}d ago"
    }
}

private fun Color.toArgb(): Int =
    android.graphics.Color.argb(
        (alpha * 255).roundToInt(),
        (red * 255).roundToInt(),
        (green * 255).roundToInt(),
        (blue * 255).roundToInt()
    )

/** Action parameter key. Kept in sync with [ROUTE_EXTRA] when read in MainActivity. */
val RouteParam: ActionParameters.Key<String> = ActionParameters.Key(ROUTE_EXTRA)

const val ROUTE_EXTRA = "ultraprocessed_route"
