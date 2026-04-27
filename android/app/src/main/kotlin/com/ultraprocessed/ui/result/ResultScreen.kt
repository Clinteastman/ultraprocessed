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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultraprocessed.analyzer.FoodAlternative
import com.ultraprocessed.analyzer.FoodAnalysis
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.novaColor
import com.ultraprocessed.ui.MainViewModel
import com.ultraprocessed.ui.PendingResult
import com.ultraprocessed.ui.components.NovaDigit
import com.ultraprocessed.ui.components.NovaScale
import com.ultraprocessed.ui.components.NutrientBreakdown
import com.ultraprocessed.ui.components.Overline
import com.ultraprocessed.ui.components.PrimaryButton
import com.ultraprocessed.ui.components.SecondaryButton
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
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

    // Default: whole pack if known; else one serving; else 100g.
    var grams by remember(analysis) {
        mutableStateOf(analysis.packageGrams ?: analysis.gramsPerUnit ?: 100.0)
    }
    var customMode by remember(analysis) { mutableStateOf(false) }

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

        Overline(text = "How much did you eat?")
        Spacer(Modifier.height(Tokens.Space.s3))
        PortionPicker(
            analysis = analysis,
            grams = grams,
            customMode = customMode,
            onSelect = { g -> grams = g; customMode = false },
            onCustomTap = { customMode = true },
            onCustomGrams = { grams = it }
        )

        Spacer(Modifier.height(Tokens.Space.s5))
        KcalLine(grams = grams, analysis = analysis)

        analysis.nutrientsPer100g?.let { nutrients ->
            Spacer(Modifier.height(Tokens.Space.s5))
            NutrientBreakdown(
                nutrientsPer100g = nutrients,
                factorOf100g = grams / 100.0
            )
        }

        Spacer(Modifier.height(Tokens.Space.s6))
        PrimaryButton(
            label = "I ate it (${grams.roundToInt()} g)",
            onClick = {
                val toLog = current.copy(analysis = analysis)
                resultVm.logConsumption(toLog, grams) { onDone() }
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
private fun KcalLine(grams: Double, analysis: FoodAnalysis) {
    val kcal: Int? = run {
        val perGram: Double? = analysis.kcalPer100g?.let { it / 100.0 }
            ?: run {
                val perUnit = analysis.kcalPerUnit
                val gramsPerUnit = analysis.gramsPerUnit
                if (perUnit != null && gramsPerUnit != null && gramsPerUnit > 0)
                    perUnit / gramsPerUnit
                else null
            }
        perGram?.let { (it * grams).roundToInt() }
    }
    val basisHint = when {
        analysis.kcalPer100g != null -> "values per 100g, scaled to ${grams.roundToInt()} g"
        analysis.kcalPerUnit != null && analysis.gramsPerUnit != null ->
            "from per-serving (${analysis.gramsPerUnit!!.roundToInt()} g)"
        else -> "calorie info missing"
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        if (kcal != null) {
            Text(
                text = kcal.toString(),
                color = Semantic.colors.inkHigh,
                fontSize = 56.sp,
                style = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Start)
            )
            Spacer(Modifier.size(Tokens.Space.s3))
        }
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            if (kcal != null) {
                Text(
                    text = "kcal",
                    color = Semantic.colors.inkMid,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = basisHint,
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun novaLabel(novaClass: Int): String = when (novaClass) {
    1 -> "Unprocessed"
    2 -> "Culinary ingredient"
    3 -> "Processed"
    4 -> "Ultra-processed"
    else -> "Unknown"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortionPicker(
    analysis: FoodAnalysis,
    grams: Double,
    customMode: Boolean,
    onSelect: (Double) -> Unit,
    onCustomTap: () -> Unit,
    onCustomGrams: (Double) -> Unit
) {
    val presets = remember(analysis) {
        buildList<Pair<Double, String>> {
            add(100.0 to "100 g")
            analysis.gramsPerUnit
                ?.takeIf { it > 0 && it != 100.0 && it != analysis.packageGrams }
                ?.let {
                    val descr = analysis.servingDescription?.takeIf { d -> d.isNotBlank() }
                        ?: "1 serving"
                    add(it to "$descr · ${it.roundToInt()} g")
                }
            analysis.packageGrams
                ?.takeIf { it > 0 && it != 100.0 && it != analysis.gramsPerUnit }
                ?.let { add(it to "Whole pack · ${it.roundToInt()} g") }
        }.distinctBy { it.first }
    }

    var customText by remember(analysis, customMode) {
        mutableStateOf(if (customMode) grams.roundToInt().toString() else "")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)
        ) {
            presets.forEach { (g, label) ->
                PortionChip(
                    label = label,
                    selected = !customMode && (grams - g).absoluteValue < 0.5,
                    onClick = { onSelect(g) }
                )
            }
            PortionChip(
                label = "Custom",
                selected = customMode,
                onClick = onCustomTap
            )
        }
        if (customMode) {
            Spacer(Modifier.height(Tokens.Space.s3))
            OutlinedTextField(
                value = customText,
                onValueChange = { raw ->
                    customText = raw.filter { it.isDigit() || it == '.' }.take(6)
                    customText.toDoubleOrNull()?.takeIf { it >= 0 }?.let(onCustomGrams)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Grams eaten") },
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
        }
    }
}

@Composable
private fun PortionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(if (selected) Semantic.colors.accent else Semantic.colors.surface2)
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
