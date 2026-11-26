package com.atonai.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class PrefsTest {

    @Test
    fun themeMode_parses_known_ids() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.from("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.from("dark"))
    }

    @Test
    fun themeMode_falls_back_to_system() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("nonsense"))
    }

    @Test
    fun defaults_are_sane() {
        val p = Prefs()
        assertEquals("local", p.mode)
        assertEquals(ThemeMode.SYSTEM, p.themeMode)
        assertEquals(80, p.compactionThreshold)
        assertEquals(32_000, p.toolOutputCap)
    }
}
