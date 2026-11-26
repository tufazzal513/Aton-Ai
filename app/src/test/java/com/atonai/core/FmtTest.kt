package com.atonai.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FmtTest {

    @Test
    fun bytes_scales_units() {
        assertEquals("512 B", Fmt.bytes(512))
        assertEquals("1.0 KB", Fmt.bytes(1024))
        assertEquals("1.5 MB", Fmt.bytes((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun tokens_uses_k_and_m() {
        assertEquals("999", Fmt.tokens(999))
        assertEquals("1.5k", Fmt.tokens(1500))
        assertEquals("2.0M", Fmt.tokens(2_000_000))
    }

    @Test
    fun usd_keeps_small_amounts_visible() {
        assertEquals("$0.00", Fmt.usd(0.0))
        assertEquals("$0.0030", Fmt.usd(0.003))
        assertEquals("$1.25", Fmt.usd(1.25))
    }

    @Test
    fun maskKey_never_returns_full_secret() {
        assertEquals("(none)", maskKey(null))
        assertEquals("(none)", maskKey(""))
        assertEquals("…", maskKey("short"))
        assertEquals("sk-a…yz", maskKey("sk-abcdefghijyz"))
    }
}
