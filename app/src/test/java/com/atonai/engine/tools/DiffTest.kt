package com.atonai.engine.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffTest {

    @Test
    fun identical_text_produces_no_diff() {
        assertEquals("", Diff.unified("a.kt", "one\ntwo\n", "one\ntwo\n"))
    }

    @Test
    fun a_changed_line_shows_as_minus_and_plus() {
        val diff = Diff.unified("a.kt", "one\ntwo\n", "one\nTWO\n")
        assertTrue(diff.contains("--- a/a.kt"))
        assertTrue(diff.contains("+++ b/a.kt"))
        assertTrue(diff.contains("-two"))
        assertTrue(diff.contains("+TWO"))
    }

    @Test
    fun hunk_header_counts_match_the_body() {
        val diff = Diff.unified("a.kt", "a\nb\nc\n", "a\nx\nc\n")
        assertTrue(diff.contains("@@ -1,3 +1,3 @@"))
    }

    @Test
    fun additions_are_counted_by_stats() {
        val (added, removed) = Diff.stats("a\nb\n", "a\nb\nc\n")
        assertEquals(1, added)
        assertEquals(0, removed)
    }

    @Test
    fun deletions_are_counted_by_stats() {
        val (added, removed) = Diff.stats("a\nb\nc\n", "a\nc\n")
        assertEquals(0, added)
        assertEquals(1, removed)
    }

    @Test
    fun line_numbers_are_one_based() {
        val diff = Diff.unified("a.kt", "l1\nl2\n", "l1\nchanged\n")
        assertTrue(diff.contains("@@ -1,2 +1,2 @@"))
    }
}
