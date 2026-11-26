package com.atonai.data.perms

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.permsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "aton_permissions")

/**
 * Persists the user's [PermRule] list as one JSON array, mirroring
 * `permissions.json` in 04_DATA_MODELS.md §"on disk". One value keeps schema
 * changes additive: unknown keys are ignored, a missing list is empty.
 */
class PermStore(private val context: Context) {

    private val key = stringPreferencesKey("perm_rules_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(PermRule.serializer())

    val flow: Flow<List<PermRule>> = context.permsDataStore.data.map { read(it[key]) }

    suspend fun rules(): List<PermRule> = read(context.permsDataStore.data.first()[key])

    /** Adds a rule, replacing any existing rule with the same (action, pattern). */
    suspend fun put(rule: PermRule) = context.permsDataStore.edit { prefs ->
        val current = read(prefs[key]).filterNot { it.action == rule.action && it.pattern == rule.pattern }
        prefs[key] = json.encodeToString(serializer, current + rule)
    }

    /** Removes the rule with the given (action, pattern), if any. */
    suspend fun remove(action: String, pattern: String) = context.permsDataStore.edit { prefs ->
        val current = read(prefs[key]).filterNot { it.action == action && it.pattern == pattern }
        prefs[key] = json.encodeToString(serializer, current)
    }

    suspend fun clear() = context.permsDataStore.edit { it.remove(key) }

    private fun read(raw: String?): List<PermRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
