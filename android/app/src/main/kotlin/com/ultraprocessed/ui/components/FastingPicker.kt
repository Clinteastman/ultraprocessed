package com.ultraprocessed.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ultraprocessed.data.entities.FastingProfile
import com.ultraprocessed.data.entities.ScheduleType
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens

private data class FastingPreset(
    val type: ScheduleType,
    val label: String,
    val family: Family,
    val blurb: String,
    val apply: (FastingProfile) -> FastingProfile
)

private enum class Family { Tre, Weekly, Adf }

private val PRESETS = listOf(
    FastingPreset(ScheduleType.SIXTEEN_EIGHT, "16:8", Family.Tre,
        "Eat in an 8-hour window each day. Most studied TRE pattern.") {
        it.copy(scheduleType = ScheduleType.SIXTEEN_EIGHT, name = "16:8",
            eatingWindowStartMinutes = 12 * 60, eatingWindowEndMinutes = 20 * 60,
            restrictedDaysMask = 0, restrictedKcalTarget = null)
    },
    FastingPreset(ScheduleType.EIGHTEEN_SIX, "18:6", Family.Tre,
        "6-hour eating window, 18 hours fasting.") {
        it.copy(scheduleType = ScheduleType.EIGHTEEN_SIX, name = "18:6",
            eatingWindowStartMinutes = 13 * 60, eatingWindowEndMinutes = 19 * 60,
            restrictedDaysMask = 0, restrictedKcalTarget = null)
    },
    FastingPreset(ScheduleType.TWENTY_FOUR, "20:4", Family.Tre,
        "4-hour eating window (Warrior diet).") {
        it.copy(scheduleType = ScheduleType.TWENTY_FOUR, name = "20:4",
            eatingWindowStartMinutes = 14 * 60, eatingWindowEndMinutes = 18 * 60,
            restrictedDaysMask = 0, restrictedKcalTarget = null)
    },
    FastingPreset(ScheduleType.OMAD, "OMAD", Family.Tre,
        "One meal a day; ~1 hour eating window.") {
        it.copy(scheduleType = ScheduleType.OMAD, name = "OMAD",
            eatingWindowStartMinutes = 17 * 60, eatingWindowEndMinutes = 18 * 60,
            restrictedDaysMask = 0, restrictedKcalTarget = null)
    },
    FastingPreset(ScheduleType.FIVE_TWO, "5:2", Family.Weekly,
        "5 normal-eating days + 2 restricted (~500 kcal). Recent work suggests scheduling the 2 restricted days back-to-back can improve insulin sensitivity vs spacing them out.") {
        it.copy(scheduleType = ScheduleType.FIVE_TWO, name = "5:2",
            eatingWindowStartMinutes = 0, eatingWindowEndMinutes = 1440,
            restrictedDaysMask = 0b0000011, restrictedKcalTarget = 500)
    },
    FastingPreset(ScheduleType.FOUR_THREE, "4:3", Family.Weekly,
        "4 normal + 3 restricted days. More aggressive variant of 5:2.") {
        it.copy(scheduleType = ScheduleType.FOUR_THREE, name = "4:3",
            eatingWindowStartMinutes = 0, eatingWindowEndMinutes = 1440,
            restrictedDaysMask = 0b0010101, restrictedKcalTarget = 500)
    },
    FastingPreset(ScheduleType.ADF, "ADF", Family.Adf,
        "Alternate-day fasting. Restricted (~500 kcal) every other day.") {
        it.copy(scheduleType = ScheduleType.ADF, name = "ADF",
            eatingWindowStartMinutes = 0, eatingWindowEndMinutes = 1440,
            restrictedDaysMask = 0b0101010, restrictedKcalTarget = 500)
    },
    FastingPreset(ScheduleType.CUSTOM, "Custom", Family.Tre,
        "Pick your own eating window or restricted-day mask.") { it.copy(scheduleType = ScheduleType.CUSTOM) }
)

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FastingPicker(
    profile: FastingProfile,
    onProfileChange: (FastingProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = remember(profile.scheduleType) { PRESETS.firstOrNull { it.type == profile.scheduleType } ?: PRESETS.first() }

    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)
        ) {
            PRESETS.forEach { preset ->
                val isActive = preset.type == profile.scheduleType
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Tokens.Radius.sm))
                        .background(if (isActive) Semantic.colors.accent else Semantic.colors.surface2)
                        .clickable { onProfileChange(preset.apply(profile)) }
                        .padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2)
                ) {
                    Text(
                        text = preset.label,
                        color = if (isActive) Semantic.colors.inkInverse else Semantic.colors.inkMid,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(Tokens.Space.s3))
        Text(
            text = active.blurb,
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(Tokens.Space.s4))
        when (active.family) {
            Family.Tre -> EatingWindow(profile, onProfileChange)
            Family.Weekly, Family.Adf -> RestrictedDayPicker(profile, onProfileChange)
        }

        Spacer(Modifier.height(Tokens.Space.s4))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active",
                    color = Semantic.colors.inkHigh,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Drives the home-screen status strip.",
                    color = Semantic.colors.inkMid,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = profile.active,
                onCheckedChange = { onProfileChange(profile.copy(active = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Semantic.colors.accent,
                    checkedTrackColor = Semantic.colors.accent.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun EatingWindow(profile: FastingProfile, onChange: (FastingProfile) -> Unit) {
    Column {
        Overline(text = "Eating window")
        Spacer(Modifier.height(Tokens.Space.s2))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            TimeField(profile.eatingWindowStartMinutes) { mins ->
                onChange(profile.copy(eatingWindowStartMinutes = mins, scheduleType = ScheduleType.CUSTOM, name = "Custom"))
            }
            Text("to", color = Semantic.colors.inkMid, style = MaterialTheme.typography.bodyMedium)
            TimeField(profile.eatingWindowEndMinutes) { mins ->
                onChange(profile.copy(eatingWindowEndMinutes = mins, scheduleType = ScheduleType.CUSTOM, name = "Custom"))
            }
        }
    }
}

@Composable
private fun TimeField(minutes: Int, onChange: (Int) -> Unit) {
    val text = remember(minutes) {
        val h = (minutes / 60).toString().padStart(2, '0')
        val m = (minutes % 60).toString().padStart(2, '0')
        "$h:$m"
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val cleaned = raw.filter { it.isDigit() || it == ':' }.take(5)
            val parts = cleaned.split(":")
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull() ?: return@OutlinedTextField
                val m = parts[1].toIntOrNull() ?: 0
                if (h in 0..23 && m in 0..59) onChange(h * 60 + m)
            }
        },
        modifier = Modifier.width(110.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Semantic.colors.surface2,
            unfocusedContainerColor = Semantic.colors.surface2,
            focusedTextColor = Semantic.colors.inkHigh,
            unfocusedTextColor = Semantic.colors.inkHigh,
            focusedIndicatorColor = Semantic.colors.accent,
            unfocusedIndicatorColor = Semantic.colors.surface3,
            cursorColor = Semantic.colors.accent
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestrictedDayPicker(profile: FastingProfile, onChange: (FastingProfile) -> Unit) {
    Column {
        Overline(text = "Restricted days")
        Spacer(Modifier.height(Tokens.Space.s2))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            DAY_LABELS.forEachIndexed { idx, label ->
                val on = (profile.restrictedDaysMask and (1 shl idx)) != 0
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Tokens.Radius.sm))
                        .background(if (on) Semantic.colors.accent.copy(alpha = 0.6f) else Semantic.colors.surface2)
                        .clickable { onChange(profile.copy(restrictedDaysMask = profile.restrictedDaysMask xor (1 shl idx))) }
                        .padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2)
                ) {
                    Text(
                        text = label,
                        color = if (on) Semantic.colors.inkHigh else Semantic.colors.inkMid,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (on) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(Tokens.Space.s2))
        Text(
            text = "Tap to toggle. Back-to-back restricted days (e.g. Mon + Tue) are slightly easier on insulin sensitivity than spaced ones in the recent literature.",
            color = Semantic.colors.inkLow,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(Tokens.Space.s4))
        Overline(text = "Restricted-day kcal cap")
        Spacer(Modifier.height(Tokens.Space.s2))
        OutlinedTextField(
            value = profile.restrictedKcalTarget?.toString() ?: "",
            onValueChange = { raw ->
                val n = raw.filter { it.isDigit() }.take(5).toIntOrNull()
                onChange(profile.copy(restrictedKcalTarget = n))
            },
            modifier = Modifier.width(140.dp),
            singleLine = true,
            placeholder = { Text("500") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Semantic.colors.surface2,
                unfocusedContainerColor = Semantic.colors.surface2,
                focusedTextColor = Semantic.colors.inkHigh,
                unfocusedTextColor = Semantic.colors.inkHigh,
                focusedIndicatorColor = Semantic.colors.accent,
                unfocusedIndicatorColor = Semantic.colors.surface3,
                cursorColor = Semantic.colors.accent
            )
        )
    }
}
