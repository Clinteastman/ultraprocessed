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
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: Settings,
    private val secrets: SecretStore,
    private val httpClient: HttpClient
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = SettingsState(
                provider = settings.provider.first(),
                baseUrl = settings.providerBaseUrl.first(),
                model = settings.providerModel.first(),
                apiKey = secrets.providerApiKey.orEmpty(),
                backendUrl = settings.backendBaseUrl.first().orEmpty(),
                backendToken = secrets.backendToken.orEmpty(),
                relayThroughBackend = settings.relayThroughBackend.first()
            )
        }
    }

    fun setProvider(p: ProviderType) {
        viewModelScope.launch {
            settings.setProvider(p)
            settings.setProviderBaseUrl(p.defaultBaseUrl)
            settings.setProviderModel(p.defaultModel)
            _state.value = _state.value.copy(
                provider = p,
                baseUrl = p.defaultBaseUrl,
                model = p.defaultModel
            )
        }
    }

    fun updateBaseUrl(url: String) {
        _state.value = _state.value.copy(baseUrl = url)
        viewModelScope.launch { settings.setProviderBaseUrl(url) }
    }

    fun updateModel(model: String) {
        _state.value = _state.value.copy(model = model)
        viewModelScope.launch { settings.setProviderModel(model) }
    }

    fun updateApiKey(key: String) {
        _state.value = _state.value.copy(apiKey = key)
        secrets.providerApiKey = key
    }

    fun updateBackendUrl(url: String) {
        _state.value = _state.value.copy(
            backendUrl = url,
            pairStatus = PairStatus.Idle
        )
        viewModelScope.launch { settings.setBackendBaseUrl(url.takeIf { it.isNotBlank() }) }
    }

    fun updateBackendToken(token: String) {
        _state.value = _state.value.copy(backendToken = token, pairStatus = PairStatus.Idle)
        secrets.backendToken = token
    }

    fun setRelayThroughBackend(value: Boolean) {
        _state.value = _state.value.copy(relayThroughBackend = value)
        viewModelScope.launch { settings.setRelayThroughBackend(value) }
    }

    /**
     * Asks the backend for a fresh device token and stashes it. Uses the
     * phone's model name (e.g. "Pixel 8 Pro") as the device name so the
     * dashboard's later device list reads sensibly.
     */
    fun pair() {
        val url = _state.value.backendUrl
        if (url.isBlank()) {
            _state.value = _state.value.copy(pairStatus = PairStatus.Failed("Set a Backend URL first."))
            return
        }
        _state.value = _state.value.copy(pairStatus = PairStatus.InFlight)
        viewModelScope.launch {
            val client = BackendClient(baseUrl = url, token = "", client = httpClient)
            val result = client.pair(deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            result.fold(
                onSuccess = { tokenResp ->
                    secrets.backendToken = tokenResp.token
                    _state.value = _state.value.copy(
                        backendToken = tokenResp.token,
                        pairStatus = PairStatus.Success("Paired as device #${tokenResp.deviceId}")
                    )
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
                SettingsViewModel(container.settings, container.secrets, container.httpClient)
            }
        }
    }
}

data class SettingsState(
    val provider: ProviderType = ProviderType.Anthropic,
    val baseUrl: String = ProviderType.Anthropic.defaultBaseUrl,
    val model: String = ProviderType.Anthropic.defaultModel,
    val apiKey: String = "",
    val backendUrl: String = "",
    val backendToken: String = "",
    val relayThroughBackend: Boolean = false,
    val pairStatus: PairStatus = PairStatus.Idle
)

sealed class PairStatus {
    data object Idle : PairStatus()
    data object InFlight : PairStatus()
    data class Success(val message: String) : PairStatus()
    data class Failed(val message: String) : PairStatus()
}
