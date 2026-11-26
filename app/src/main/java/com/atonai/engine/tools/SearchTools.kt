package com.atonai.engine.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Finds files by glob pattern (`**\/*.kt`, `src/**\/*Test.kt`). Results are
 * workspace-relative, sorted by modification time (newest first) like the
 * original, then by path.
 */
class GlobTool : Tool {
    override val name = "glob"
    override val readOnly = true
    override val description =
        "List files whose path matches a glob pattern. `**` spans directories. " +
            "Ignore rules (.gitignore and build/VCS folders) are applied."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("pattern") {
                put("type", "string")
                put("description", "Glob, e.g. **/*.kt")
            }
            putJsonObject("path") {
                put("type", "string")
                put("description", "Optional sub-directory to search in")
            }
        }
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive("pattern"),
        )))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        val a = Args(args)
        val pattern = a.str("pattern")
        Sandbox.assertRelativePattern(pattern)
        val start = a.optStr("path")?.let { Sandbox.resolve(it, ctx) } ?: File(ctx.workdir)

        if (!start.exists()) return ToolResult.error("Folder not found: ${Sandbox.display(start, ctx)}")

        val walker = Walker(File(ctx.workdir))
        val regex = Walker.globToRegex(pattern.removePrefix("./"))
        val matches = walker.files(if (start.isDirectory) start else start.parentFile ?: start)
            .filter { regex.matches(walker.relPath(it)) }
            .sortedByDescending { it.lastModified() }
            .take(MAX_RESULTS)

        if (matches.isEmpty()) return ToolResult.ok("No files match $pattern")
        return ToolResult.ok(matches.joinToString("\n") { walker.relPath(it) })
    }

    private companion object {
        const val MAX_RESULTS = 300
    }
}

/**
 * Regex search across the workspace, `file:line:text` output, with optional
 * glob filter, case sensitivity, context lines and a hit cap.
 */
class GrepTool : Tool {
    override val name = "grep"
    override val readOnly = true
    override val description =
        "Search file contents with a regular expression. Returns file:line:text. " +
            "Use `glob` to restrict file types and `ignoreCase` for case-insensitive search."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("pattern") { put("type", "string") }
            putJsonObject("glob") { put("type", "string"); put("description", "e.g. *.kt") }
            putJsonObject("path") { put("type", "string") }
            putJsonObject("context") { put("type", "integer"); put("description", "Lines before/after") }
            putJsonObject("ignoreCase") { put("type", "boolean") }
            putJsonObject("limit") { put("type", "integer") }
        }
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive("pattern"),
        )))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        val a = Args(args)
        val pattern = a.str("pattern")
        val ignoreCase = a.bool("ignoreCase", false)
        val context = a.int("context", 0).coerceIn(0, 5)
        val limit = a.int("limit", 120).coerceIn(1, 500)
        val glob = a.optStr("glob")?.let { Walker.globToRegex(it) }

        val regex = runCatching {
            if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        }.getOrElse { return ToolResult.error("Invalid regex: ${it.message}") }

        val start = a.optStr("path")?.let { Sandbox.resolve(it, ctx) } ?: File(ctx.workdir)
        val walker = Walker(File(ctx.workdir))
        val files = if (start.isFile) listOf(start) else walker.files(start)

        val out = ArrayList<String>(limit)
        var hits = 0
        var scanned = 0
        for (file in files) {
            if (hits >= limit) break
            if (glob != null && !glob.matches(file.name) && !glob.matches(walker.relPath(file))) continue
            if (file.length() > MAX_FILE_BYTES) continue
            val lines = runCatching { file.readLines() }.getOrNull() ?: continue
            scanned++
            for ((idx, line) in lines.withIndex()) {
                if (!regex.containsMatchIn(line)) continue
                if (hits >= limit) break
                val rel = walker.relPath(file)
                if (context > 0) {
                    if (out.isNotEmpty()) out.add("--")
                    val from = (idx - context).coerceAtLeast(0)
                    val to = (idx + context).coerceAtMost(lines.lastIndex)
                    for (k in from..to) {
                        val mark = if (k == idx) ":" else "-"
                        out.add("$rel$mark${k + 1}$mark${lines[k].take(MAX_LINE)}")
                    }
                } else {
                    out.add("$rel:${idx + 1}:${line.take(MAX_LINE)}")
                }
                hits++
            }
        }

        if (hits == 0) return ToolResult.ok("No matches for $pattern")
        val tail = if (hits >= limit) "\n… stopped at $limit matches (scanned $scanned files)" else ""
        return ToolResult.ok(out.joinToString("\n") + tail)
    }

    private companion object {
        const val MAX_FILE_BYTES = 1_000_000L
        const val MAX_LINE = 400
    }
}
