package com.ultraprocessed.ui.gut

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultraprocessed.data.entities.BowelEvent
import com.ultraprocessed.data.entities.FodmapLevel
import com.ultraprocessed.data.entities.SymptomEvent
import com.ultraprocessed.data.entities.SymptomKind
import com.ultraprocessed.sync.TriggerReportDto
import com.ultraprocessed.sync.TriggerScoreDto
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.theme.UltraprocessedColors
import com.ultraprocessed.ui.components.InfoIcon
import com.ultraprocessed.ui.components.Overline
import com.ultraprocessed.ui.components.PrimaryButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GutScreen(
    onBack: () -> Unit,
    onOpenFood: (String) -> Unit = {}
) {
    val vm: GutViewModel = viewModel(factory = GutViewModel.Factory)
    val todayBowels by vm.todayBowels.collectAsState()
    val todaySymptoms by vm.todaySymptoms.collectAsState()
    val todayCheckin by vm.todayCheckin.collectAsState()
    val recentBowels by vm.recentBowels.collectAsState()
    val recentSymptoms by vm.recentSymptoms.collectAsState()
    val triggers by vm.triggers.collectAsState()
    val toast by vm.toast.collectAsState()

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(1_500)
            vm.consumeToast()
        }
    }

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
                    Column {
                        Overline("Gut tracker")
                        Text(
                            text = "Today",
                            color = Semantic.colors.inkHigh,
                            style = MaterialTheme.typography.displaySmall
                        )
                    }
                }
            }

            item("today-summary") {
                TodaySummaryCard(
                    bowelCount = todayBowels.size,
                    flares = todayBowels.count { it.bristol in listOf(1, 2, 6, 7) || it.urgency >= 2 || it.pain >= 2 },
                    symptomCount = todaySymptoms.size,
                    bloating = todayCheckin?.bloatingOverall ?: 0,
                    heartburn = todayCheckin?.heartburnOverall ?: 0,
                    gutScore = todayCheckin?.gutScore
                )
            }

            item("trigger-leaderboard") {
                TriggerLeaderboardCard(
                    state = triggers,
                    onRetry = vm::refreshTriggers,
                    onOpenFood = onOpenFood
                )
            }

            item("section-events") {
                SectionHeader(
                    title = "As it happens",
                    subtitle = "Tap when you visit the bathroom or feel a symptom — each one gets timestamped now."
                )
            }

            item("bowel-card") {
                BowelLogCard(onLog = vm::logBowel)
            }

            item("symptom-card") {
                SymptomLogCard(onLog = vm::logSymptom)
            }

            item("section-endofday") {
                SectionHeader(
                    title = "End of day",
                    subtitle = "One rollup of how today felt overall. Update it whenever — only the latest version is saved."
                )
            }

            item("checkin-card") {
                DailyCheckinCard(
                    initialBloating = todayCheckin?.bloatingOverall ?: 0,
                    initialHeartburn = todayCheckin?.heartburnOverall ?: 0,
                    initialGutScore = todayCheckin?.gutScore,
                    initialStress = todayCheckin?.stress ?: 0,
                    initialPeriod = todayCheckin?.period ?: false,
                    initialNotes = todayCheckin?.notes ?: "",
                    isUpdate = todayCheckin != null,
                    onSave = vm::saveCheckin
                )
            }

            item("section-history") {
                SectionHeader(
                    title = "History",
                    subtitle = "Recent bathroom visits and symptoms — tap × to remove a mis-tap."
                )
            }

            item("recent-bowel-header") {
                Overline("Recent bowel events")
            }
            if (recentBowels.isEmpty()) {
                item("empty-bowel") {
                    Text(
                        text = "Nothing logged yet.",
                        color = Semantic.colors.inkMid,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(
                    items = recentBowels.take(15),
                    key = { it.clientUuid }
                ) { event ->
                    BowelRow(event = event, onDelete = { vm.deleteBowel(event.clientUuid) })
                }
            }

            item("recent-sym-header") {
                Overline("Recent symptoms")
            }
            if (recentSymptoms.isEmpty()) {
                item("empty-sym") {
                    Text(
                        text = "Nothing logged yet.",
                        color = Semantic.colors.inkMid,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(
                    items = recentSymptoms.take(15),
                    key = { it.clientUuid }
                ) { event ->
                    SymptomRow(event = event, onDelete = { vm.deleteSymptom(event.clientUuid) })
                }
            }
        }

        if (toast != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Tokens.Space.s5)
                    .clip(RoundedCornerShape(Tokens.Radius.lg))
                    .background(Semantic.colors.surface3)
                    .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2)
            ) {
                Text(
                    text = toast!!,
                    color = Semantic.colors.inkHigh,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ---- Component pieces ----

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(top = Tokens.Space.s3, start = Tokens.Space.s2)
    ) {
        Text(
            text = title.uppercase(),
            color = Semantic.colors.inkHigh,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TodaySummaryCard(
    bowelCount: Int,
    flares: Int,
    symptomCount: Int,
    bloating: Int,
    heartburn: Int,
    gutScore: Int?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s5)
    ) {
        Column {
            Overline("Today")
            Spacer(Modifier.height(Tokens.Space.s2))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                if (gutScore != null) {
                    Text(
                        text = gutScore.toString(),
                        color = gutScoreColor(gutScore),
                        style = MaterialTheme.typography.displayLarge
                    )
                    Text(
                        text = "/ 10",
                        color = Semantic.colors.inkLow,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Text(
                        text = "—",
                        color = Semantic.colors.inkMid,
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(
                        text = "no check-in yet",
                        color = Semantic.colors.inkLow,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(Tokens.Space.s3))
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                StatColumn("Bowel", "$bowelCount visit${if (bowelCount == 1) "" else "s"}${if (flares > 0) " · $flares flare${if (flares == 1) "" else "s"}" else ""}")
                StatColumn(
                    "Symptoms",
                    buildString {
                        val parts = mutableListOf<String>()
                        if (bloating > 0) parts += "bloat $bloating/3"
                        if (heartburn > 0) parts += "heartburn $heartburn/3"
                        if (symptomCount > 0) parts += "$symptomCount event${if (symptomCount == 1) "" else "s"}"
                        if (parts.isEmpty()) append("nothing flagged") else append(parts.joinToString(" · "))
                    }
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            color = Semantic.colors.inkLow,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = Semantic.colors.inkHigh,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun BowelLogCard(
    onLog: (Int, Int, Int, Int, Boolean, Boolean, String?) -> Unit
) {
    var bristol by remember { mutableStateOf<Int?>(null) }
    var urgency by remember { mutableStateOf(0) }
    var pain by remember { mutableStateOf(0) }
    val completeness = 2
    var notes by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s5)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Overline("Bathroom visit")
            BristolPicker(value = bristol, onChange = { bristol = it })
            SeverityRow(label = "Urgency", value = urgency, onChange = { urgency = it })
            SeverityRow(label = "Pain", value = pain, onChange = { pain = it })
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Notes (optional)", color = Semantic.colors.inkLow) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Semantic.colors.surface2,
                    unfocusedContainerColor = Semantic.colors.surface2,
                    focusedTextColor = Semantic.colors.inkHigh,
                    unfocusedTextColor = Semantic.colors.inkHigh
                )
            )
            PrimaryButton(
                label = if (bristol == null) "Pick a Bristol score" else "Log bowel event",
                onClick = {
                    val b = bristol ?: return@PrimaryButton
                    onLog(b, urgency, pain, completeness, false, false, notes)
                    bristol = null
                    urgency = 0
                    pain = 0
                    notes = ""
                },
                enabled = bristol != null
            )
        }
    }
}

@Composable
private fun SymptomLogCard(
    onLog: (SymptomKind, Int, String?, String?) -> Unit
) {
    var kind by remember { mutableStateOf(SymptomKind.BLOATING) }
    var severity by remember { mutableStateOf(1) }
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s5)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Overline("Symptom")
                InfoIcon(title = GutExplainers.SYMPTOMS_TITLE, body = GutExplainers.SYMPTOMS_BODY)
            }
            SymptomGrid(selected = kind, onSelect = { kind = it })
            SeverityRow(label = "Severity", value = severity, onChange = { severity = it }, allowZero = false)
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                placeholder = { Text("Where? (optional)", color = Semantic.colors.inkLow) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Semantic.colors.surface2,
                    unfocusedContainerColor = Semantic.colors.surface2,
                    focusedTextColor = Semantic.colors.inkHigh,
                    unfocusedTextColor = Semantic.colors.inkHigh
                )
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Notes (optional)", color = Semantic.colors.inkLow) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Semantic.colors.surface2,
                    unfocusedContainerColor = Semantic.colors.surface2,
                    focusedTextColor = Semantic.colors.inkHigh,
                    unfocusedTextColor = Semantic.colors.inkHigh
                )
            )
            PrimaryButton(
                label = "Log ${kind.name.lowercase()}",
                onClick = {
                    onLog(kind, severity, location, notes)
                    severity = 1
                    location = ""
                    notes = ""
                }
            )
        }
    }
}

