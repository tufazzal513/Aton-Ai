package com.atonai.engine.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PatchToolTest {

    private lateinit var root: File
    private lateinit var ctx: ToolCtx

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "patch-${System.nanoTime()}")
        root.mkdirs()
        ctx = ToolCtx(workdir = root.path)
        File(root, "a.kt").writeText("one\ntwo\nthree\n")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun patch(text: String) = buildJsonObject { put("patch", text) }

    @Test
    fun update_replaces_a_matched_block() = runBlocking {
        val result = PatchTool().run(
            patch("*** Update File: a.kt\n@@\n-two\n+TWO"),
            ctx,
        )
        assertFalse(result.error)
        assertEquals("one\nTWO\nthree\n", File(root, "a.kt").readText())
    }

    @Test
    fun add_creates_a_new_file() = runBlocking {
        val result = PatchTool().run(
            patch("*** Add File: new.kt\n+fresh line"),
            ctx,
        )
        assertFalse(result.error)
        assertEquals("fresh line", File(root, "new.kt").readText())
    }

    @Test
    fun delete_removes_the_file() = runBlocking {
        val result = PatchTool().run(patch("*** Delete File: a.kt"), ctx)
        assertFalse(result.error)
        assertFalse(File(root, "a.kt").exists())
    }

    @Test
    fun a_failing_hunk_aborts_before_any_write() = runBlocking {
        val result = PatchTool().run(
            patch(
                "*** Update File: a.kt\n@@\n-two\n+TWO\n" +
                    "*** Update File: b.kt\n@@\n-not there\n+whatever",
            ),
            ctx,
        )
        assertTrue(result.error)
        // a.kt must be untouched because the plan failed before the commit.
        assertEquals("one\ntwo\nthree\n", File(root, "a.kt").readText())
    }

    @Test
    fun summary_lists_changed_files() = runBlocking {
        val result = PatchTool().run(
            patch("*** Add File: x.kt\n+x"),
            ctx,
        )
        assertTrue(result.output.startsWith("A x.kt"))
    }

    @Test
    fun read_only_session_is_denied() = runBlocking {
        val result = PatchTool().run(
            patch("*** Delete File: a.kt"),
            ctx.copy(readOnly = true),
        )
        assertTrue(result.error)
        assertTrue(File(root, "a.kt").exists())
    }

    @Test
    fun empty_patch_is_an_error() = runBlocking {
        val result = PatchTool().run(patch("not a patch"), ctx)
        assertTrue(result.error)
    }
}
