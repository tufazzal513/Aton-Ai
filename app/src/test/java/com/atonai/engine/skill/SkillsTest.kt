package com.atonai.engine.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SkillsTest {

    private val withFrontmatter = """
        ---
        name: code-review
        description: Review a diff for bugs.
        ---

        # Code Review

        Read the file first.
    """.trimIndent()

    @Test
    fun parses_frontmatter_name_description_and_body() {
        val skill = SkillParser.parse(withFrontmatter, fallbackName = "folder")
        assertEquals("code-review", skill.name)
        assertEquals("Review a diff for bugs.", skill.description)
        assertTrue(skill.body.startsWith("# Code Review"))
        assertTrue(!skill.body.contains("description:"))
    }

    @Test
    fun falls_back_to_folder_name_and_keeps_whole_body_when_no_frontmatter() {
        val skill = SkillParser.parse("# Spike\nTry it.", fallbackName = "spike")
        assertEquals("spike", skill.name)
        assertEquals("", skill.description)
        assertEquals("# Spike\nTry it.", skill.body)
    }

    @Test
    fun tolerates_crlf_frontmatter() {
        val crlf = "---\r\nname: spike\r\ndescription: Try things.\r\n---\r\n\r\nBody.\r\n"
        val skill = SkillParser.parse(crlf, fallbackName = "x")
        assertEquals("spike", skill.name)
        assertEquals("Try things.", skill.description)
        assertEquals("Body.", skill.body)
    }

    @Test
    fun missing_closing_fence_leaves_text_intact() {
        val broken = "---\nname: x\ndescription: y\n# no closing fence"
        val skill = SkillParser.parse(broken, fallbackName = "fb")
        assertEquals("fb", skill.name)
        assertTrue(skill.body.startsWith("---"))
    }

    @Test
    fun prompt_is_empty_without_skills() {
        assertEquals("", SkillLibrary().prompt(""))
    }

    @Test
    fun prompt_lists_bundled_skills_with_descriptions() {
        val lib = SkillLibrary(listOf(Skill("spike", "Try a prototype.", "body")))
        val block = lib.prompt("")
        assertTrue(block.contains("<available_skills>"))
        assertTrue(block.contains("<name>spike</name>"))
        assertTrue(block.contains("Try a prototype."))
        assertTrue(block.contains("</available_skills>"))
    }

    @Test
    fun discovers_project_skills_under_agents_dir_and_overrides_bundled() {
        val root = File(System.getProperty("java.io.tmpdir"), "skills-${System.nanoTime()}")
        val dir = File(root, ".agents/skills/spike")
        dir.mkdirs()
        File(dir, "SKILL.md").writeText("---\nname: spike\ndescription: project version\n---\nProject body.")
        File(dir, "reference.md").writeText("extra")

        val lib = SkillLibrary(listOf(Skill("spike", "bundled version", "bundled")))
        val found = lib.discover(root.path)

        assertEquals(1, found.size)
        assertEquals("project version", found.first().description)
        assertEquals(listOf("reference.md"), found.first().files)
        root.deleteRecursively()
    }

    @Test
    fun find_is_case_insensitive() {
        val lib = SkillLibrary(listOf(Skill("code-review", "d", "b")))
        assertTrue(lib.find("", "CODE-REVIEW") != null)
    }
}
