package com.ultraprocessed.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ultraprocessed.analyzer.Nutrients
import com.ultraprocessed.data.entities.ConsumptionLog
import com.ultraprocessed.data.entities.FodmapLevel
import com.ultraprocessed.data.entities.FoodEntry
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.UltraprocessedColors
import com.ultraprocessed.theme.novaColor
import com.ultraprocessed.ui.components.NovaPill
import com.ultraprocessed.ui.components.Overline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FoodDetailScreen(foodUuid: String, onBack: () -> Unit) {
    val vm: FoodDetailViewModel = viewModel(factory = FoodDetailViewModel.factory(foodUuid))
    val food by vm.food.collectAsState()
    val ingredients by vm.ingredients.collectAsState()
    val nutrients by vm.nutrients.collectAsState()
    val logs by vm.logs.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Semantic.colors.bg)) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = Tokens.Space.s5,
                end = Tokens.Space.s5,
                top = Tokens.Space.s5,
                bottom = Tokens.Space.s8
            ),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)
        ) {
            item("header") {
                HeaderRow(food = food, onBack = onBack)
            }

            food?.let { f ->
                item("rationale") { NovaRationaleCard(food = f) }

                item("ingredients") {
                    IngredientsCard(ingredients = ingredients)
                }

                if (nutrients != null && nutrients?.isEmpty() == false) {
                    item("nutrients") {
                        NutrientsCard(kcalPer100g = f.kcalPer100g, nutrients = nutrients!!)
                    }
                }

                item("history-header") {
                    Overline(
                        if (logs.isEmpty()) "No consumption yet"
                        else "Eaten ${logs.size} time${if (logs.size == 1) "" else "s"}"
                    )
                }
                items(items = logs.take(50), key = { it.clientUuid }) { log ->
                    ConsumptionRow(log = log)
                }
            }
        }
    }
}

// ---- Header / hero ----

