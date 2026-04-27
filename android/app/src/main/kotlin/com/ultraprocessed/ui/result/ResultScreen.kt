package com.ultraprocessed.ui.result

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.novaColor
import com.ultraprocessed.ui.MainViewModel
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
    val analysis = current.analysis

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
                resultVm.logConsumption(current, percentage.roundToInt()) { onDone() }
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
