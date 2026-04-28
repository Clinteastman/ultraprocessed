package com.ultraprocessed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import com.ultraprocessed.data.dao.ConsumptionWithFood
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.novaColor
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import androidx.core.graphics.drawable.toDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb

/**
 * Compact non-interactive map preview for the home screen. Shows where
 * the [items] in the current date window were logged, auto-fitted to
 * their bounding box. Tapping anywhere on the map jumps to the full
 * Places screen via [onTap].
 *
 * The osmdroid MapView is rendered as a background; an overlay Box
 * absorbs all touches so the map never scrolls/zooms underneath.
 */
@Composable
fun MiniPlacesMap(
    items: List<ConsumptionWithFood>,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val located = items.filter { it.log.lat != null && it.log.lng != null }

    Column(modifier = modifier.fillMaxWidth()) {
        Overline(text = "Where")
        Spacer(Modifier.size(Tokens.Space.s2))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(Tokens.Radius.md))
                .background(Semantic.colors.surface1)
                .clickable(onClick = onTap)
        ) {
            if (located.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(Tokens.Space.s4),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No located items in this range. Tap to open the full map.",
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
                            setMultiTouchControls(false)
                            isClickable = false
                            isFocusable = false
                            isFocusableInTouchMode = false
                            isHorizontalScrollBarEnabled = false
                            isVerticalScrollBarEnabled = false
                            // No zoom controls UI; this is a preview.
                            setBuiltInZoomControls(false)
                        }
                    },
                    update = { map ->
                        map.overlays.clear()
                        val ctx = map.context
                        located.forEach { item ->
                            val accent = novaColor(item.food.novaClass).toArgb()
                            val marker = Marker(map).apply {
                                position = GeoPoint(item.log.lat!!, item.log.lng!!)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = makeDot(accent).toDrawable(ctx.resources)
                            }
                            map.overlays.add(marker)
                        }
                        // Camera changes only trigger tile fetches once the
                        // MapView has a measured viewport, so defer to
                        // doOnLayout on the first pass.
                        val applyCamera: () -> Unit = {
                            val pts = located.map { GeoPoint(it.log.lat!!, it.log.lng!!) }
                            val centroidLat = pts.map { it.latitude }.average()
                            val centroidLng = pts.map { it.longitude }.average()
                            val maxSpan = maxOf(
                                pts.maxOf { it.latitude } - pts.minOf { it.latitude },
                                pts.maxOf { it.longitude } - pts.minOf { it.longitude }
                            )
                            // ~50m at this latitude. When everything's at
                            // (near-)identical coords, zoomToBoundingBox
                            // with a near-degenerate box behaves badly, so
                            // explicitly pin to the centroid instead.
                            if (pts.size == 1 || maxSpan < 0.0005) {
                                map.controller.setCenter(GeoPoint(centroidLat, centroidLng))
                                map.controller.setZoom(16.5)
                            } else {
                                val box = BoundingBox(
                                    pts.maxOf { it.latitude },
                                    pts.maxOf { it.longitude },
                                    pts.minOf { it.latitude },
                                    pts.minOf { it.longitude }
                                )
                                map.zoomToBoundingBox(box, false, 40, 17.0, null)
                            }
                            map.invalidate()
                        }
                        if (map.width > 0 && map.height > 0) applyCamera()
                        else map.doOnLayout { applyCamera() }
                    }
                )
            }
            // Tap-absorbing overlay so the underlying MapView never
            // intercepts gestures - this is a preview, not a control.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onTap)
            )
        }
    }
}

/** A small filled circle bitmap, used as a custom marker icon. */
private fun makeDot(colorArgb: Int, sizePx: Int = 22): Bitmap {
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