@Composable
private fun DailyCheckinCard(
    initialBloating: Int,
    initialHeartburn: Int,
    initialGutScore: Int?,
    initialStress: Int,
    initialPeriod: Boolean,
    initialNotes: String,
    isUpdate: Boolean,
    onSave: (Int, Int, Int?, Int?, Boolean, String?) -> Unit
) {
    var bloating by remember(initialBloating) { mutableStateOf(initialBloating) }
    var heartburn by remember(initialHeartburn) { mutableStateOf(initialHeartburn) }
    var gutScore by remember(initialGutScore) { mutableStateOf(initialGutScore) }
    var stress by remember(initialStress) { mutableStateOf(initialStress) }
    var period by remember(initialPeriod) { mutableStateOf(initialPeriod) }
    var notes by remember(initialNotes) { mutableStateOf(initialNotes) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s5)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Overline(if (isUpdate) "Update today's check-in" else "Daily check-in")
            SeverityRow(label = "Bloating today", value = bloating, onChange = { bloating = it })
            SeverityRow(label = "Heartburn today", value = heartburn, onChange = { heartburn = it })
            SeverityRow(label = "Stress today", value = stress, onChange = { stress = it })
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "GUT SCORE (1 worst, 10 best)",
                        color = Semantic.colors.inkLow,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                    InfoIcon(title = GutExplainers.GUT_SCORE_TITLE, body = GutExplainers.GUT_SCORE_BODY)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (n in 1..10) {
                        val selected = gutScore == n
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(Tokens.Radius.sm))
                                .background(
                                    when {
                                        n <= 3 -> UltraprocessedColors.Nova4.copy(alpha = 0.3f)
                                        n <= 6 -> UltraprocessedColors.Nova3.copy(alpha = 0.4f)
                                        else -> UltraprocessedColors.Nova1.copy(alpha = 0.4f)
                                    }
                                )
                                .then(
                                    if (selected) Modifier.border(2.dp, Semantic.colors.inkHigh, RoundedCornerShape(Tokens.Radius.sm))
                                    else Modifier
                                )
                                .clickable { gutScore = n },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = n.toString(),
                                color = Semantic.colors.inkHigh,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckboxLabeled(label = "Period today", checked = period, onChange = { period = it })
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Notes (sleep, travel, stress source...)", color = Semantic.colors.inkLow) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Semantic.colors.surface2,
                    unfocusedContainerColor = Semantic.colors.surface2,
                    focusedTextColor = Semantic.colors.inkHigh,
                    unfocusedTextColor = Semantic.colors.inkHigh
                )
            )
            PrimaryButton(
                label = if (isUpdate) "Update check-in" else "Save check-in",
                onClick = { onSave(bloating, heartburn, gutScore, stress, period, notes) }
            )
        }
    }
}

