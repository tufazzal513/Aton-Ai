package com.atonai.engine.tools

import com.atonai.engine.skill.Skill
import com.atonai.engine.skill.SkillLibrary
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillToolTest {

    private val lib = SkillLibrary(
        listOf(
            Skill("code-review", "Review a diff.", "# Code Review\nRead first.", files = listOf("reference.md")),
            Skill("spike", "Try a prototype.", "body"),
        ),
    )

    @Test
    fun returns_body_description_and_files() = runBlocking {
        val result = SkillTool(lib).run(buildJsonObject { put("name", "code-review") }, ToolCtx(workdir = ""))
        assertFalse(result.error)
        assertTrue(result.output.contains("# Skill: code-review"))
        assertTrue(result.output.contains("Review a diff."))
        assertTrue(result.output.contains("# Code Review"))
        assertTrue(result.output.contains("<skill_files>"))
        assertTrue(result.output.contains("reference.md"))
    }

    @Test
    fun unknown_name_lists_available_skills() = runBlocking {
        val result = SkillTool(lib).run(buildJsonObject { put("name", "nope") }, ToolCtx(workdir = ""))
        assertTrue(result.error)
        assertTrue(result.output.contains("code-review"))
        assertTrue(result.output.contains("spike"))
    }

    @Test
    fun blank_name_is_rejected() = runBlocking {
        val result = SkillTool(lib).run(buildJsonObject { put("name", "  ") }, ToolCtx(workdir = ""))
        assertTrue(result.error)
    }
}
