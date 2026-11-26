package com.atonai.ui.nav

/**
 * Typed navigation routes (05_UI_MAP.md §2). Using an object of constants keeps
 * the graph in one place while staying string-based so Navigation-Compose needs
 * no extra serialization dependency.
 */
object Routes {
    const val CHAT = "chat"
    const val SESSIONS = "sessions"
    const val CODE = "code"
    const val MCP = "mcp"
    const val SETTINGS = "settings"
    const val ADVANCED = "settings/advanced"
    const val PERMISSIONS = "settings/permissions"
    const val AUTO_SWITCH = "settings/autoswitch"
    const val PROVIDER_BACKUP = "settings/backup"

    /** Debug-only design gallery (M1), reached by tapping About five times. */
    const val GALLERY = "debug/gallery"
}
