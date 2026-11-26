package com.atonai.engine.tools

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WalkerTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "walker-${System.nanoTime()}")
        root.mkdirs()
        file("src/Main.kt")
        file("src/util/Helper.kt")
        file("src/util/HelperTest.kt")
        file("build/out.dex")
        file("node_modules/pkg/index.js")
        file(".git/config")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun file(rel: String) {
        val f = File(root, rel)
        f.parentFile?.mkdirs()
        f.writeText("x")
    }

    @Test
    fun always_ignored_directories_are_skipped() {
        val paths = Walker(root).files().map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
        assertFalse(paths.any { it.startsWith("build/") })
        assertFalse(paths.any { it.startsWith("node_modules/") })
        assertFalse(paths.any { it.startsWith(".git/") })
    }

    @Test
    fun normal_sources_are_listed() {
        val paths = walkPaths()
        assertTrue(paths.contains("src/Main.kt"))
        assertTrue(paths.contains("src/util/Helper.kt"))
    }

    @Test
    fun gitignore_name_rule_is_honoured() {
        File(root, ".gitignore").writeText("out.dex\n")
        file("src/out.dex")
        assertFalse(walkPaths().any { it.endsWith("out.dex") })
    }

    @Test
    fun gitignore_glob_rule_is_honoured() {
        File(root, ".gitignore").writeText("*.log\n")
        file("src/app.log")
        assertFalse(walkPaths().any { it.endsWith(".log") })
    }

    @Test
    fun glob_double_star_spans_directories() {
        val regex = Walker.globToRegex("**/*.kt")
        assertTrue(regex.matches("src/Main.kt"))
        assertTrue(regex.matches("src/util/Helper.kt"))
        assertFalse(regex.matches("src/Main.java"))
    }

    @Test
    fun glob_single_star_stays_in_one_segment() {
        val regex = Walker.globToRegex("src/*.kt")
        assertTrue(regex.matches("src/Main.kt"))
        assertFalse(regex.matches("src/util/Helper.kt"))
    }

    @Test
    fun glob_question_mark_matches_one_character() {
        val regex = Walker.globToRegex("a?.kt")
        assertTrue(regex.matches("ab.kt"))
        assertFalse(regex.matches("abc.kt"))
    }

    @Test
    fun rel_path_uses_forward_slashes() {
        val walker = Walker(root)
        assertEquals("src/Main.kt", walker.relPath(File(root, "src/Main.kt")))
    }

    @Test
    fun file_cap_is_respected() {
        val walker = Walker(root, maxFiles = 2)
        assertTrue(walker.files().size <= 2)
    }

    private fun walkPaths(): List<String> =
        Walker(root).files().map { it.relativeTo(root).path.replace(File.separatorChar, '/') }

    @Test
    fun is_inside_helper_matches_children_only() {
        assertTrue(Sandbox.isInside(File(root, "src/Main.kt"), root))
        assertFalse(Sandbox.isInside(File(root.parentFile, "elsewhere"), root))
    }

    @Test
    fun assert_relative_pattern_rejects_parent_tricks() {
        assertNotNull(runCatching { Sandbox.assertRelativePattern("src/**/*.kt") }.getOrNull())
        var threw = false
        try {
            Sandbox.assertRelativePattern("../secret")
        } catch (e: ToolError) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun unresolved_path_is_null_safe() {
        val ctx = ToolCtx(workdir = root.path)
        assertNotNull(Sandbox.resolve("src/Main.kt", ctx))
        assertNull(runCatching { Sandbox.resolve("../../etc/passwd", ctx) }.getOrNull())
    }
}
