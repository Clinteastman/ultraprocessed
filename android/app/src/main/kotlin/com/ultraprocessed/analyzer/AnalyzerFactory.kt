package com.ultraprocessed.analyzer

import com.ultraprocessed.data.settings.ProviderType
import com.ultraprocessed.data.settings.SecretStore
import com.ultraprocessed.data.settings.Settings
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first

/**
 * Reads current Settings + SecretStore and produces a [FoodAnalyzer]
 * matching the user's configured provider.
 */
class AnalyzerFactory(
    private val settings: Settings,
    private val secrets: SecretStore,
    private val client: HttpClient
) {
    suspend fun current(): FoodAnalyzer {
        val provider = settings.provider.first()
        val baseUrl = settings.providerBaseUrl.first()
        val model = settings.providerModel.first()
        val key = secrets.providerApiKey.orEmpty()

        return when (provider) {
            ProviderType.Anthropic -> AnthropicAnalyzer(
                baseUrl = baseUrl,
                apiKey = key,
                model = model,
                client = client
            )
            ProviderType.OpenAICompatible -> OpenAICompatibleAnalyzer(
                baseUrl = baseUrl,
                apiKey = key,
                model = model,
                client = client
            )
        }
    }
}
