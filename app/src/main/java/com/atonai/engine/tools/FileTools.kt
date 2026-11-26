package com.atonai.engine.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import com.atonai.data.perms.Action
import java.io.File

/**
 * Reads a text file, with optional `offset`/`limit` in **lines** and a cap on
 * characters so a huge file cannot blow up the context (TOOLS_SPEC.md §2).
 * Output is prefixed with 1-based line numbers in the `N: text` form, because
 * models then quote line numbers correctly in [EditTool].
 */
class ReadTool : Tool {
    override val name = "read"
    override val readOnly = true
    override val description =
        "Read a UTF-8 text file from the workspace. Returns numbered lines. " +
            "Use offset/limit for large files."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Workspace-relative path")
            }
            putJsonObject("offset") {
                put("type", "integer")
                put("description", "First line (1-based)")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Number of lines to return")
            }
        }
        put("required", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("path"))))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        val a = Args(args)
        val file = Sandbox.resolve(a.str("path"), ctx)
        if (!file.exists()) return ToolResult.error("File not found: ${Sandbox.display(file, ctx)}")
        if (file.isDirectory) return ToolResult.error("${Sandbox.display(file, ctx)} is a directory; use glob")

        val bytes = file.length()
        if (bytes > MAX_BYTES) {
            return ToolResult.error(
                "File is ${bytes / 1024} KB, larger than the ${MAX_BYTES / 1024} KB read limit. " +
                    "Use offset/limit or grep.",
            )
        }

        val text = runCatching { file.readText() }
            .getOrElse { return ToolResult.error("Cannot read as UTF-8 text (${it.message})") }

        val lines = text.lines()
        val from = (a.int("offset", 1) - 1).coerceIn(0, maxOf(0, lines.size - 1))
        val wanted = a.int("limit", DEFAULT_LINES).coerceIn(1, MAX_LINES)
        val slice = lines.drop(from).take(wanted)

        val body = slice.mapIndexed { i, line ->
            val n = from + i + 1
            if (line.length > MAX_LINE) "$n: ${line.take(MAX_LINE)}…" else "$n: $line"
        }.joinToString("\n")

        val more = lines.size - (from + slice.size)
        val tail = if (more > 0) "\n… $more more lines (pass offset=${from + slice.size + 1})" else ""
        return ToolResult.ok(body + tail)
    }

    companion object {
        private const val MAX_BYTES = 512 * 1024
        private const val MAX_LINES = 2000
        private const val DEFAULT_LINES = 600
        private const val MAX_LINE = 2000
    }
}

/** Creates or overwrites a file, creating parent directories. */
class WriteTool : Tool {
    override val name = "write"
    override val description =
        "Create a file or replace its whole content. Parent directories are created. " +
            "For partial changes prefer edit."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") { put("type", "string") }
            putJsonObject("content") { put("type", "string") }
        }
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive("path"),
            kotlinx.serialization.json.JsonPrimitive("content"),
        )))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        if (ctx.readOnly) return ToolResult.denied("This session is read-only.")
        val a = Args(args)
        val file = Sandbox.resolve(a.str("path"), ctx)
        ctx.permit.permit(Action.WRITE, Sandbox.display(file, ctx))
        val content = a.str("content", "")
        if (content.length > MAX_BYTES) {
            return ToolResult.error("Content is too large (${content.length} chars)")
        }
        val existed = file.exists()
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }.onFailure { return ToolResult.error("Write failed: ${it.message}") }

        val lines = content.count { it == '\n' } + if (content.isEmpty()) 0 else 1
        return ToolResult.ok(
            "${if (existed) "Updated" else "Created"} ${Sandbox.display(file, ctx)} ($lines lines)",
        )
    }

    private companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}
