package com.ultraprocessed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ultraprocessed.theme.Semantic

/**
 * Tappable circular "i" badge sized to sit next to a section label.
 * Opens a simple AlertDialog with [title] and [body]. Body can include
 * newlines for line-by-line explanation copy.
 */
@Composable
fun InfoIcon(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Semantic.colors.surface2)
            .clickable { open = true },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "i",
            color = Semantic.colors.inkMid,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = {
                Text(
                    text = title,
                    color = Semantic.colors.inkHigh,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = body,
                    color = Semantic.colors.inkMid,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text("Got it", color = Semantic.colors.accent)
                }
            },
            containerColor = Semantic.colors.surface1,
            titleContentColor = Semantic.colors.inkHigh,
            textContentColor = Semantic.colors.inkMid
        )
    }
}
