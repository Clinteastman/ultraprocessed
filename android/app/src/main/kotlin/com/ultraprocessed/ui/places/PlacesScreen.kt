package com.ultraprocessed.ui.places

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnLayout
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.novaColor
import com.ultraprocessed.ui.components.DateRangeChips
import com.ultraprocessed.ui.components.DateRangePreset
import com.ultraprocessed.ui.components.Overline
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Top-level "Places" experience: date range chips, OSM map below them
 * (markers per item, NOVA-tinted), then a chronological list of the
 * filtered items. Tapping a list row pans + zooms the map to that item.
 */
@Composable
fun PlacesScreen(onBack: () -> Unit) {
    val vm: PlacesViewModel = viewModel(factory = PlacesViewModel.Factory)
    val state by vm.state.collectAsState()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var selectedUuid by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Semantic.colors.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.Space.s5)
                    .padding(top = Tokens.Space.s7, bottom = Tokens.Space.s3),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    text = "Places",
                    style = MaterialTheme.typography.displaySmall,
                    color = Semantic.colors.inkHigh
                )
            }

            // Date range chips.
            Box(modifier = Modifier.padding(horizontal = Tokens.Space.s5)) {
                DateRangeChips(
                    selected = state.selected,
                    onSelect = { vm.selectPreset(it) }
                )
            }

            Spacer(Modifier.height(Tokens.Space.s4))

            // Map.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(horizontal = Tokens.Space.s5)
                    .clip(RoundedCornerShape(Tokens.Radius.md))
                    .background(Semantic.colors.surface1)
            ) {
                if (state.points.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(Tokens.Space.s4),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No located items in this range. Tag past items as Home in Settings, or grant location permission for new logs.",
                            color = Semantic.colors.inkMid,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                setTilesScaledToDpi(true)
                                isHorizontalMapRepetitionEnabled = false
                                isVerticalMapRepetitionEnabled = false
                                mapView = this
                            }
                        },
                        update = { map ->
                            map.overlays.clear()
                            val ctx = map.context
                            state.points.forEach { p ->
                                val accent = novaColor(p.novaClass).toArgb()
                                val marker = Marker(map).apply {
                                    position = GeoPoint(p.lat, p.lng)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    title = p.title
                                    subDescription = p.subtitle
                                    icon = makeDot(accent, sizePx = 26).toDrawable(ctx.resources)
                                }
                                map.overlays.add(marker)
                            }

                            // Center / fit only after the view is actually
                            // laid out - otherwise the controller's zoom +
                            // center calls happen before the viewport has
                            // a real size, and osmdroid never schedules
                            // tile downloads. doOnLayout fires once on the
                            // first measure pass.
                            val applyCamera: () -> Unit = {
                                val sel = state.points.firstOrNull { it.clientUuid == selectedUuid }
                                val pts = state.points
                                val maxSpan = if (pts.size > 1) maxOf(
                                    pts.maxOf { it.lat } - pts.minOf { it.lat },
                                    pts.maxOf { it.lng } - pts.minOf { it.lng }
                                ) else 0.0
                                when {
                                    sel != null -> {
                                        map.controller.animateTo(GeoPoint(sel.lat, sel.lng))
                                        map.controller.setZoom(17.0)
                                    }
                                    pts.size == 1 || maxSpan < 0.0005 -> {
                                        // Single item or tight cluster - pin
                                        // to the centroid; zoomToBoundingBox
                                        // misbehaves with a near-degenerate box.
                                        val cLat = pts.map { it.lat }.average()
                                        val cLng = pts.map { it.lng }.average()
                                        map.controller.setCenter(GeoPoint(cLat, cLng))
                                        map.controller.setZoom(16.5)
                                    }
                                    else -> {
                                        val box = BoundingBox(
                                            pts.maxOf { it.lat },
                                            pts.maxOf { it.lng },
                                            pts.minOf { it.lat },
                                            pts.minOf { it.lng }
                                        )
                                        map.zoomToBoundingBox(box, false, 80, 18.0, null)
                                    }
                                }
                                map.invalidate()
                            }
                            if (map.width > 0 && map.height > 0) applyCamera()
                            else map.doOnLayout { applyCamera() }
                        }
                    )
                }
            }

            Spacer(Modifier.height(Tokens.Space.s5))

            // List of items in this range. Tap centers the map.
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(modifier = Modifier.padding(horizontal = Tokens.Space.s5)) {
                    Overline(text = "${state.points.size} item${if (state.points.size == 1) "" else "s"} in range")
                }
                Spacer(Modifier.height(Tokens.Space.s2))
                if (state.points.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Tokens.Space.s5),
                    ) {
                        Text(
                            text = "Nothing logged with a location yet in this period.",
                            color = Semantic.colors.inkMid,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Tokens.Space.s5),
                        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)
                    ) {
                        items(state.points, key = { it.clientUuid }) { p ->
                            ItemRow(
                                point = p,
                                isSelected = p.clientUuid == selectedUuid,
                                onTap = { selectedUuid = p.clientUuid }
                            )
                        }
                        item("groups-spacer") { Spacer(Modifier.height(Tokens.Space.s4)) }
                        if (state.groups.isNotEmpty()) {
                            item("groups-header") {
                                Overline(text = "By place")
                                Spacer(Modifier.height(Tokens.Space.s2))
                            }
                            items(state.groups, key = { it.label }) { g ->
                                PlaceRow(group = g)
                                Spacer(Modifier.height(Tokens.Space.s2))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(point: MapPoint, isSelected: Boolean, onTap: () -> Unit) {
    val accent = novaColor(point.novaClass)
    val tintAlpha = if (Semantic.colors.isDark) 0.16f else 0.14f
    val highlight = if (isSelected) accent.copy(alpha = 0.35f) else accent.copy(alpha = tintAlpha)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(highlight)
            .clickable(onClick = onTap)
            .padding(Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = point.novaClass.toString(),
                color = Semantic.colors.inkInverse,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(Tokens.Space.s3))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = point.title,
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = point.subtitle,
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PlaceRow(group: PlaceGroup) {
    val accent = novaColor(group.novaAverage.roundToInt().coerceIn(1, 4))
    val tintAlpha = if (Semantic.colors.isDark) 0.16f else 0.14f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(accent.copy(alpha = tintAlpha))
            .padding(Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%.1f", group.novaAverage),
                color = Semantic.colors.inkInverse,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(Tokens.Space.s3))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = group.label,
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${group.itemCount} item${if (group.itemCount == 1) "" else "s"} · ${group.upfShare}% UPF · ${group.totalKcal.roundToInt()} kcal",
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun makeDot(colorArgb: Int, sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint().apply { isAntiAlias = true }
    paint.color = colorArgb
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 2f, paint)
    paint.color = AndroidColor.argb(180, 0, 0, 0)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 2f, paint)
    return bmp
}
