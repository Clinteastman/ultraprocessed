package com.ultraprocessed.ui.settings

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultraprocessed.data.settings.ProviderType
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.ui.components.Overline

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onScanPairingQr: () -> Unit = {}
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Semantic.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.s5)
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

        Spacer(Modifier.height(Tokens.Space.s6))
        SectionHeader("Provider")
        ProviderSwitch(
            selected = state.provider,
            onChange = vm::setProvider
        )

        Spacer(Modifier.height(Tokens.Space.s4))
        Field(label = "Base URL", value = state.baseUrl, onValueChange = vm::updateBaseUrl)
        Spacer(Modifier.height(Tokens.Space.s3))
        Field(label = "Model", value = state.model, onValueChange = vm::updateModel)
        Spacer(Modifier.height(Tokens.Space.s3))
        Field(
            label = "API key",
            value = state.apiKey,
            onValueChange = vm::updateApiKey,
            secret = true,
            helper = "Stored on device, encrypted."
        )

        Spacer(Modifier.height(Tokens.Space.s7))
        SectionHeader("Backend (optional)")
        Text(
            text = "Easiest: open your dashboard's Settings page on a laptop, tap " +
                "\"Pair a device\", then scan the QR with the button below.",
            color = Semantic.colors.inkMid,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Tokens.Space.s3))
        ScanPairingQrButton(onClick = onScanPairingQr)
        Spacer(Modifier.height(Tokens.Space.s5))
        Overline(text = "Or set manually")
        Spacer(Modifier.height(Tokens.Space.s2))
        Field(label = "Backend URL", value = state.backendUrl, onValueChange = vm::updateBackendUrl)
        Spacer(Modifier.height(Tokens.Space.s3))
        Field(
            label = "Device token",
            value = state.backendToken,
            onValueChange = vm::updateBackendToken,
            secret = true
        )
        Spacer(Modifier.height(Tokens.Space.s3))
        PairBackendButton(
            status = state.pairStatus,
            enabled = state.backendUrl.isNotBlank(),
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
                checked = state.relayThroughBackend,
                onCheckedChange = vm::setRelayThroughBackend,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Semantic.colors.accent,
                    checkedTrackColor = Semantic.colors.accent.copy(alpha = 0.3f)
                )
            )
        }

        Spacer(Modifier.height(Tokens.Space.s8))
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
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
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
