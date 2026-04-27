package com.ultraprocessed.ui.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.data.settings.ProviderType
import com.ultraprocessed.data.settings.SecretStore
import com.ultraprocessed.data.settings.Settings
import com.ultraprocessed.sync.BackendClient
import com.ultraprocessed.sync.SyncCoordinator
import com.ultraprocessed.sync.SyncResult
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Draft + saved model: every field edit updates the draft only; Save
 * persists everything to Settings + EncryptedSharedPreferences in one
 * shot. The screen calls [refresh] on every entry so changes made
 * elsewhere (e.g. QR pairing) show up immediately.
 */
class SettingsViewModel(
    private val settings: Settings,
    private val secrets: SecretStore,
    private val httpClient: HttpClient,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val values = SettingsValues(
                provider = settings.provider.first(),
                baseUrl = settings.providerBaseUrl.first(),
                model = settings.providerModel.first(),
                apiKey = secrets.providerApiKey.orEmpty(),
                backendUrl = settings.backendBaseUrl.first().orEmpty(),
                backendToken = secrets.backendToken.orEmpty(),
                relayThroughBackend = settings.relayThroughBackend.first()
            )
            _state.value = _state.value.copy(
                saved = values,
                draft = values,
                saveStatus = SaveStatus.Idle,
                pairStatus = PairStatus.Idle
            )
        }
    }

    fun setProvider(p: ProviderType) {
        _state.value = _state.value.copy(
            draft = _state.value.draft.copy(
                provider = p,
                baseUrl = p.defaultBaseUrl,
                model = p.defaultModel
            ),
            saveStatus = SaveStatus.Idle
        )
    }

    fun updateBaseUrl(url: String) = patchDraft { it.copy(baseUrl = url) }
    fun updateModel(model: String) = patchDraft { it.copy(model = model) }
    fun updateApiKey(key: String) = patchDraft { it.copy(apiKey = key) }
    fun updateBackendUrl(url: String) = patchDraft { it.copy(backendUrl = url) }
    fun updateBackendToken(token: String) = patchDraft { it.copy(backendToken = token) }
    fun setRelayThroughBackend(value: Boolean) = patchDraft { it.copy(relayThroughBackend = value) }

    private inline fun patchDraft(transform: (SettingsValues) -> SettingsValues) {
        _state.value = _state.value.copy(
            draft = transform(_state.value.draft),
            saveStatus = SaveStatus.Idle
        )
    }

    /** Persists the current draft to Settings + SecretStore. */
    fun save() {
        val draft = _state.value.draft
        _state.value = _state.value.copy(saveStatus = SaveStatus.InFlight)
        viewModelScope.launch {
            settings.setProvider(draft.provider)
            settings.setProviderBaseUrl(draft.baseUrl)
            settings.setProviderModel(draft.model)
            settings.setBackendBaseUrl(draft.backendUrl.takeIf { it.isNotBlank() })
            settings.setRelayThroughBackend(draft.relayThroughBackend)
            secrets.providerApiKey = draft.apiKey
            secrets.backendToken = draft.backendToken
            _state.value = _state.value.copy(
                saved = draft,
                saveStatus = SaveStatus.Saved
            )
        }
    }

    /** Manually trigger a sync; surfaces the result on screen. */
    fun syncNow() {
        _state.value = _state.value.copy(syncStatus = SyncStatus.InFlight)
        viewModelScope.launch {
            val result = syncCoordinator.syncOnce()
            _state.value = _state.value.copy(syncStatus = SyncStatus.fromResult(result))
        }
    }

    /** Throws away unsaved edits; reverts the draft to the saved values. */
    fun discard() {
        _state.value = _state.value.copy(
            draft = _state.value.saved,
            saveStatus = SaveStatus.Idle
        )
    }

    /**
     * Asks the backend for a fresh device token and stashes it. Token
     * lands in the draft *and* is auto-saved, since the user clearly
     * wanted that value to apply.
     */
    fun pair() {
        val url = _state.value.draft.backendUrl
        if (url.isBlank()) {
            _state.value = _state.value.copy(
                pairStatus = PairStatus.Failed("Set a Backend URL first.")
            )
            return
        }
        _state.value = _state.value.copy(pairStatus = PairStatus.InFlight)
        viewModelScope.launch {
            val client = BackendClient(baseUrl = url, token = "", client = httpClient)
            val result = client.pair(deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            result.fold(
                onSuccess = { tokenResp ->
                    val newDraft = _state.value.draft.copy(backendToken = tokenResp.token)
                    _state.value = _state.value.copy(
                        draft = newDraft,
                        pairStatus = PairStatus.Success("Paired as device #${tokenResp.deviceId}")
                    )
                    save()
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        pairStatus = PairStatus.Failed(err.message ?: "Pairing failed.")
                    )
                }
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                val container = app.container
                SettingsViewModel(
                    settings = container.settings,
                    secrets = container.secrets,
                    httpClient = container.httpClient,
                    syncCoordinator = container.syncCoordinator
                )
            }
        }
    }
}

data class SettingsValues(
    val provider: ProviderType = ProviderType.Anthropic,
    val baseUrl: String = ProviderType.Anthropic.defaultBaseUrl,
    val model: String = ProviderType.Anthropic.defaultModel,
    val apiKey: String = "",
    val backendUrl: String = "",
    val backendToken: String = "",
    val relayThroughBackend: Boolean = false
)

data class SettingsState(
    val saved: SettingsValues = SettingsValues(),
    val draft: SettingsValues = SettingsValues(),
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val pairStatus: PairStatus = PairStatus.Idle,
    val syncStatus: SyncStatus = SyncStatus.Idle
) {
    val dirty: Boolean get() = saved != draft
}

sealed class SaveStatus {
    data object Idle : SaveStatus()
    data object InFlight : SaveStatus()
    data object Saved : SaveStatus()
}

sealed class PairStatus {
    data object Idle : PairStatus()
    data object InFlight : PairStatus()
    data class Success(val message: String) : PairStatus()
    data class Failed(val message: String) : PairStatus()
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object InFlight : SyncStatus()
    data class Reported(val message: String, val isError: Boolean) : SyncStatus()

    companion object {
        fun fromResult(result: SyncResult): SyncStatus = when (result) {
            SyncResult.NotConfigured -> Reported("Backend URL or token not set.", true)
            SyncResult.BackendUnreachable -> Reported("Backend unreachable. Check the URL.", true)
            SyncResult.UpToDate -> Reported("Already up to date.", false)
            is SyncResult.Pushed -> Reported(
                "Pushed ${result.foodCount} foods, ${result.logCount} logs.",
                false
            )
            is SyncResult.Failed -> Reported("Sync failed: ${result.message}", true)
        }
    }
}
