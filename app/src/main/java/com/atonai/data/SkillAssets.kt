package com.atonai.data

import android.content.Context
import com.atonai.engine.skill.Skill
import com.atonai.engine.skill.SkillLibrary
import com.atonai.engine.skill.SkillParser

/**
 * Loads the app's bundled skills from `assets/skills/<name>/SKILL.md` (M10).
 * Every folder directly under `assets/skills` that has a `SKILL.md` becomes a
 * skill; the folder name is the fallback id when the frontmatter omits `name`.
 */
class SkillAssets(private val context: Context) {

    /** Built once on first use; the assets never change at runtime. */
    val library: SkillLibrary by lazy { SkillLibrary(loadBundled()) }

    private fun loadBundled(): List<Skill> = runCatching {
        val assets = context.assets
        val names = assets.list("skills")?.filterNotNull()?.sorted().orEmpty()
        names.mapNotNull { dir ->
            val path = "skills/$dir/SKILL.md"
            val text = runCatching {
                assets.open(path).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null

            val files = runCatching {
                assets.list("skills/$dir")?.filterNotNull()
                    ?.filter { it != "SKILL.md" }?.sorted().orEmpty()
            }.getOrDefault(emptyList())

            SkillParser.parse(text, fallbackName = dir, directory = "", files = files)
        }
    }.getOrDefault(emptyList())

    /** The `<available_skills>` block for the system prompt, or "". */
    fun prompt(workdir: String): String = library.prompt(workdir)
}
