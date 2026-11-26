package com.atonai.engine.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class EditToolTest {

    private lateinit var root: File
    private lateinit var ctx: ToolCtx

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "edit-${System.nanoTime()}")
        root.mkdirs()
        ctx = ToolCtx(workdir = root.path)
        File(root, "a.kt").writeText("line one\nline two\nline three\n")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun args(path: String, old: String, new: String, all: Boolean = false) = buildJsonObject {
        put("path", path)
        put("old", old)
        put("new", new)
        if (all) put("all", true)
    }

    @Test
    fun unique_match_is_replaced() = runBlocking {
        val result = EditTool().run(args("a.kt", "line two", "LINE 2"), ctx)
        assertFalse(result.error)
        assertTrue(File(root, "a.kt").readText().contains("LINE 2"))
    }

    @Test
    fun ambiguous_match_is_rejected() = runBlocking {
        File(root, "dup.kt").writeText("same\nsame\n")
        val result = EditTool().run(args("dup.kt", "same", "changed"), ctx)
        assertTrue(result.error)
        assertTrue(result.output.contains("occurs 2 times"))
        assertTrue(File(root, "dup.kt").readText().contains("same"))
    }

    @Test
    fun all_true_replaces_every_occurrence() = runBlocking {
        File(root, "dup.kt").writeText("same\nsame\n")
        val result = EditTool().run(args("dup.kt", "same", "changed", all = true), ctx)
        assertFalse(result.error)
        assertFalse(File(root, "dup.kt").readText().contains("same"))
    }

    @Test
    fun missing_text_reports_a_hint() = runBlocking {
        val result = EditTool().run(args("a.kt", "not present", "x"), ctx)
        assertTrue(result.error)
        assertTrue(result.output.contains("not found", ignoreCase = true))
    }

    @Test
    fun diff_is_returned_in_extra() = runBlocking {
        val result = EditTool().run(args("a.kt", "line two", "LINE 2"), ctx)
        assertTrue(result.extra.contains("-line two"))
        assertTrue(result.extra.contains("+LINE 2"))
    }

    @Test
    fun read_only_session_is_denied() = runBlocking {
        val ro = ctx.copy(readOnly = true)
        val result = EditTool().run(args("a.kt", "line two", "x"), ro)
        assertTrue(result.error)
        assertTrue(File(root, "a.kt").readText().contains("line two"))
    }

    @Test
    fun identical_old_and_new_is_rejected() = runBlocking {
        val result = EditTool().run(args("a.kt", "line two", "line two"), ctx)
        assertTrue(result.error)
    }
}
