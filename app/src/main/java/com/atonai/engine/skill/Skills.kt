package com.atonai.engine.skill

import java.io.File

/**
 * One discovered skill (M10). A skill is a folder with a `SKILL.md` whose
 * frontmatter carries `name` and `description`; the body holds the instructions
 * the agent reads on demand via the `skill` tool.
 */
data class Skill(
    val name: String,
    val description: String,
    val body: String,
    /** Absolute folder shown to the model; empty for bundled asset skills. */
    val directory: String = "",
    /** Helper files next to `SKILL.md`, read on demand. */
    val files: List<String> = emptyList(),
)

/**
 * Parses the `SKILL.md` frontmatter + body. Pure and free of I/O so it is
 * unit-testable; discovery lives in [SkillLibrary].
 */
object SkillParser {

    fun parse(
        text: String,
        fallbackName: String,
        directory: String = "",
        files: List<String> = emptyList(),
    ): Skill {
        val normalized = text.replace("\r\n", "\n")
        var name = fallbackName
        var description = ""
        var body = normalized

        if (normalized.startsWith("---\n")) {
            val end = normalized.indexOf("\n---", 4)
            if (end >= 0) {
                val front = normalized.substring(4, end)
                body = normalized.substring(end + 4).trimStart('\n')
                front.lineSequence().forEach { line ->
                    val idx = line.indexOf(':')
                    if (idx <= 0) return@forEach
                    val key = line.substring(0, idx).trim().lowercase()
                    val value = line.substring(idx + 1).trim()
                    when (key) {
                        "name" -> if (value.isNotBlank()) name = value
                        "description" -> description = value
                    }
                }
            }
        }
        return Skill(name, description, body.trim(), directory, files)
    }
}

/**
 * Collects the skills the agent can see: the app's bundled skills plus any
 * project skills under `<workdir>/.agents/skills/<name>/SKILL.md`. Project
 * skills override bundled ones with the same name.
 */
class SkillLibrary(private val bundled: List<Skill> = emptyList()) {

    fun discover(workdir: String): List<Skill> {
        val out = LinkedHashMap<String, Skill>()
        bundled.forEach { out[it.name] = it }
        workspaceSkills(workdir).forEach { out[it.name] = it }
        return out.values.toList()
    }

    fun find(workdir: String, name: String): Skill? =
        discover(workdir).firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * The `<available_skills>` block appended to the system prompt so the model
     * knows which skills exist and when to load them. Empty when there are none.
     */
    fun prompt(workdir: String): String {
        val skills = discover(workdir)
        if (skills.isEmpty()) return ""
        return buildString {
            append("<available_skills>\n")
            skills.forEach { skill ->
                append("  <skill>\n")
                append("    <name>").append(skill.name).append("</name>\n")
                append("    <description>").append(skill.description).append("</description>\n")
                append("  </skill>\n")
            }
            append("</available_skills>\n")
            append("Load a skill's full instructions with the `skill` tool before using it.")
        }
    }

    private fun workspaceSkills(workdir: String): List<Skill> {
        if (workdir.isBlank()) return emptyList()
        val root = File(workdir, ".agents/skills")
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return dirs.sortedBy { it.name }.mapNotNull { dir ->
            val md = File(dir, "SKILL.md")
            if (!md.isFile) return@mapNotNull null
            val text = runCatching { md.readText() }.getOrNull() ?: return@mapNotNull null
            val files = dir.listFiles()
                ?.filter { it.isFile && it.name != "SKILL.md" }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()
            SkillParser.parse(text, fallbackName = dir.name, directory = dir.path, files = files)
        }
    }
}
