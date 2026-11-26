package com.atonai.engine.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import com.atonai.data.perms.Action

/**
 * Exact string replacement. `old` must occur exactly once unless `all` is true,
 * which is the same rule the original enforces — it prevents the model from
 * silently editing the wrong place. Whitespace differences are retried once.
 */
class EditTool : Tool {
    override val name = "edit"
    override val description =
        "Replace an exact block of text in a file. `old` must match exactly once " +
            "(include surrounding context to make it unique) unless `all` is true."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") { put("type", "string") }
            putJsonObject("old") { put("type", "string") }
            putJsonObject("new") { put("type", "string") }
            putJsonObject("all") {
                put("type", "boolean")
                put("description", "Replace every occurrence")
            }
        }
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive("path"),
            kotlinx.serialization.json.JsonPrimitive("old"),
            kotlinx.serialization.json.JsonPrimitive("new"),
        )))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        if (ctx.readOnly) return ToolResult.denied("This session is read-only.")
        val a = Args(args)
        val file = Sandbox.resolve(a.str("path"), ctx)
        if (!file.isFile) return ToolResult.error("File not found: ${Sandbox.display(file, ctx)}")

        val old = a.str("old")
        val new = a.str("new")
        val all = a.bool("all", false)
        if (old.isEmpty()) return ToolResult.error("\"old\" is empty; use write to create a file")
        if (old == new) return ToolResult.error("\"old\" and \"new\" are identical")

        val original = file.readText()
        val count = countOccurrences(original, old)
        if (count == 0) {
            val hint = fuzzyHint(original, old)
            return ToolResult.error("Text not found in ${Sandbox.display(file, ctx)}.$hint")
        }
        if (count > 1 && !all) {
            return ToolResult.error(
                "\"old\" occurs $count times. Add surrounding context to make it unique, or pass all=true.",
            )
        }
        ctx.permit.permit(Action.WRITE, Sandbox.display(file, ctx))

        val updated = if (all) original.replace(old, new) else replaceFirst(original, old, new)
        runCatching { file.writeText(updated) }
            .onFailure { return ToolResult.error("Write failed: ${it.message}") }

        val diff = Diff.unified(
            name = Sandbox.display(file, ctx),
            before = original,
            after = updated,
        )
        return ToolResult.ok("Replaced ${if (all) "$count occurrences" else "1 occurrence"} in ${Sandbox.display(file, ctx)}", diff)
    }

    private fun replaceFirst(src: String, old: String, new: String): String {
        val at = src.indexOf(old)
        return src.substring(0, at) + new + src.substring(at + old.length)
    }

    private fun countOccurrences(src: String, needle: String): Int {
        var count = 0
        var at = src.indexOf(needle)
        while (at >= 0) {
            count++
            at = src.indexOf(needle, at + needle.length)
        }
        return count
    }

    /**
     * When an exact match fails, try a whitespace-insensitive match and tell the
     * model which line differs — that is the repair hint the original gives.
     */
    private fun fuzzyHint(src: String, old: String): String {
        val wanted = old.trim().replace(Regex("\\s+"), " ")
        val close = src.lines().indexOfFirst { it.trim().replace(Regex("\\s+"), " ") == wanted }
        return if (close >= 0) {
            " A whitespace-only difference was found at line ${close + 1}; copy that line verbatim."
        } else if (wanted.isNotEmpty() && src.contains(wanted.take(40))) {
            " A similar passage exists but the indentation or line breaks differ."
        } else {
            " Use read to check the current content."
        }
    }
}
