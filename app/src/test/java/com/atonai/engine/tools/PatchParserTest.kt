package com.atonai.engine.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchParserTest {

    @Test
    fun empty_input_yields_no_operations() {
        assertTrue(PatchParser.parse("").isEmpty())
    }

    @Test
    fun add_file_collects_body_lines_without_the_plus() {
        val ops = PatchParser.parse(
            """
            *** Begin Patch
            *** Add File: src/New.kt
            +package demo
            +class New
            *** End Patch
            """.trimIndent(),
        )
        val add = ops.single() as PatchParser.Op.Add
        assertEquals("src/New.kt", add.path)
        assertEquals(listOf("package demo", "class New"), add.lines)
    }

    @Test
    fun delete_file_needs_no_body() {
        val ops = PatchParser.parse("*** Delete File: old.txt")
        assertEquals("old.txt", (ops.single() as PatchParser.Op.Delete).path)
    }

    @Test
    fun update_file_reads_hunk_lines() {
        val ops = PatchParser.parse(
            """
            *** Update File: a.kt
            @@ -2,1 +2,1 @@
            -old
            +new
            """.trimIndent(),
        )
        val update = ops.single() as PatchParser.Op.Update
        assertEquals("a.kt", update.path)
        val hunk = update.hunks.single()
        assertEquals('-', hunk[0].kind)
        assertEquals("old", hunk[0].text)
        assertEquals('+', hunk[1].kind)
        assertEquals("new", hunk[1].text)
    }

    @Test
    fun begin_and_end_lines_are_optional() {
        val withEnvelope = PatchParser.parse("*** Begin Patch\n*** Delete File: x\n*** End Patch")
        val withoutEnvelope = PatchParser.parse("*** Delete File: x")
        assertEquals(1, withEnvelope.size)
        assertEquals(1, withoutEnvelope.size)
    }

    @Test
    fun multiple_operations_keep_order() {
        val ops = PatchParser.parse(
            """
            *** Add File: a
            +x
            *** Update File: b
            @@
            -y
            +z
            *** Delete File: c
            """.trimIndent(),
        )
        assertEquals(3, ops.size)
        assertTrue(ops[0] is PatchParser.Op.Add)
        assertTrue(ops[1] is PatchParser.Op.Update)
        assertTrue(ops[2] is PatchParser.Op.Delete)
    }

    @Test
    fun hunk_start_line_is_parsed_from_the_marker() {
        val ops = PatchParser.parse("*** Update File: a\n@@ -12,3 +12,4 @@\n-a\n+b")
        val hunk = (ops.single() as PatchParser.Op.Update).hunks.single()
        assertEquals(12, hunk.first().line)
    }
}