@Composable
private fun HeaderRow(food: FoodEntry?, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Semantic.colors.surface1)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Semantic.colors.inkHigh
            )
        }
        Spacer(Modifier.width(Tokens.Space.s3))
        Column(modifier = Modifier.weight(1f)) {
            Overline(food?.brand?.uppercase() ?: "Food")
            Text(
                text = food?.name ?: "…",
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
        }
    }
    Spacer(Modifier.height(Tokens.Space.s3))
    if (food != null) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(Tokens.Radius.lg))
                    .background(Semantic.colors.surface1),
                contentAlignment = Alignment.Center
            ) {
                if (!food.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = food.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Tokens.Radius.lg))
                    )
                } else if (!food.imagePath.isNullOrBlank()) {
                    AsyncImage(
                        model = food.imagePath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Tokens.Radius.lg))
                    )
                } else {
                    Text(
                        text = "no image",
                        color = Semantic.colors.inkLow,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)
                ) {
                    NovaPill(novaClass = food.novaClass)
                    FodmapChip(level = food.fodmapLevel)
                }
                food.barcode?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "barcode $it",
                        color = Semantic.colors.inkLow,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    text = "via ${food.source.name.lowercase()}" +
                        if (food.confidence < 0.9) " · ${(food.confidence * 100).roundToInt()}% confidence" else "",
                    color = Semantic.colors.inkLow,
                    style = MaterialTheme.typography.labelSmall
                )
                food.kcalPer100g?.let {
                    Text(
                        text = "${it.roundToInt()} kcal / 100 g",
                        color = Semantic.colors.inkMid,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NovaRationaleCard(food: FoodEntry) {
    val accent = novaColor(food.novaClass)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(accent.copy(alpha = 0.12f))
            .padding(Tokens.Space.s5)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Overline("Why NOVA ${food.novaClass}")
            Text(
                text = food.novaRationale,
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun IngredientsCard(ingredients: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s5)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Overline("Ingredients")
            Text(
                text = if (ingredients.isEmpty()) "No ingredient list captured."
                else ingredients.joinToString(", "),
                color = if (ingredients.isEmpty()) Semantic.colors.inkMid else Semantic.colors.inkHigh,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ---- Nutrients per 100g ----

@Composable
private fun NutrientsCard(kcalPer100g: Double?, nutrients: Nutrients) {
    val rows = nutrientRows(nutrients)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s5)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Overline("Per 100 g")
            kcalPer100g?.let {
                Text(
                    text = "${it.roundToInt()} kcal",
                    color = Semantic.colors.inkHigh,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            rows.forEach { (label, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = label,
                        color = Semantic.colors.inkMid,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = value,
                        color = Semantic.colors.inkHigh,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun nutrientRows(n: Nutrients): List<Pair<String, String>> {
    fun row(label: String, amount: Double?, unit: String): Pair<String, String>? {
        if (amount == null || amount <= 0.0) return null
        val txt = if (amount < 10.0) "%.1f %s".format(amount, unit)
                  else "%d %s".format(amount.roundToInt(), unit)
        return label to txt
    }
    return listOfNotNull(
        row("Protein", n.proteinG, "g"),
        row("Fat", n.fatG, "g"),
        row("Sat fat", n.saturatedFatG, "g"),
        row("Carbs", n.carbsG, "g"),
        row("Sugar", n.sugarG, "g"),
        row("Fiber", n.fiberG, "g"),
        row("Salt", n.saltG, "g"),
        row("Sodium", n.sodiumMg, "mg"),
        row("Cholesterol", n.cholesterolMg, "mg"),
        row("Omega-3", n.omega3G, "g"),
        row("Calcium", n.calciumMg, "mg"),
        row("Iron", n.ironMg, "mg"),
        row("Potassium", n.potassiumMg, "mg"),
        row("Magnesium", n.magnesiumMg, "mg"),
        row("Zinc", n.zincMg, "mg"),
        row("Phosphorus", n.phosphorusMg, "mg"),
        row("Selenium", n.seleniumUg, "μg"),
        row("Iodine", n.iodineUg, "μg"),
        row("Copper", n.copperMg, "mg"),
        row("Manganese", n.manganeseMg, "mg"),
        row("Vitamin A", n.vitaminAUg, "μg"),
        row("Vitamin C", n.vitaminCMg, "mg"),
        row("Vitamin D", n.vitaminDUg, "μg"),
        row("Vitamin E", n.vitaminEMg, "mg"),
        row("Vitamin K", n.vitaminKUg, "μg"),
        row("Vitamin B1", n.vitaminB1Mg, "mg"),
        row("Vitamin B2", n.vitaminB2Mg, "mg"),
        row("Vitamin B3", n.vitaminB3Mg, "mg"),
        row("Vitamin B6", n.vitaminB6Mg, "mg"),
        row("Vitamin B12", n.vitaminB12Ug, "μg"),
        row("Folate", n.folateUg, "μg")
    )
}

// ---- Consumption log row ----

@Composable
private fun ConsumptionRow(log: ConsumptionLog) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s3)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatDayTime(log.eatenAt),
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = buildString {
                    log.gramsEaten?.let { append("${it.roundToInt()} g · ") }
                    append("${log.percentageEaten}%")
                    log.kcalConsumedSnapshot?.let { append(" · ${it.roundToInt()} kcal") }
                    log.locationLabel?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ---- FODMAP chip (mirrors leaderboard chip) ----

@Composable
private fun FodmapChip(level: FodmapLevel) {
    val (label, tone) = when (level) {
        FodmapLevel.HIGH -> "FODMAP H" to UltraprocessedColors.Nova4
        FodmapLevel.MODERATE -> "FODMAP M" to UltraprocessedColors.Nova3
        FodmapLevel.LOW -> "FODMAP L" to UltraprocessedColors.Nova1
        FodmapLevel.UNKNOWN -> return
    }
    Text(
        text = label,
        color = Semantic.colors.inkHigh,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(Tokens.Radius.sm))
            .background(tone.copy(alpha = 0.30f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// ---- Helpers ----

private val timeFormatter = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())

private fun formatDayTime(epochMs: Long): String =
    timeFormatter.format(Date(epochMs))
