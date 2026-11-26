package com.atonai.engine.tools

import com.atonai.engine.skill.SkillLibrary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Loads a skill's full instructions on demand (M10). The name/description of
 * every skill is already in the system prompt; this tool pulls in the body only
 * when the agent decides a skill applies. Read-only, so it needs no permission.
 */
class SkillTool(
    private val library: SkillLibrary,
    /** Resolves the active session workdir at run time. */
    private val workdir: () -> String = { "" },
) : Tool {
    override val name = "skill"
    override val readOnly = true
    override val description =
        "Load a skill's full instructions and helper file list. Call it by name " +
            "(the available skills are listed in the system prompt)."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("name") {
                put("type", "string")
                put("description", "The skill ID, e.g. code-review")
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("name"))))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        val name = Args(args).str("name").trim()
        if (name.isEmpty()) return ToolResult.error("Provide a skill name")

        val dir = ctx.workdir.ifBlank { workdir() }
        val skill = library.find(dir, name)
            ?: return ToolResult.error(
                "No skill named \"$name\". Available: " +
                    library.discover(dir).joinToString(", ") { it.name }.ifBlank { "(none)" },
            )

        return ToolResult.ok(render(skill), extra = skill.description)
    }

    private fun render(skill: com.atonai.engine.skill.Skill): String = buildString {
        append("# Skill: ").append(skill.name).append("\n\n")
        if (skill.description.isNotBlank()) append(skill.description).append("\n\n")
        if (skill.directory.isNotBlank()) {
            append("Base directory for this skill: ").append(skill.directory).append("\n\n")
        }
        append(skill.body.trim()).append("\n")
        if (skill.files.isNotEmpty()) {
            append("\n<skill_files>\n")
            skill.files.forEach { append(it).append("\n") }
            append("</skill_files>\n")
        }
    }
}
