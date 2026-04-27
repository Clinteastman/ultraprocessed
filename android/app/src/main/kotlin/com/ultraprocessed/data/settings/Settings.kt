package com.ultraprocessed.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Non-secret settings, persisted via DataStore. API keys live separately
 * in EncryptedSharedPreferences (see [SecretStore]).
 */
class Settings(context: Context) {

    private val ds = context.applicationContext.dataStore

    val provider: Flow<ProviderType> = ds.data.map {
        ProviderType.fromKey(it[Keys.Provider]) ?: ProviderType.Anthropic
    }

    val providerBaseUrl: Flow<String> = ds.data.map {
        it[Keys.ProviderBaseUrl] ?: ProviderType.Anthropic.defaultBaseUrl
    }

    val providerModel: Flow<String> = ds.data.map {
        it[Keys.ProviderModel] ?: ProviderType.Anthropic.defaultModel
    }

    val backendBaseUrl: Flow<String?> = ds.data.map { it[Keys.BackendBaseUrl] }

    val relayThroughBackend: Flow<Boolean> = ds.data.map {
        it[Keys.RelayThroughBackend] ?: false
    }

    suspend fun setProvider(p: ProviderType) {
        ds.edit { prefs ->
            prefs[Keys.Provider] = p.key
            // Reset URL/model to provider defaults when switching, unless the
            // user has explicitly customised them.
            if (prefs[Keys.ProviderBaseUrl].isNullOrBlank()) {
                prefs[Keys.ProviderBaseUrl] = p.defaultBaseUrl
            }
            if (prefs[Keys.ProviderModel].isNullOrBlank()) {
                prefs[Keys.ProviderModel] = p.defaultModel
            }
        }
    }

    suspend fun setProviderBaseUrl(url: String) = ds.edit { it[Keys.ProviderBaseUrl] = url }
    suspend fun setProviderModel(model: String) = ds.edit { it[Keys.ProviderModel] = model }
    suspend fun setBackendBaseUrl(url: String?) = ds.edit {
        if (url.isNullOrBlank()) it.remove(Keys.BackendBaseUrl) else it[Keys.BackendBaseUrl] = url
    }
    suspend fun setRelayThroughBackend(value: Boolean) = ds.edit { it[Keys.RelayThroughBackend] = value }

    private object Keys {
        val Provider = stringPreferencesKey("provider")
        val ProviderBaseUrl = stringPreferencesKey("provider_base_url")
        val ProviderModel = stringPreferencesKey("provider_model")
        val BackendBaseUrl = stringPreferencesKey("backend_base_url")
        val RelayThroughBackend = booleanPreferencesKey("relay_through_backend")
    }

    companion object {
        private val Context.dataStore: androidx.datastore.core.DataStore<Preferences>
                by preferencesDataStore("ultraprocessed_settings")
    }
}

/**
 * The two LLM provider shapes the app supports out of the box, plus the
 * default endpoint and model for each. The OpenAI-compatible adapter
 * drives almost every other provider (NVIDIA NIM, OpenRouter, Together,
 * Groq, Ollama, LM Studio) by varying baseUrl + model.
 */
enum class ProviderType(
    val key: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    Anthropic(
        key = "anthropic",
        displayName = "Anthropic (Claude)",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-haiku-4-5-20251001"
    ),
    OpenAICompatible(
        key = "openai_compatible",
        displayName = "OpenAI-compatible",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini"
    );

    companion object {
        fun fromKey(key: String?): ProviderType? = entries.firstOrNull { it.key == key }
    }
}
