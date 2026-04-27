package com.ultraprocessed.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores secret values (API keys, backend tokens) in
 * EncryptedSharedPreferences backed by an Android Keystore master key.
 *
 * The file name is excluded from auto-backup via res/xml/backup_rules.xml
 * and res/xml/data_extraction_rules.xml, so secrets never sync to a Google
 * account or transfer across devices.
 */
class SecretStore(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var providerApiKey: String?
        get() = prefs.getString(KEY_PROVIDER_API_KEY, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_PROVIDER_API_KEY) else putString(KEY_PROVIDER_API_KEY, value)
        }.apply()

    var backendToken: String?
        get() = prefs.getString(KEY_BACKEND_TOKEN, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_BACKEND_TOKEN) else putString(KEY_BACKEND_TOKEN, value)
        }.apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "ultraprocessed_secrets"
        const val KEY_PROVIDER_API_KEY = "provider_api_key"
        const val KEY_BACKEND_TOKEN = "backend_token"
    }
}
