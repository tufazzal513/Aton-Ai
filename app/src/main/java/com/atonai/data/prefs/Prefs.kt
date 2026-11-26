package com.atonai.data.prefs

import kotlinx.serialization.Serializable

/** Theme preference. Stored as its [id] string, not ordinal, so it survives reordering. */
enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Non-secret user preferences (04_DATA_MODELS.md §7). Secrets such as API keys
 * live in an encrypted store, never here.
 */
@Serializable
data class Prefs(
    val mode: String = "local",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val directory: String = "",
    val autoConnect: Boolean = true,
    val serverUrl: String = "",
    // [aton] additions
    val effortDefault: String = "medium",
    val compactionThreshold: Int = 80,      // percent of context window
    val ttsEnabled: Boolean = false,
    val notifyOnComplete: Boolean = true,
    val toolOutputCap: Int = 32_000,        // chars kept from a tool result
    val autoSwitchEnabled: Boolean = true,
    val locale: String = "system",
)