// ---- Pickers ----

@Composable
private fun BristolPicker(value: Int?, onChange: (Int) -> Unit) {
    val tones: List<Color> = listOf(
        UltraprocessedColors.Nova4.copy(alpha = 0.30f),
        UltraprocessedColors.Nova3.copy(alpha = 0.40f),
        UltraprocessedColors.Nova2.copy(alpha = 0.40f),
        UltraprocessedColors.Nova1.copy(alpha = 0.40f),
        UltraprocessedColors.Nova2.copy(alpha = 0.40f),
        UltraprocessedColors.Nova3.copy(alpha = 0.40f),
        UltraprocessedColors.Nova4.copy(alpha = 0.30f)
    )
    val tips = listOf("Pellets", "Lumpy", "Cracked", "Smooth", "Soft", "Mushy", "Liquid")
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "BRISTOL STOOL SCALE",
                    color = Semantic.colors.inkLow,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                InfoIcon(title = GutExplainers.BRISTOL_TITLE, body = GutExplainers.BRISTOL_BODY)
            }
            Text(
                text = if (value != null) tips[value - 1] else "",
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1..7) {
                val selected = value == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(Tokens.Radius.md))
                        .background(tones[i - 1])
                        .then(
                            if (selected) Modifier.border(2.dp, Semantic.colors.inkHigh, RoundedCornerShape(Tokens.Radius.md))
                            else Modifier
                        )
                        .clickable { onChange(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = i.toString(),
                        color = Semantic.colors.inkHigh,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SeverityRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    allowZero: Boolean = true,
    modifier: Modifier = Modifier
) {
    val labels = listOf("None", "Mild", "Mod", "Severe")
    val tones: List<Color> = listOf(
        Semantic.colors.surface2,
        UltraprocessedColors.Nova1.copy(alpha = 0.40f),
        UltraprocessedColors.Nova3.copy(alpha = 0.40f),
        UltraprocessedColors.Nova4.copy(alpha = 0.40f)
    )
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label.uppercase(),
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            InfoIcon(title = GutExplainers.SEVERITY_TITLE, body = GutExplainers.SEVERITY_BODY)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0..3) {
                if (!allowZero && i == 0) continue
                val selected = value == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(Tokens.Radius.md))
                        .background(tones[i])
                        .then(
                            if (selected) Modifier.border(2.dp, Semantic.colors.inkHigh, RoundedCornerShape(Tokens.Radius.md))
                            else Modifier
                        )
                        .clickable { onChange(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labels[i],
                        color = if (i == 0) Semantic.colors.inkMid else Semantic.colors.inkHigh,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SymptomGrid(selected: SymptomKind, onSelect: (SymptomKind) -> Unit) {
    // 3 columns × 3 rows (8 items, last cell empty). At 3 columns each cell
    // is wide enough to render the longest single-word label ("Heartburn",
    // "Cramping") in one line at bodySmall.
    val pairs = listOf(
        SymptomKind.BLOATING to "Bloating",
        SymptomKind.HEARTBURN to "Heartburn",
        SymptomKind.CRAMPING to "Cramping",
        SymptomKind.GAS to "Gas",
        SymptomKind.PAIN to "Pain",
        SymptomKind.NAUSEA to "Nausea",
        SymptomKind.URGENCY to "Urgency",
        SymptomKind.FATIGUE to "Fatigue"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pairs.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (kind, label) ->
                    val isSelected = selected == kind
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(Tokens.Radius.md))
                            .background(
                                if (isSelected) Semantic.colors.accent.copy(alpha = 0.18f)
                                else Semantic.colors.surface2
                            )
                            .then(
                                if (isSelected) Modifier.border(2.dp, Semantic.colors.accent, RoundedCornerShape(Tokens.Radius.md))
                                else Modifier
                            )
                            .clickable { onSelect(kind) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = Semantic.colors.inkHigh,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                }
                // Pad the trailing row so cells stay the same width across rows.
                repeat(3 - row.size) {
                    Box(modifier = Modifier.weight(1f).height(44.dp))
                }
            }
        }
    }
}

