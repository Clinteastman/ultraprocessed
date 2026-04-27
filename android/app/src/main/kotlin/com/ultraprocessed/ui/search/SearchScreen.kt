package com.ultraprocessed.ui.search

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
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
import com.ultraprocessed.data.entities.FoodEntry
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.novaColor
import com.ultraprocessed.ui.components.NovaPill
import com.ultraprocessed.ui.components.Overline
import kotlin.math.roundToInt

/**
 * Plain-text food search. Used to log a meal you forgot to scan/photo
 * earlier in the day. The input runs against the local FoodEntry cache
 * (recently-scanned items, OFF lookups, prior manual entries), and a
 * "create with LLM" CTA at the bottom classifies a fresh string into a
 * new FoodEntry plus a ConsumptionLog at the current time.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onLogged: () -> Unit
) {
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val classifying by vm.classifying.collectAsState()
    val error by vm.error.collectAsState()

    var picking by remember { mutableStateOf<FoodEntry?>(null) }
    var classifyText by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Semantic.colors.bg)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Tokens.Space.s5)) {
            Spacer(Modifier.height(Tokens.Space.s7))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clickable(onClick = onBack),
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
                    text = "Add by name",
                    style = MaterialTheme.typography.displaySmall,
                    color = Semantic.colors.inkHigh
                )
            }

            Spacer(Modifier.height(Tokens.Space.s4))
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. mango, hummus, latte") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Semantic.colors.inkMid
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
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

            error?.let {
                Spacer(Modifier.height(Tokens.Space.s3))
                Text(
                    text = it,
                    color = Semantic.colors.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(Tokens.Space.s4))
            Overline(
                text = if (query.isBlank()) "Recent foods" else "Matches"
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = Tokens.Space.s3, bottom = Tokens.Space.s5),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)
            ) {
                items(results, key = { it.clientUuid }) { food ->
                    FoodResultRow(food = food, onTap = { picking = food })
                }

                if (query.isNotBlank()) {
                    item("classify-cta") {
                        Spacer(Modifier.height(Tokens.Space.s3))
                        ClassifyCta(
                            text = query.trim(),
                            classifying = classifying,
                            onTap = { classifyText = query.trim() }
                        )
                    }
                }

                if (results.isEmpty() && query.isBlank()) {
                    item("hint") {
                        Spacer(Modifier.height(Tokens.Space.s4))
                        Text(
                            text = "Type a food name to find something you've already scanned, or to add it as a new entry classified with your LLM provider.",
                            color = Semantic.colors.inkMid,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    picking?.let { food ->
        AmountDialog(
            initialName = food.name,
            initialGrams = defaultGramsFor(food),
            onCancel = { picking = null },
            onConfirm = { grams ->
                vm.logExisting(food, grams) {
                    picking = null
                    onLogged()
                }
            }
        )
    }

    classifyText?.let { text ->
        AmountDialog(
            initialName = text,
            initialGrams = 100.0,
            classifying = classifying,
            cta = "Classify and log",
            onCancel = { classifyText = null },
            onConfirm = { grams ->
                vm.classifyAndLog(text, grams) {
                    classifyText = null
                    onLogged()
                }
            }
        )
    }
}

private fun defaultGramsFor(food: FoodEntry): Double {
    food.gramsPerUnit?.let { if (it > 0) return it }
    food.packageGrams?.let { if (it > 0) return it }
    return 100.0
}

@Composable
private fun FoodResultRow(food: FoodEntry, onTap: () -> Unit) {
    val accent = novaColor(food.novaClass)
    val tintAlpha = if (Semantic.colors.isDark) 0.16f else 0.14f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(accent.copy(alpha = tintAlpha))
            .clickable(onClick = onTap)
            .padding(Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(Semantic.colors.surface2)
        ) {
            val source = food.imagePath ?: food.imageUrl
            if (!source.isNullOrBlank()) {
                AsyncImage(
                    model = source,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.size(Tokens.Space.s3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = food.name,
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            val sub = listOfNotNull(
                food.brand,
                food.kcalPer100g?.let { "${it.roundToInt()} kcal/100g" }
            ).joinToString("  ·  ")
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sub,
                    color = Semantic.colors.inkMid,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.size(Tokens.Space.s2))
        NovaPill(novaClass = food.novaClass)
    }
}

@Composable
private fun ClassifyCta(
    text: String,
    classifying: Boolean,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Semantic.colors.surface2)
            .clickable(enabled = !classifying, onClick = onTap)
            .padding(Tokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Semantic.colors.accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Semantic.colors.accent
            )
        }
        Spacer(Modifier.size(Tokens.Space.s3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (classifying) "Classifying..." else "Add \"$text\"",
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Classifies with your provider, then logs it now.",
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AmountDialog(
    initialName: String,
    initialGrams: Double,
    classifying: Boolean = false,
    cta: String = "Log it",
    onCancel: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var text by remember(initialName) {
        mutableStateOf(initialGrams.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        })
    }
    val grams = text.toDoubleOrNull()?.takeIf { it >= 0 }
    AlertDialog(
        onDismissRequest = { if (!classifying) onCancel() },
        confirmButton = {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(Tokens.Radius.md))
                    .background(
                        if (grams != null && !classifying) Semantic.colors.accent
                        else Semantic.colors.surface3
                    )
                    .clickable(enabled = grams != null && !classifying) {
                        grams?.let(onConfirm)
                    }
                    .padding(horizontal = Tokens.Space.s4),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (classifying) "Working..." else cta,
                    color = if (grams != null && !classifying) Semantic.colors.inkInverse
                    else Semantic.colors.inkLow,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(Tokens.Radius.md))
                    .background(Semantic.colors.surface2)
                    .clickable(enabled = !classifying, onClick = onCancel)
                    .padding(horizontal = Tokens.Space.s4),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", color = Semantic.colors.inkHigh)
            }
        },
        title = { Text(initialName, color = Semantic.colors.inkHigh) },
        text = {
            Column {
                Text(
                    text = "How much did you eat? Logs at the current time.",
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
                    enabled = !classifying,
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
    )
}
