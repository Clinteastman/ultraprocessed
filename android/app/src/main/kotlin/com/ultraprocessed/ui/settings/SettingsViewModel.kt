package com.ultraprocessed.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.ultraprocessed.UltraprocessedApplication
import com.ultraprocessed.core.AppContainer
import com.ultraprocessed.data.settings.ProviderType
import com.ultraprocessed.data.settings.SecretStore
import com.ultraprocessed.data.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: Settings,
    private val secrets: SecretStore
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
        _state.value = _state.value.copy(backendUrl = url)
        viewModelScope.launch { settings.setBackendBaseUrl(url.takeIf { it.isNotBlank() }) }
    }

    fun updateBackendToken(token: String) {
        _state.value = _state.value.copy(backendToken = token)
        secrets.backendToken = token
    }

    fun setRelayThroughBackend(value: Boolean) {
        _state.value = _state.value.copy(relayThroughBackend = value)
        viewModelScope.launch { settings.setRelayThroughBackend(value) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as UltraprocessedApplication)
                val container = app.container
                SettingsViewModel(container.settings, container.secrets)
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
    val relayThroughBackend: Boolean = false
)
