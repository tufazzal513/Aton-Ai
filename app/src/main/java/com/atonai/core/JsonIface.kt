package com.atonai.core

import kotlinx.serialization.json.Json

/** The one JSON configuration used for every persisted file in the app. */
object JsonIface {
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Compact form for wire payloads and single-line storage. */
    val compact: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}
