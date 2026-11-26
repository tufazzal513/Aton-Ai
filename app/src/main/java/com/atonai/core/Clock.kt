package com.atonai.core

import java.util.concurrent.atomic.AtomicLong

/**
 * Wall-clock helper. Wrapped so engine code never calls [System.currentTimeMillis]
 * directly and tests can freeze time.
 */
object Clock {
    private val override = AtomicLong(-1L)

    fun now(): Long = override.get().takeIf { it >= 0 } ?: System.currentTimeMillis()

    /** Test hook: pass a negative value to restore the real clock. */
    fun freeze(millis: Long) {
        override.set(millis)
    }
}
