package com.atonai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.selectionStore: DataStore<Preferences> by
    preferencesDataStore(name = "aton_selection")

/**
 * Persists the current provider/model selection and any per-provider base-URL
 * override. Secrets (API keys) live in [com.atonai.data.prefs.SecretStore];
 * they are never written here.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val PROVIDER_ID = stringPreferencesKey("provider_id")
        val MODEL_ID = stringPreferencesKey("model_id")
        val BASE_URL_PREFIX = "base_url_" // + providerId
    }

    private fun baseUrlKey(providerId: String) =
        stringPreferencesKey(Keys.BASE_URL_PREFIX + providerId)

    val selectedProviderId: Flow<String?> =
        context.selectionStore.data.map { it[Keys.PROVIDER_ID] }

    val selectedModelId: Flow<String?> =
        context.selectionStore.data.map { it[Keys.MODEL_ID] }

    fun baseUrl(providerId: String): Flow<String?> =
        context.selectionStore.data.map { it[baseUrlKey(providerId)] }

    suspend fun setBaseUrl(providerId: String, url: String?) {
        context.selectionStore.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(baseUrlKey(providerId))
            else prefs[baseUrlKey(providerId)] = url
        }
    }

    suspend fun setSelection(providerId: String, modelId: String) {
        context.selectionStore.edit { prefs ->
            prefs[Keys.PROVIDER_ID] = providerId
            prefs[Keys.MODEL_ID] = modelId
        }
    }
}
