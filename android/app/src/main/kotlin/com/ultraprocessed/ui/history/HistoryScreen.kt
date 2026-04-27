package com.ultraprocessed.ui.history

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ultraprocessed.data.dao.ConsumptionWithFood
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.novaColor
import com.ultraprocessed.ui.components.NovaPill
import com.ultraprocessed.ui.components.Overline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
    val items by vm.recent.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Semantic.colors.bg)
            .padding(horizontal = Tokens.Space.s5)
    ) {
        Spacer(Modifier.height(Tokens.Space.s7))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
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
                text = "History",
                style = MaterialTheme.typography.displaySmall,
                color = Semantic.colors.inkHigh
            )
        }

        Spacer(Modifier.height(Tokens.Space.s5))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = Tokens.Space.s9),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "Nothing logged yet. Scan something to get started.",
                    color = Semantic.colors.inkMid,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            val grouped = items.groupBy { dayBucket(it.log.eatenAt) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)
            ) {
                grouped.forEach { (label, dayItems) ->
                    item(key = "header-$label") {
                        DayHeader(label = label, items = dayItems)
                    }
                    items(dayItems, key = { it.log.clientUuid })
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(
    items: List<ConsumptionWithFood>,
    key: (ConsumptionWithFood) -> Any
) {
    items.forEach { item ->
        item(key = key(item)) {
            HistoryRow(item = item)
        }
    }
}

@Composable
private fun DayHeader(label: String, items: List<ConsumptionWithFood>) {
    val totalKcal = items.sumOf { it.log.kcalConsumedSnapshot ?: 0.0 }.roundToInt()
    val mealCount = items.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Tokens.Space.s5, bottom = Tokens.Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Overline(text = label)
        Overline(text = "$mealCount meals  ·  $totalKcal kcal")
    }
}

@Composable
private fun HistoryRow(item: ConsumptionWithFood) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val accent = novaColor(item.food.novaClass)
    val tintAlpha = if (Semantic.colors.isDark) 0.16f else 0.14f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(accent.copy(alpha = tintAlpha))
            .padding(Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImageThumb(item)
        Spacer(Modifier.size(Tokens.Space.s4))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.food.name,
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${timeFormatter.format(Date(item.log.eatenAt))}  ·  ${item.log.percentageEaten}%  ·  ${(item.log.kcalConsumedSnapshot ?: 0.0).roundToInt()} kcal",
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.size(Tokens.Space.s3))
        NovaPill(novaClass = item.food.novaClass)
    }
}

@Composable
private fun ImageThumb(item: ConsumptionWithFood) {
    val source = item.food.imagePath ?: item.food.imageUrl
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(Tokens.Radius.sm))
            .background(Semantic.colors.surface2)
    ) {
        if (!source.isNullOrBlank()) {
            AsyncImage(
                model = source,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun dayBucket(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        cal.sameDayAs(today) -> "Today"
        cal.sameDayAs(yesterday) -> "Yesterday"
        else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(epochMs))
    }
}

private fun Calendar.sameDayAs(other: Calendar): Boolean =
    get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
