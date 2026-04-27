package com.ultraprocessed.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ultraprocessed.data.dao.ConsumptionWithFood
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.novaColor
import com.ultraprocessed.ui.components.DateRangeChips
import com.ultraprocessed.ui.components.DietScoreCard
import com.ultraprocessed.ui.components.NovaPill
import com.ultraprocessed.ui.components.Overline
import com.ultraprocessed.ui.components.UpfShareCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onScanTap: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val selected by vm.selected.collectAsState()
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<ConsumptionWithFood?>(null) }

    val agg = state.aggregation

    Box(modifier = Modifier.fillMaxSize().background(Semantic.colors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Tokens.Space.s5,
                end = Tokens.Space.s5,
                top = Tokens.Space.s7,
                bottom = 96.dp  // leaves room for the FAB
            ),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)
        ) {
            item("header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Overline(text = "Ultraprocessed")
                        Spacer(Modifier.height(Tokens.Space.s1))
                        Text(
                            text = state.window.label,
                            style = MaterialTheme.typography.displaySmall,
                            color = Semantic.colors.inkHigh
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Semantic.colors.surface1)
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Semantic.colors.inkHigh
                        )
                    }
                }
            }

            item("range") {
                DateRangeChips(selected = selected, onSelect = vm::selectPreset)
            }

            item("diet-score") {
                DietScoreCard(
                    novaAverage = agg.novaAverage,
                    mealCount = agg.mealCount
                )
            }

            item("upf-share") {
                UpfShareCard(
                    novaCalories = agg.novaCalories,
                    totalKcal = agg.totalKcal
                )
            }

            item("calories") {
                CalorieSummary(
                    totalKcal = agg.totalKcal,
                    mealCount = agg.mealCount
                )
            }

            if (state.items.isNotEmpty()) {
                item("meals-header") {
                    Spacer(Modifier.height(Tokens.Space.s2))
                    Overline(text = "Meals")
                }
                items(state.items, key = { it.log.clientUuid }) { item ->
                    MealRow(item = item, onTap = { editing = item })
                }
            } else {
                item("empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Tokens.Space.s6),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nothing logged in this period.",
                            color = Semantic.colors.inkMid,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // FAB - the primary action: open the scanner.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Tokens.Space.s5, bottom = Tokens.Space.s5)
                .size(64.dp)
                .clip(CircleShape)
                .background(Semantic.colors.accent)
                .clickable(onClick = onScanTap),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Scan food",
                tint = Semantic.colors.inkInverse,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    editing?.let { item ->
        EditConsumptionDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { newGrams ->
                vm.updateGrams(item, newGrams)
                editing = null
            },
            onDelete = {
                vm.delete(item)
                editing = null
            }
        )
    }
}

@Composable
private fun CalorieSummary(totalKcal: Double, mealCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s4)
    ) {
        Overline(text = "Calories")
        Spacer(Modifier.height(Tokens.Space.s2))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = totalKcal.roundToInt().toString(),
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(Modifier.size(Tokens.Space.s2))
            Text(
                text = "kcal · $mealCount meal${if (mealCount == 1) "" else "s"}",
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun MealRow(item: ConsumptionWithFood, onTap: () -> Unit) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val accent = novaColor(item.food.novaClass)
    val tintAlpha = if (Semantic.colors.isDark) 0.16f else 0.14f
    val gramsText = item.log.gramsEaten?.let { "${it.roundToInt()} g" }
        ?: "${item.log.percentageEaten}%"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(accent.copy(alpha = tintAlpha))
            .clickable(onClick = onTap)
            .padding(Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImageThumb(item)
        Spacer(Modifier.size(Tokens.Space.s4))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.food.name,
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${timeFormatter.format(Date(item.log.eatenAt))}  ·  $gramsText  ·  ${(item.log.kcalConsumedSnapshot ?: 0.0).roundToInt()} kcal",
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
            .size(56.dp)
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

@Composable
private fun EditConsumptionDialog(
    item: ConsumptionWithFood,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit
) {
    val initial = item.log.gramsEaten?.toString() ?: item.log.percentageEaten.toString()
    var text by remember(item) { mutableStateOf(initial) }
    val grams = text.toDoubleOrNull()?.takeIf { it >= 0 }

    val perGram: Double? = item.food.kcalPer100g?.let { it / 100.0 }
        ?: run {
            val pu = item.food.kcalPerUnit
            val gpu = item.food.gramsPerUnit
            if (pu != null && gpu != null && gpu > 0) pu / gpu else null
        }
    val previewKcal = grams?.let { perGram?.let { pg -> (pg * it).roundToInt() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(Tokens.Radius.md))
                    .background(if (grams == null) Semantic.colors.surface3 else Semantic.colors.accent)
                    .clickable(enabled = grams != null) { grams?.let(onSave) }
                    .padding(horizontal = Tokens.Space.s4),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Save",
                    color = if (grams == null) Semantic.colors.inkLow else Semantic.colors.inkInverse,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(Tokens.Radius.md))
                        .background(Semantic.colors.error.copy(alpha = 0.15f))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = Tokens.Space.s4),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Delete", color = Semantic.colors.error, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(Tokens.Radius.md))
                        .background(Semantic.colors.surface2)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = Tokens.Space.s4),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = Semantic.colors.inkHigh)
                }
            }
        },
        title = { Text(item.food.name, color = Semantic.colors.inkHigh) },
        text = {
            Column {
                Text(
                    text = "How much did you actually eat?",
                    color = Semantic.colors.inkMid,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(Tokens.Space.s3))
                OutlinedTextField(
                    value = text,
                    onValueChange = { raw ->
                        text = raw.filter { it.isDigit() || it == '.' }.take(6)
                    },
                    label = { Text("Grams") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Semantic.colors.surface1,
                        unfocusedContainerColor = Semantic.colors.surface1,
                        focusedTextColor = Semantic.colors.inkHigh,
                        unfocusedTextColor = Semantic.colors.inkHigh,
                        focusedIndicatorColor = Semantic.colors.accent,
                        unfocusedIndicatorColor = Semantic.colors.surface3,
                        cursorColor = Semantic.colors.accent
                    )
                )
                Spacer(Modifier.height(Tokens.Space.s2))
                Text(
                    text = if (previewKcal != null) "$previewKcal kcal" else "Calorie info missing",
                    color = Semantic.colors.inkLow,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}
