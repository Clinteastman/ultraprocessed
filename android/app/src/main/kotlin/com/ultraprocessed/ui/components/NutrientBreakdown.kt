package com.ultraprocessed.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.ultraprocessed.analyzer.Nutrients
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens

/**
 * Macro + micro nutrient breakdown for the result screen.
 *
 * Renders the consumed amounts (i.e. the analyzer's per-100g values
 * already scaled by `factorOf100g`). Macros sit in a wrapped row of
 * chip-style cells; micros live in an expandable section underneath.
 *
 * Nothing is rendered for nutrients the analyzer didn't return - we'd
 * rather show nothing than show wrong numbers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutrientBreakdown(
    nutrientsPer100g: Nutrients,
    factorOf100g: Double,
    modifier: Modifier = Modifier
) {
    val consumed = remember(nutrientsPer100g, factorOf100g) {
        nutrientsPer100g.scaledBy(factorOf100g)
    }
    var expanded by remember { mutableStateOf(false) }

    val macros = listOfNotNull(
        consumed.proteinG?.let { "Protein" to formatGrams(it) },
        consumed.fatG?.let { "Fat" to formatGrams(it) },
        consumed.saturatedFatG?.let { "Sat fat" to formatGrams(it) },
        consumed.carbsG?.let { "Carbs" to formatGrams(it) },
        consumed.sugarG?.let { "Sugar" to formatGrams(it) },
        consumed.fiberG?.let { "Fiber" to formatGrams(it) },
        consumed.saltG?.let { "Salt" to formatGrams(it) }
    )
    val micros = micronutrientPairs(consumed)

    if (macros.isEmpty() && micros.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        if (macros.isNotEmpty()) {
            Overline(text = "Macros")
            Spacer(Modifier.height(Tokens.Space.s2))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)
            ) {
                macros.forEach { (label, value) -> NutrientCell(label, value) }
            }
        }

        if (micros.isNotEmpty()) {
            Spacer(Modifier.height(Tokens.Space.s4))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Overline(
                    text = if (expanded) "Hide micronutrients" else "Show micronutrients (${micros.size})",
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Semantic.colors.inkMid
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = Tokens.Space.s3)) {
                    micros.forEach { (label, value) -> MicroRow(label = label, value = value) }
                }
            }
        }
    }
}

@Composable
private fun NutrientCell(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Tokens.Radius.sm))
            .background(Semantic.colors.surface1)
            .padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.width(Tokens.Space.s2))
            Text(
                text = value,
                color = Semantic.colors.inkHigh,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MicroRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Tokens.Space.s1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = Semantic.colors.inkHigh,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun micronutrientPairs(n: Nutrients): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    n.calciumMg?.let { out += "Calcium" to formatMg(it) }
    n.ironMg?.let { out += "Iron" to formatMg(it) }
    n.potassiumMg?.let { out += "Potassium" to formatMg(it) }
    n.magnesiumMg?.let { out += "Magnesium" to formatMg(it) }
    n.zincMg?.let { out += "Zinc" to formatMg(it) }
    n.phosphorusMg?.let { out += "Phosphorus" to formatMg(it) }
    n.seleniumUg?.let { out += "Selenium" to formatUg(it) }
    n.iodineUg?.let { out += "Iodine" to formatUg(it) }
    n.copperMg?.let { out += "Copper" to formatMg(it) }
    n.manganeseMg?.let { out += "Manganese" to formatMg(it) }
    n.sodiumMg?.let { out += "Sodium" to formatMg(it) }
    n.cholesterolMg?.let { out += "Cholesterol" to formatMg(it) }
    n.omega3G?.let { out += "Omega-3" to formatGrams(it) }
    n.vitaminAUg?.let { out += "Vitamin A" to formatUg(it) }
    n.vitaminCMg?.let { out += "Vitamin C" to formatMg(it) }
    n.vitaminDUg?.let { out += "Vitamin D" to formatUg(it) }
    n.vitaminEMg?.let { out += "Vitamin E" to formatMg(it) }
    n.vitaminKUg?.let { out += "Vitamin K" to formatUg(it) }
    n.vitaminB1Mg?.let { out += "Vitamin B1 (thiamin)" to formatMg(it) }
    n.vitaminB2Mg?.let { out += "Vitamin B2 (riboflavin)" to formatMg(it) }
    n.vitaminB3Mg?.let { out += "Vitamin B3 (niacin)" to formatMg(it) }
    n.vitaminB6Mg?.let { out += "Vitamin B6" to formatMg(it) }
    n.vitaminB12Ug?.let { out += "Vitamin B12" to formatUg(it) }
    n.folateUg?.let { out += "Folate" to formatUg(it) }
    return out
}

private fun formatGrams(value: Double): String = when {
    value < 0.05 -> "<0.1 g"
    value < 1.0 -> String.format("%.1f g", value)
    else -> String.format("%.1f g", value)
}

private fun formatMg(value: Double): String = when {
    value < 0.5 -> String.format("%.2f mg", value)
    value < 10 -> String.format("%.1f mg", value)
    else -> "${value.toInt()} mg"
}

private fun formatUg(value: Double): String = when {
    value < 1.0 -> String.format("%.2f μg", value)
    value < 10 -> String.format("%.1f μg", value)
    else -> "${value.toInt()} μg"
}
