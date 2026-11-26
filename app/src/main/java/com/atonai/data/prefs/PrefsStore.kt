package com.atonai.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.prefsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "aton_prefs")

/**
 * Stores [Prefs] as one JSON blob under a single key. One value means schema
 * changes are additive and cheap: unknown keys are ignored, missing keys fall
 * back to the [Prefs] defaults.
 */
class PrefsStore(private val context: Context) {

    private val key = stringPreferencesKey("prefs_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val flow: Flow<Prefs> = context.prefsDataStore.data.map { prefs ->
        prefs[key]?.let { decode(it) } ?: Prefs()
    }

    suspend fun current(): Prefs {
        val prefs = context.prefsDataStore.data.first()
        return prefs[key]?.let { decode(it) } ?: Prefs()
    }

    /** Applies [block] to the current prefs and persists the result atomically. */
    suspend fun update(block: (Prefs) -> Prefs) {
        context.prefsDataStore.edit { prefs ->
            val current = prefs[key]?.let { decode(it) } ?: Prefs()
            prefs[key] = json.encodeToString(Prefs.serializer(), block(current))
        }
    }

    private fun decode(raw: String): Prefs =
        runCatching { json.decodeFromString(Prefs.serializer(), raw) }.getOrElse { Prefs() }
}
