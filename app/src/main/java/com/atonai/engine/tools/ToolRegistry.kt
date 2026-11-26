package com.atonai.engine.tools

import com.atonai.engine.llm.ToolSpec
import com.atonai.engine.skill.SkillLibrary

/**
 * Holds every [Tool] by wire name and converts them to the [ToolSpec] list the
 * model sees. Order is fixed so prompt caching stays stable between steps.
 */
class ToolRegistry(tools: List<Tool> = defaults()) {

    private val byName: Map<String, Tool> = tools.associateBy { it.name }

    val tools: List<Tool> = tools

    fun get(name: String): Tool? = byName[name]

    /** Specs filtered for the session; read-only sessions hide mutating tools. */
    fun specs(readOnly: Boolean): List<ToolSpec> = tools
        .filter { !readOnly || it.readOnly }
        .map { ToolSpec(name = it.name, description = it.description, schema = it.schema) }

    companion object {
        /**
         * The full tool set (M6 file tools + M9 shell/web/plan/diagnostics/code
         * + M10 skill). Order is fixed so the prompt cache stays stable between
         * steps. Without a [skills] library the `skill` tool is omitted.
         */
        fun defaults(skills: SkillLibrary? = null): List<Tool> = buildList {
            add(ReadTool())
            add(WriteTool())
            add(EditTool())
            add(PatchTool())
            add(GlobTool())
            add(GrepTool())
            add(ShellTool())
            add(WebFetchTool())
            add(WebSearchTool())
            add(TodoTool())
            add(QuestionTool())
            add(DiagnosticsTool())
            add(CodeTool())
            if (skills != null) add(SkillTool(skills))
        }
    }
}
