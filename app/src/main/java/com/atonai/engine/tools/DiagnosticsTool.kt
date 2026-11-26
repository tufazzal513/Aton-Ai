package com.atonai.engine.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Cheap static checks the agent can run without a compiler (TOOLS_SPEC.md §14):
 * JSON validity and unbalanced brackets/quotes. This mirrors the on-device
 * `diagnostics` tool used while building AtonAI itself.
 */
class DiagnosticsTool : Tool {
    override val name = "diagnostics"
    override val readOnly = true
    override val description =
        "Check files for simple syntactic problems: invalid JSON and unbalanced " +
            "brackets or quotes. Not a compiler — it catches obvious mistakes only."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("paths") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
            }
        }
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive("paths"),
        )))
    }

    data class Issue(val path: String, val line: Int, val message: String)

    private val json = Json { isLenient = false }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        val paths = Args(args).strings("paths")
        if (paths.isEmpty()) return ToolResult.error("Provide at least one path")

        val issues = ArrayList<Issue>()
        for (raw in paths) {
            val file = runCatching { Sandbox.resolve(raw, ctx) }.getOrNull()
            if (file == null) {
                issues.add(Issue(raw, 0, "path is outside the workspace"))
                continue
            }
            if (!file.isFile) {
                issues.add(Issue(raw, 0, "not a file"))
                continue
            }
            val text = runCatching { file.readText() }.getOrNull()
            if (text == null) {
                issues.add(Issue(raw, 0, "not readable as text"))
                continue
            }
            val rel = Sandbox.display(file, ctx)
            when (file.extension.lowercase()) {
                "json" -> issues.addAll(checkJson(rel, text))
                "kt", "kts" -> issues.addAll(checkBrackets(rel, text))
                else -> Unit
            }
        }

        if (issues.isEmpty()) return ToolResult.ok("No issues found.")
        return ToolResult.ok(issues.joinToString("\n") { "${it.path}:${it.line}: ${it.message}" })
    }

    /** One issue per file when the whole document fails to parse. */
    fun checkJson(path: String, text: String): List<Issue> = runCatching<List<Issue>> {
        json.parseToJsonElement(text)
        emptyList()
    }.getOrElse { e: Throwable ->
        val line = extractLine(e.message)
        listOf(Issue(path, line, e.message?.substringBefore('\n') ?: "invalid JSON"))
    }

    /** Counts brackets outside strings and comments; reports the first imbalance. */
    fun checkBrackets(path: String, text: String): List<Issue> {
        val stack = ArrayDeque<Pair<Char, Int>>()
        var line = 1
        var i = 0
        var inString = false
        var inLineComment = false
        var inBlockComment = false
        val opens = mapOf('(' to ')', '{' to '}', '[' to ']')
        val closes = opens.values.toSet()

        while (i < text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            when {
                c == '\n' -> { line++; inLineComment = false }
                inLineComment -> Unit
                inBlockComment -> if (c == '*' && next == '/') { inBlockComment = false; i++ }
                inString -> when {
                    c == '\\' -> i++
                    c == '"' -> inString = false
                }
                c == '/' && next == '/' -> inLineComment = true
                c == '/' && next == '*' -> inBlockComment = true
                c == '"' -> inString = true
                c in opens -> stack.addLast(c to line)
                c in closes -> {
                    val top = stack.removeLastOrNull()
                    if (top == null) return listOf(Issue(path, line, "unexpected '$c'"))
                    if (opens[top.first] != c) {
                        return listOf(Issue(path, line, "'$c' does not match '${top.first}' on line ${top.second}"))
                    }
                }
            }
            i++
        }
        if (inString) return listOf(Issue(path, line, "unterminated string"))
        if (stack.isNotEmpty()) {
            val top = stack.last()
            return listOf(Issue(path, top.second, "'${top.first}' is never closed"))
        }
        return emptyList()
    }

    private fun extractLine(message: String?): Int =
        Regex("line (\\d+)").find(message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
