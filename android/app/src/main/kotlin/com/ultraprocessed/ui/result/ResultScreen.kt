package com.ultraprocessed.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultraprocessed.analyzer.FoodAlternative
import com.ultraprocessed.analyzer.FoodAnalysis
import com.ultraprocessed.ui.components.NutrientBreakdown
import kotlinx.coroutines.launch
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.novaColor
import com.ultraprocessed.ui.MainViewModel
import com.ultraprocessed.ui.PendingResult
import com.ultraprocessed.ui.components.NovaDigit
import com.ultraprocessed.ui.components.NovaScale
import com.ultraprocessed.ui.components.Overline
import com.ultraprocessed.ui.components.PrimaryButton
import com.ultraprocessed.ui.components.SecondaryButton
import kotlin.math.roundToInt

@Composable
fun ResultScreen(
    mainVm: MainViewModel,
    onDone: () -> Unit
) {
    val pending by mainVm.pending.collectAsState()
    val resultVm: ResultViewModel = viewModel(factory = ResultViewModel.Factory)

    val current = pending
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize().background(Semantic.colors.bg))
        return
    }

    var percentage by remember { mutableStateOf(100f) }
    // Selected index: -1 = primary, 0..n-1 = alternative index, -2 = manual override.
    var selectedAltIndex by remember { mutableIntStateOf(-1) }
    var manualOverride by remember { mutableStateOf<FoodAnalysis?>(null) }
    var manualEntryActive by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var manualLoading by remember { mutableStateOf(false) }
    var manualError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val analysis: FoodAnalysis = current.analysis.let { primary ->
        when {
            selectedAltIndex == -2 && manualOverride != null -> manualOverride!!
            selectedAltIndex < 0 || selectedAltIndex >= primary.alternatives.size -> primary
            else -> {
                val alt = primary.alternatives[selectedAltIndex]
                primary.copy(
                    name = alt.name,
                    novaClass = alt.novaClass,
                    novaRationale = "You picked ${alt.name} as the actual food.",
                    kcalPer100g = alt.kcalPer100g ?: primary.kcalPer100g,
                    kcalPerUnit = alt.kcalPerUnit ?: primary.kcalPerUnit
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Semantic.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.s5)
            .padding(top = Tokens.Space.s7, bottom = Tokens.Space.s7)
    ) {
        BackRow(onBack = onDone)

        Spacer(Modifier.height(Tokens.Space.s6))
        Text(
            text = analysis.name,
            color = Semantic.colors.inkHigh,
            style = MaterialTheme.typography.displaySmall
        )
        analysis.brand?.let { brand ->
            Spacer(Modifier.height(Tokens.Space.s1))
            Text(
                text = brand,
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(Modifier.height(Tokens.Space.s6))
        Row(verticalAlignment = Alignment.CenterVertically) {
            NovaDigit(novaClass = analysis.novaClass, sizeDp = 132)
            Spacer(Modifier.size(Tokens.Space.s5))
            Column {
                Overline(text = "NOVA", color = novaColor(analysis.novaClass))
                Spacer(Modifier.height(Tokens.Space.s1))
                Text(
                    text = novaLabel(analysis.novaClass),
                    color = Semantic.colors.inkHigh,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Spacer(Modifier.height(Tokens.Space.s5))
        NovaScale(novaClass = analysis.novaClass)

        Spacer(Modifier.height(Tokens.Space.s5))
        Text(
            text = analysis.novaRationale,
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(Tokens.Space.s5))
        if (manualEntryActive) {
            ManualEntry(
                text = manualText,
                onTextChange = { manualText = it; manualError = null },
                loading = manualLoading,
                error = manualError,
                onSubmit = submit@{
                    if (manualText.isBlank() || manualLoading) return@submit
                    manualLoading = true
                    manualError = null
                    val query = manualText
                    coroutineScope.launch {
                        resultVm.classify(query).fold(
                            onSuccess = { result ->
                                manualOverride = result
                                selectedAltIndex = -2
                                manualEntryActive = false
                                manualText = ""
                            },
                            onFailure = { err ->
                                manualError = err.message ?: "Lookup failed."
                            }
                        )
                        manualLoading = false
                    }
                },
                onCancel = {
                    manualEntryActive = false
                    manualText = ""
                    manualError = null
                }
            )
        } else {
            AlternativesPicker(
                primaryName = current.analysis.name,
                alternatives = current.analysis.alternatives,
                manualOverride = manualOverride,
                selectedIndex = selectedAltIndex,
                onSelect = { idx -> selectedAltIndex = idx },
                onAddOther = { manualEntryActive = true }
            )
        }

        if (analysis.ingredients.isNotEmpty()) {
            Spacer(Modifier.height(Tokens.Space.s5))
            Overline(text = "Notable ingredients")
            Spacer(Modifier.height(Tokens.Space.s2))
            Text(
                text = analysis.ingredients.take(8).joinToString(", "),
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(Tokens.Space.s6))
        Divider()
        Spacer(Modifier.height(Tokens.Space.s5))

        KcalLine(analysis.kcalPer100g, analysis.kcalPerUnit, percentage.toInt())

        analysis.nutrientsPer100g?.let { nutrients ->
            val factor = consumedFactorOf100g(analysis, percentage.toInt())
            if (factor != null) {
                Spacer(Modifier.height(Tokens.Space.s5))
                NutrientBreakdown(
                    nutrientsPer100g = nutrients,
                    factorOf100g = factor
                )
            }
        }

        Spacer(Modifier.height(Tokens.Space.s6))
        Overline(text = "How much did you eat?")
        Spacer(Modifier.height(Tokens.Space.s3))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = percentage,
                onValueChange = { percentage = it },
                valueRange = 0f..100f,
                steps = 19, // 5% steps
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Semantic.colors.accent,
                    activeTrackColor = Semantic.colors.accent,
                    inactiveTrackColor = Semantic.colors.surface3
                )
            )
            Spacer(Modifier.size(Tokens.Space.s4))
            Text(
                text = "${percentage.roundToInt()}%",
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(Tokens.Space.s6))
        PrimaryButton(
            label = "I ate it",
            onClick = {
                val toLog = current.copy(analysis = analysis)
                resultVm.logConsumption(toLog, percentage.roundToInt()) { onDone() }
            }
        )
        Spacer(Modifier.height(Tokens.Space.s3))
        SecondaryButton(label = "Didn't eat it", onClick = onDone)
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
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
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Semantic.colors.surface3)
    )
}

@Composable
private fun KcalLine(kcalPer100g: Double?, kcalPerUnit: Double?, pct: Int) {
    val (number, unit) = when {
        kcalPerUnit != null -> {
            val n = (kcalPerUnit * pct / 100.0).roundToInt()
            "$n" to "kcal at $pct%"
        }
        kcalPer100g != null -> {
            val n = (kcalPer100g * pct / 100.0).roundToInt()
            "$n" to "kcal at $pct% of 100g"
        }
        else -> "" to "calories unknown"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        if (number.isNotEmpty()) {
            Text(
                text = number,
                color = Semantic.colors.inkHigh,
                fontSize = 56.sp,
                style = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Start)
            )
            Spacer(Modifier.size(Tokens.Space.s3))
        }
        Text(
            text = unit,
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

private fun novaLabel(novaClass: Int): String = when (novaClass) {
    1 -> "Unprocessed"
    2 -> "Culinary ingredient"
    3 -> "Processed"
    4 -> "Ultra-processed"
    else -> "Unknown"
}

/**
 * Mirrors [com.ultraprocessed.ui.result.ResultViewModel.consumedFactorOf100g]
 * so the nutrient breakdown reflects the slider in real time without a
 * round-trip to the ViewModel.
 */
private fun consumedFactorOf100g(analysis: FoodAnalysis, pct: Int): Double? {
    val pctFactor = pct / 100.0
    val kcal100 = analysis.kcalPer100g
    val kcalUnit = analysis.kcalPerUnit
    val gramsConsumed = when {
        kcal100 != null && kcal100 > 0 && kcalUnit != null -> (kcalUnit / kcal100) * 100.0 * pctFactor
        kcalUnit != null || kcal100 != null -> 100.0 * pctFactor
        else -> return null
    }
    return gramsConsumed / 100.0
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlternativesPicker(
    primaryName: String,
    alternatives: List<FoodAlternative>,
    manualOverride: FoodAnalysis?,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAddOther: () -> Unit
) {
    Column {
        Overline(text = "Not right?  Pick another or type your own.")
        Spacer(Modifier.height(Tokens.Space.s2))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)
        ) {
            FoodChip(
                label = primaryName,
                selected = selectedIndex == -1,
                onClick = { onSelect(-1) }
            )
            alternatives.forEachIndexed { idx, alt ->
                FoodChip(
                    label = alt.name,
                    selected = selectedIndex == idx,
                    onClick = { onSelect(idx) }
                )
            }
            if (manualOverride != null) {
                FoodChip(
                    label = manualOverride.name,
                    selected = selectedIndex == -2,
                    onClick = { onSelect(-2) }
                )
            }
            FoodChip(
                label = "+ Other",
                selected = false,
                onClick = onAddOther
            )
        }
    }
}

@Composable
private fun ManualEntry(
    text: String,
    onTextChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Column {
        Overline(text = "What is it?")
        Spacer(Modifier.height(Tokens.Space.s2))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !loading,
            placeholder = { Text("e.g. mango, tesco hummus, latte", color = Semantic.colors.inkLow) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Semantic.colors.surface1,
                unfocusedContainerColor = Semantic.colors.surface1,
                disabledContainerColor = Semantic.colors.surface1,
                focusedTextColor = Semantic.colors.inkHigh,
                unfocusedTextColor = Semantic.colors.inkHigh,
                focusedIndicatorColor = Semantic.colors.accent,
                unfocusedIndicatorColor = Semantic.colors.surface3,
                cursorColor = Semantic.colors.accent
            )
        )
        if (error != null) {
            Spacer(Modifier.height(Tokens.Space.s2))
            Text(
                text = error,
                color = Semantic.colors.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(Tokens.Space.s3))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(Tokens.Radius.md))
                    .background(Semantic.colors.surface2)
                    .clickable(enabled = !loading, onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", color = Semantic.colors.inkHigh, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(Tokens.Radius.md))
                    .background(if (text.isBlank() || loading) Semantic.colors.surface3 else Semantic.colors.accent)
                    .clickable(enabled = !loading && text.isNotBlank(), onClick = onSubmit),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Semantic.colors.inkInverse,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        "Look it up",
                        color = if (text.isBlank()) Semantic.colors.inkLow else Semantic.colors.inkInverse,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(
                if (selected) Semantic.colors.accent
                else Semantic.colors.surface2
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2)
    ) {
        Text(
            text = label,
            color = if (selected) Semantic.colors.inkInverse else Semantic.colors.inkHigh,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