@Composable
private fun CheckboxLabeled(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Semantic.colors.accent,
                uncheckedColor = Semantic.colors.inkMid,
                checkmarkColor = Semantic.colors.inkInverse
            )
        )
        Text(
            text = label,
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ---- Recent rows ----

@Composable
private fun BowelRow(event: BowelEvent, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s3)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(bristolColor(event.bristol).copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = event.bristol.toString(),
                color = Semantic.colors.inkHigh,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(Tokens.Space.s3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append("Bristol ").append(event.bristol)
                    if (event.urgency >= 2) append(" · urgent")
                    if (event.pain >= 2) append(" · painful")
                    if (event.blood) append(" · blood")
                    if (event.mucus) append(" · mucus")
                },
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatDayTime(event.occurredAt) + (event.notes?.let { " · $it" } ?: ""),
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "×",
            color = Semantic.colors.inkLow,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = Tokens.Space.s2)
                .clickable(onClick = onDelete)
        )
    }
}

@Composable
private fun SymptomRow(event: SymptomEvent, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s3)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(severityColor(event.severity).copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = event.kind.name.take(3),
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(Tokens.Space.s3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${event.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · ${severityLabel(event.severity)}" +
                    (event.location?.let { " · $it" } ?: ""),
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatDayTime(event.occurredAt) + (event.notes?.let { " · $it" } ?: ""),
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "×",
            color = Semantic.colors.inkLow,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = Tokens.Space.s2)
                .clickable(onClick = onDelete)
        )
    }
}

// ---- Helpers ----

private fun bristolColor(bristol: Int): Color = when (bristol) {
    1, 2, 7 -> UltraprocessedColors.Nova4
    3, 5 -> UltraprocessedColors.Nova3
    6 -> UltraprocessedColors.Nova4
    else -> UltraprocessedColors.Nova1
}

private fun severityColor(severity: Int): Color = when (severity) {
    0 -> UltraprocessedColors.Nova1
    1 -> UltraprocessedColors.Nova2
    2 -> UltraprocessedColors.Nova3
    else -> UltraprocessedColors.Nova4
}

private fun severityLabel(severity: Int): String = when (severity) {
    0 -> "none"
    1 -> "mild"
    2 -> "moderate"
    else -> "severe"
}

