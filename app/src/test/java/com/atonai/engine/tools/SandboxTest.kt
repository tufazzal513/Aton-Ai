package com.atonai.engine.tools

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SandboxTest {

    private lateinit var root: File
    private lateinit var ctx: ToolCtx

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "sandbox-${System.nanoTime()}")
        root.mkdirs()
        ctx = ToolCtx(workdir = root.path)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun relative_path_resolves_inside_the_workdir() {
        val resolved = Sandbox.resolve("src/Main.kt", ctx)
        assertEquals(File(root, "src/Main.kt").canonicalPath, resolved.path)
    }

    @Test
    fun tilde_resolves_to_the_workdir() {
        assertEquals(root.canonicalPath, Sandbox.resolve("~", ctx).path)
    }

    @Test
    fun absolute_path_inside_is_allowed() {
        val inside = File(root, "a.txt")
        assertEquals(inside.canonicalPath, Sandbox.resolve(inside.path, ctx).path)
    }

    @Test
    fun parent_traversal_is_rejected() {
        var threw = false
        try {
            Sandbox.resolve("../escape.txt", ctx)
        } catch (e: ToolError) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun absolute_path_outside_is_rejected() {
        var threw = false
        try {
            Sandbox.resolve("/etc/passwd", ctx)
        } catch (e: ToolError) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun empty_path_is_rejected() {
        var threw = false
        try {
            Sandbox.resolve("  ", ctx)
        } catch (e: ToolError) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun windows_separators_are_normalised() {
        val resolved = Sandbox.resolve("src\\Main.kt", ctx)
        assertTrue(resolved.path.endsWith("Main.kt"))
        assertTrue(Sandbox.isInside(resolved, root.canonicalFile))
    }

    @Test
    fun display_is_relative_to_the_workdir() {
        val file = File(root, "src/Main.kt")
        assertEquals("src/Main.kt", Sandbox.display(file, ctx).replace(File.separatorChar, '/'))
    }

    @Test
    fun workdir_is_created_when_missing() {
        val fresh = File(root, "nested/deep")
        assertFalse(fresh.exists())
        Sandbox.resolve("x.txt", ToolCtx(workdir = fresh.path))
        assertTrue(fresh.exists())
    }
}
