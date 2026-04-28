package com.ultraprocessed.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultraprocessed.data.settings.ProviderType
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.ui.components.FastingPicker
import com.ultraprocessed.ui.components.Overline

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onScanPairingQr: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenPlaces: () -> Unit = {}
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state by vm.state.collectAsState()
    val draft = state.draft

    LaunchedEffect(Unit) { vm.refresh() }

    Box(modifier = Modifier.fillMaxSize().background(Semantic.colors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Space.s5)
                .padding(bottom = if (state.dirty) 96.dp else Tokens.Space.s8)
        ) {
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
                    text = "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    color = Semantic.colors.inkHigh
                )
            }

            Spacer(Modifier.height(Tokens.Space.s4))
            Text(
                text = "The app works fully on this device. Set your LLM provider below to enable photo scanning. Pairing a homelab backend is optional.",
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(Tokens.Space.s6))
            SectionHeader("Provider")
            Text(
                text = "Used for analysing photographed and manually entered foods. Barcode scans don't need it.",
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Tokens.Space.s3))
            ProviderSwitch(selected = draft.provider, onChange = vm::setProvider)

            Spacer(Modifier.height(Tokens.Space.s4))
            Field(label = "Base URL", value = draft.baseUrl, onValueChange = vm::updateBaseUrl)
            Spacer(Modifier.height(Tokens.Space.s3))
            Field(label = "Model", value = draft.model, onValueChange = vm::updateModel)
            Spacer(Modifier.height(Tokens.Space.s3))
            Field(
                label = "API key",
                value = draft.apiKey,
                onValueChange = vm::updateApiKey,
                secret = true,
                helper = "Stored on device, encrypted."
            )

            Spacer(Modifier.height(Tokens.Space.s7))
            SectionHeader("Fasting")
            Text(
                text = "Drives the home-screen status strip and the dashboard's fasting card. Off by default; toggle Active to enable.",
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Tokens.Space.s3))
            FastingPicker(
                profile = draft.fasting,
                onProfileChange = vm::setFasting
            )

            Spacer(Modifier.height(Tokens.Space.s7))
            SectionHeader("Home location")
            Text(
                text = "Used as a fallback location label for any item logged without GPS, and to backfill older entries.",
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Tokens.Space.s3))
            Field(
                label = "Label (e.g. \"Home\")",
                value = draft.homeLabel,
                onValueChange = vm::updateHomeLabel
            )
            Spacer(Modifier.height(Tokens.Space.s3))
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(modifier = Modifier.weight(1f)) {
                    Field(
                        label = "Latitude",
                        value = draft.homeLat,
                        onValueChange = vm::updateHomeLat
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    Field(
                        label = "Longitude",
                        value = draft.homeLng,
                        onValueChange = vm::updateHomeLng
                    )
                }
            }
            Spacer(Modifier.height(Tokens.Space.s2))
            Text(
                text = "Tip: longitudes in the UK are negative (e.g. -1.7986).",
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Tokens.Space.s3))
            BackfillHomeButton(
                status = state.backfillStatus,
                enabled = !state.dirty &&
                    state.saved.homeLat.toDoubleOrNull() != null &&
                    state.saved.homeLng.toDoubleOrNull() != null,
                onClick = vm::backfillUnlocatedAsHome
            )
            Spacer(Modifier.height(Tokens.Space.s3))
            RetagHomeButton(
                status = state.retagStatus,
                enabled = !state.dirty &&
                    state.saved.homeLat.toDoubleOrNull() != null &&
                    state.saved.homeLng.toDoubleOrNull() != null,
                onClick = vm::retagHomeItems
            )

            Spacer(Modifier.height(Tokens.Space.s7))

            // Backend / homelab integration: optional, collapsed by default.
            // Auto-expands when the user already has a backend configured so
            // they can manage it without having to find it.
            val backendConfigured = draft.backendUrl.isNotBlank() || draft.backendToken.isNotBlank()
            var backendExpanded by remember { mutableStateOf(backendConfigured) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { backendExpanded = !backendExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Overline(text = "Connect a homelab backend")
                    Spacer(Modifier.height(Tokens.Space.s1))
                    Text(
                        text = "Optional. Sync your data to a self-hosted server for a web dashboard, Home Assistant integration, or multi-device use.",
                        color = Semantic.colors.inkMid,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.size(Tokens.Space.s2))
                Icon(
                    imageVector = if (backendExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (backendExpanded) "Collapse" else "Expand",
                    tint = Semantic.colors.inkMid
                )
            }
            AnimatedVisibility(visible = backendExpanded) {
                Column(modifier = Modifier.padding(top = Tokens.Space.s4)) {
                    Text(
                        text = "Easiest: open your dashboard's Settings page on a laptop, tap \"Pair a device\", then scan the QR with the button below.",
                        color = Semantic.colors.inkMid,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(Tokens.Space.s3))
                    ScanPairingQrButton(onClick = onScanPairingQr)
                    Spacer(Modifier.height(Tokens.Space.s5))
                    Overline(text = "Or set manually")
                    Spacer(Modifier.height(Tokens.Space.s2))
                    Field(label = "Backend URL", value = draft.backendUrl, onValueChange = vm::updateBackendUrl)
                    Spacer(Modifier.height(Tokens.Space.s3))
                    Field(
                        label = "Device token",
                        value = draft.backendToken,
                        onValueChange = vm::updateBackendToken,
                        secret = true
                    )
                    Spacer(Modifier.height(Tokens.Space.s3))
                    PairBackendButton(
                        status = state.pairStatus,
                        enabled = draft.backendUrl.isNotBlank(),
                        onClick = vm::pair
                    )
                    Spacer(Modifier.height(Tokens.Space.s4))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Relay analyses through backend",
                                color = Semantic.colors.inkHigh,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "When enabled, the phone never sees a provider key.",
                                color = Semantic.colors.inkMid,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = draft.relayThroughBackend,
                            onCheckedChange = vm::setRelayThroughBackend,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Semantic.colors.accent,
                                checkedTrackColor = Semantic.colors.accent.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(Tokens.Space.s7))
            SectionHeader("More")
            Spacer(Modifier.height(Tokens.Space.s2))
            NavRow(label = "Places map", subtitle = "Where you've eaten, with NOVA per place.", onClick = onOpenPlaces)
            Spacer(Modifier.height(Tokens.Space.s2))
            NavRow(label = "How this app works", subtitle = "What NOVA is, what fasting schedules mean, why we lead with processing.", onClick = onOpenHelp)

            if (state.saveStatus is SaveStatus.Saved && !state.dirty) {
                Spacer(Modifier.height(Tokens.Space.s4))
                Text(
                    text = "Saved.",
                    color = Semantic.colors.success,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        SaveBar(
            visible = state.dirty,
            saving = state.saveStatus is SaveStatus.InFlight,
            onSave = vm::save,
            onDiscard = vm::discard,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Overline(text = text)
    Spacer(Modifier.height(Tokens.Space.s2))
}

@Composable
private fun ProviderSwitch(
    selected: ProviderType,
    onChange: (ProviderType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Semantic.colors.surface1)
            .padding(Tokens.Space.s1)
    ) {
        ProviderType.entries.forEach { p ->
            val isActive = p == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(Tokens.Radius.sm))
                    .background(if (isActive) Semantic.colors.accent else Semantic.colors.surface1)
                    .clickable { onChange(p) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = p.displayName,
                    color = if (isActive) Semantic.colors.inkInverse else Semantic.colors.inkMid,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
    helper: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Overline(text = label)
        Spacer(Modifier.height(Tokens.Space.s1))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text
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
        helper?.let {
            Spacer(Modifier.height(Tokens.Space.s1))
            Text(
                text = it,
                color = Semantic.colors.inkLow,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ScanPairingQrButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Semantic.colors.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Scan pairing QR",
            color = Semantic.colors.inkInverse,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun PairBackendButton(
    status: PairStatus,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val inFlight = status is PairStatus.InFlight
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(Tokens.Radius.md))
                .background(
                    if (enabled && !inFlight) Semantic.colors.accent
                    else Semantic.colors.surface3
                )
                .clickable(enabled = enabled && !inFlight, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (inFlight) "Pairing..." else "Pair with backend",
                color = if (enabled && !inFlight) Semantic.colors.inkInverse else Semantic.colors.inkLow,
                style = MaterialTheme.typography.titleMedium
            )
        }
        when (status) {
            is PairStatus.Success -> {
                Spacer(Modifier.height(Tokens.Space.s2))
                Text(
                    text = status.message,
                    color = Semantic.colors.success,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is PairStatus.Failed -> {
                Spacer(Modifier.height(Tokens.Space.s2))
                Text(
                    text = status.message,
                    color = Semantic.colors.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun RetagHomeButton(
    status: BackfillStatus,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val inFlight = status is BackfillStatus.InFlight
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(Tokens.Radius.md))
                .background(
                    if (enabled && !inFlight) Semantic.colors.surface2
                    else Semantic.colors.surface3
                )
                .clickable(enabled = enabled && !inFlight, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (inFlight) "Re-tagging..." else "Re-tag existing Home items with current coords",
                color = if (enabled && !inFlight) Semantic.colors.inkHigh else Semantic.colors.inkLow,
                style = MaterialTheme.typography.titleMedium
            )
        }
        when (status) {
            is BackfillStatus.Done -> {
                Spacer(Modifier.height(Tokens.Space.s2))
                Text(
                    text = "Re-tagged ${status.rowsUpdated} item${if (status.rowsUpdated == 1) "" else "s"}.",
                    color = Semantic.colors.success,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is BackfillStatus.Failed -> {
                Spacer(Modifier.height(Tokens.Space.s2))
                Text(
                    text = status.message,
                    color = Semantic.colors.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun NavRow(label: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Semantic.colors.surface1)
            .clickable(onClick = onClick)
            .padding(Tokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = subtitle,
                color = Semantic.colors.inkMid,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(text = "›", color = Semantic.colors.inkMid)
    }
}

@Composable
private fun BackfillHomeButton(
    status: BackfillStatus,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val inFlight = status is BackfillStatus.InFlight
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(Tokens.Radius.md))
                .background(
                    if (enabled && !inFlight) Semantic.colors.surface2
                    else Semantic.colors.surface3
                )
                .clickable(enabled = enabled && !inFlight, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (inFlight) "Tagging..." else "Tag past items without location as Home",
                color = if (enabled && !inFlight) Semantic.colors.inkHigh else Semantic.colors.inkLow,
                style = MaterialTheme.typography.titleMedium
            )
        }
        when (status) {
            is BackfillStatus.Done -> {
                Spacer(Modifier.height(Tokens.Space.s2))
                Text(
                    text = "Tagged ${status.rowsUpdated} item${if (status.rowsUpdated == 1) "" else "s"} as Home.",
                    color = Semantic.colors.success,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is BackfillStatus.Failed -> {
                Spacer(Modifier.height(Tokens.Space.s2))
                Text(
                    text = status.message,
                    color = Semantic.colors.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            else -> {
                if (!enabled) {
                    Spacer(Modifier.height(Tokens.Space.s2))
                    Text(
                        text = "Set lat/lng above and tap Save first.",
                        color = Semantic.colors.inkLow,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveBar(
    visible: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Semantic.colors.surface2)
                .padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)
        ) {
            Text(
                text = "Unsaved changes",
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(Tokens.Radius.md))
                    .background(Semantic.colors.surface3)
                    .clickable(onClick = onDiscard)
                    .padding(horizontal = Tokens.Space.s4),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Discard",
                    color = Semantic.colors.inkHigh,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(Tokens.Radius.md))
                    .background(if (saving) Semantic.colors.surface3 else Semantic.colors.accent)
                    .clickable(enabled = !saving, onClick = onSave)
                    .padding(horizontal = Tokens.Space.s5),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (saving) "Saving..." else "Save",
                    color = if (saving) Semantic.colors.inkLow else Semantic.colors.inkInverse,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
