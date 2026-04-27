package com.ultraprocessed.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Small sync state icon for the camera screen header. States:
 *  - Done: nothing pending, last sync OK.
 *  - Pending: at least one local row hasn't been pushed yet.
 *  - Syncing: sync is currently in flight (animated rotation).
 *  - Off: backend isn't configured or last sync failed.
 *
 * Tap to manually re-sync.
 */
enum class SyncIconState { Done, Pending, Syncing, Off }

@Composable
fun SyncStatusIcon(
    state: SyncIconState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, tint) = when (state) {
        SyncIconState.Done -> Icons.Default.CloudDone to Color.White
        SyncIconState.Pending -> Icons.Default.CloudUpload to Color(0xFFE8A04A)
        SyncIconState.Syncing -> Icons.Default.Sync to Color.White
        SyncIconState.Off -> Icons.Default.CloudOff to Color.White.copy(alpha = 0.6f)
    }

    val transition = rememberInfiniteTransition(label = "sync-spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (state == SyncIconState.Syncing) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync-spin-rot"
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Sync status: ${state.name}",
            tint = tint,
            modifier = if (state == SyncIconState.Syncing) Modifier.rotate(rotation) else Modifier
        )
    }
}