private fun gutScoreColor(score: Int): Color = when {
    score <= 3 -> UltraprocessedColors.Nova4
    score <= 6 -> UltraprocessedColors.Nova3
    else -> UltraprocessedColors.Nova1
}

private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayTimeFormatter = SimpleDateFormat("EEE HH:mm", Locale.getDefault())

private fun formatDayTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        .format(Date(now)) == SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        .format(Date(epochMs))
    return if (sameDay) timeFormatter.format(Date(epochMs))
    else dayTimeFormatter.format(Date(epochMs))
}

// ---- Trigger leaderboard ----

@Composable
private fun TriggerLeaderboardCard(
    state: TriggersUi,
    onRetry: () -> Unit,
    onOpenFood: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s5)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Overline("Trigger foods · 14d")
                Spacer(Modifier.width(6.dp))
                InfoIcon(title = GutExplainers.TRIGGERS_TITLE, body = GutExplainers.TRIGGERS_BODY)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Refresh",
                    color = Semantic.colors.accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onRetry)
                )
            }

            when (state) {
                is TriggersUi.Loading -> {
                    Text(
                        text = "Crunching the numbers…",
                        color = Semantic.colors.inkMid,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is TriggersUi.NotConfigured -> {
                    Text(
                        text = "Connect a backend in Settings to see correlations.",
                        color = Semantic.colors.inkMid,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is TriggersUi.Error -> {
                    Text(
                        text = "Couldn't load triggers: ${state.message}",
                        color = Semantic.colors.inkMid,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is TriggersUi.Loaded -> TriggerLoadedBody(state.report, state.source, onOpenFood)
            }
        }
    }
}

@Composable
private fun TriggerLoadedBody(
    report: TriggerReportDto,
    source: TriggerSource,
    onOpenFood: (String) -> Unit
) {
    val top = report.foods.take(5)
    if (top.isEmpty()) {
        Text(
            text = if (report.flaresConsidered == 0)
                "No flares logged in the last 14 days — log bowel events or symptoms (severity ≥ moderate) to surface patterns."
            else
                "Not enough overlap between foods and flares yet.",
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }
    val maxScore = top.maxOf { it.score }.coerceAtLeast(0.0001)
    Text(
        text = buildString {
            append(report.flaresConsidered)
            append(" flare").append(if (report.flaresConsidered == 1) "" else "s")
            append(" · ")
            append(report.foodsScored)
            append(" food").append(if (report.foodsScored == 1) "" else "s").append(" scored")
            if (source == TriggerSource.Local) append(" · on-device")
        },
        color = Semantic.colors.inkLow,
        style = MaterialTheme.typography.labelSmall
    )
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        top.forEachIndexed { idx, score ->
            TriggerRow(
                rank = idx + 1,
                score = score,
                maxScore = maxScore,
                onClick = { onOpenFood(score.foodClientUuid) }
            )
        }
    }
}

@Composable
private fun TriggerRow(
    rank: Int,
    score: TriggerScoreDto,
    maxScore: Double,
    onClick: () -> Unit
) {
    val barFraction = (score.score / maxScore).coerceIn(0.0, 1.0).toFloat()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Semantic.colors.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(rankColor(rank).copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                color = Semantic.colors.inkHigh,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.width(Tokens.Space.s3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = score.foodName + (score.foodBrand?.let { " · $it" } ?: ""),
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Semantic.colors.surface3)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(rankColor(rank))
                    )
                }
                Spacer(Modifier.width(Tokens.Space.s2))
                Text(
                    text = "%.1f".format(score.score),
                    color = Semantic.colors.inkMid,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FodmapChip(level = score.fodmapLevel)
                NovaChip(novaClass = score.novaClass)
                Text(
                    text = "${score.flareCount}/${score.baselineCount} flares",
                    color = Semantic.colors.inkLow,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

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

@Composable
private fun NovaChip(novaClass: Int) {
    val tone = when (novaClass) {
        1 -> UltraprocessedColors.Nova1
        2 -> UltraprocessedColors.Nova2
        3 -> UltraprocessedColors.Nova3
        else -> UltraprocessedColors.Nova4
    }
    Text(
        text = "NOVA $novaClass",
        color = Semantic.colors.inkHigh,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(Tokens.Radius.sm))
            .background(tone.copy(alpha = 0.30f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private fun rankColor(rank: Int): Color = when (rank) {
    1 -> UltraprocessedColors.Nova4
    2 -> UltraprocessedColors.Nova3
    3 -> UltraprocessedColors.Nova3
    else -> UltraprocessedColors.Nova2
}
