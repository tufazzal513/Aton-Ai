package com.atonai.core

import java.util.UUID

/**
 * Small identifiers used across the app. Kept as an object so a test can swap
 * the generator for deterministic values.
 */
object Ids {

    /** A fresh opaque id. Not a UUID string format anyone depends on. */
    fun new(): String = UUID.randomUUID().toString()

    /** Ids the agent used to derive from a timestamp + counter. */
    fun short(): String = UUID.randomUUID().toString().take(8)
}

/** Empty-or-single-line helper used by many labels. */
fun String?.orDash(): String = if (this.isNullOrBlank()) "—" else this
