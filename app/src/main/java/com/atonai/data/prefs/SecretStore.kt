package com.atonai.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Encrypted store for secrets — provider API keys and MCP/OAuth tokens
 * (decision D-05). Backed by `EncryptedSharedPreferences` with an AES256 master
 * key in the Android Keystore, and excluded from Android backup
 * (see res/xml/backup_rules.xml).
 *
 * The in-memory cache keeps a [Flow] of the whole secret map so the UI can react
 * to key edits without re-reading the encrypted file on every frame.
 */
class SecretStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val cache = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Observable snapshot of all secrets. Loaded lazily on first access. */
    val flow: Flow<Map<String, String>> = cache.asStateFlow()

    private fun keyFor(providerId: String) = "$API_KEY_PREFIX$providerId"

    suspend fun load(): Map<String, String> = withContext(Dispatchers.IO) {
        val map = prefs.all
            .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
            .toMap()
        cache.value = map
        map
    }

    suspend fun apiKey(providerId: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(keyFor(providerId), null)
    }

    suspend fun setApiKey(providerId: String, key: String?) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            if (key.isNullOrBlank()) remove(keyFor(providerId)) else putString(keyFor(providerId), key)
        }.apply()
        cache.value = cache.value.toMutableMap().apply {
            if (key.isNullOrBlank()) remove(keyFor(providerId)) else put(keyFor(providerId), key)
        }
    }

    suspend fun token(serverId: String): String? = withContext(Dispatchers.IO) {
        prefs.getString("$TOKEN_PREFIX$serverId", null)
    }

    suspend fun setToken(serverId: String, token: String?) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            if (token.isNullOrBlank()) remove("$TOKEN_PREFIX$serverId")
            else putString("$TOKEN_PREFIX$serverId", token)
        }.apply()
    }

    companion object {
        private const val FILE_NAME = "aton_secrets"
        private const val API_KEY_PREFIX = "api_key_"
        private const val TOKEN_PREFIX = "token_"
    }
}
