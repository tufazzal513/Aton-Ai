package com.atonai.engine.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import com.atonai.data.perms.Action

/**
 * Applies a unified-style patch: `*** Begin Patch` blocks containing
 * `*** Update File:`, `*** Add File:`, `*** Delete File:` and `@@` hunks with
 * `-`/`+`/space lines. This is the format the original's prompt asks models to
 * produce, so the parser accepts it verbatim (TOOLS_SPEC.md §5).
 *
 * Every hunk is matched by its context lines; a failed hunk aborts the whole
 * patch **before** anything is written, so a partial apply cannot corrupt files.
 */
class PatchTool : Tool {
    override val name = "patch"
    override val description =
        "Apply a multi-file patch. Format:\n" +
            "*** Begin Patch\n" +
            "*** Update File: path\n@@ context\n-old line\n+new line\n" +
            "*** Add File: path\n+line\n" +
            "*** Delete File: path\n" +
            "*** End Patch"

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("patch") {
                put("type", "string")
                put("description", "The patch text, starting with *** Begin Patch")
            }
        }
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive("patch"),
        )))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        if (ctx.readOnly) return ToolResult.denied("This session is read-only.")
        val text = Args(args).str("patch")
        val ops = PatchParser.parse(text)
        if (ops.isEmpty()) return ToolResult.error("Patch has no file operations")
        ctx.permit.permit(Action.WRITE, ops.joinToString(", ") { it.path })

        // Phase 1: compute every result in memory.
        val results = ArrayList<Planned>(ops.size)
        for (op in ops) {
            val plan = when (op) {
                is PatchParser.Op.Add -> planAdd(op, ctx)
                is PatchParser.Op.Delete -> planDelete(op, ctx)
                is PatchParser.Op.Update -> planUpdate(op, ctx)
            }
            if (plan is Planned.Failed) return ToolResult.error(plan.message)
            results.add(plan)
        }

        // Phase 2: commit.
        val summary = StringBuilder()
        val diff = StringBuilder()
        for (plan in results) {
            when (plan) {
                is Planned.Added -> {
                    plan.file.parentFile?.mkdirs()
                    plan.file.writeText(plan.content)
                    summary.append("A ${Sandbox.display(plan.file, ctx)}\n")
                    diff.append(Diff.unified(Sandbox.display(plan.file, ctx), "", plan.content))
                }
                is Planned.Deleted -> {
                    plan.file.delete()
                    summary.append("D ${Sandbox.display(plan.file, ctx)}\n")
                }
                is Planned.Updated -> {
                    plan.file.writeText(plan.content)
                    summary.append("M ${Sandbox.display(plan.file, ctx)}\n")
                    diff.append(Diff.unified(Sandbox.display(plan.file, ctx), plan.before, plan.content))
                }
                is Planned.Failed -> Unit
            }
        }
        return ToolResult.ok(summary.toString().trim(), diff.toString())
    }

    // ---- planning --------------------------------------------------------

    private sealed interface Planned {
        data class Added(val file: java.io.File, val content: String) : Planned
        data class Deleted(val file: java.io.File) : Planned
        data class Updated(val file: java.io.File, val before: String, val content: String) : Planned
        data class Failed(val message: String) : Planned
    }

    private fun planAdd(op: PatchParser.Op.Add, ctx: ToolCtx): Planned = runCatching {
        val file = Sandbox.resolve(op.path, ctx)
        if (file.exists()) return Planned.Failed("${op.path} already exists; use *** Update File")
        Planned.Added(file, op.lines.joinToString("\n"))
    }.getOrElse { Planned.Failed(it.message ?: "bad path") }

    private fun planDelete(op: PatchParser.Op.Delete, ctx: ToolCtx): Planned = runCatching {
        val file = Sandbox.resolve(op.path, ctx)
        if (!file.isFile) return Planned.Failed("${op.path} does not exist")
        Planned.Deleted(file)
    }.getOrElse { Planned.Failed(it.message ?: "bad path") }

    private fun planUpdate(op: PatchParser.Op.Update, ctx: ToolCtx): Planned = runCatching {
        val file = Sandbox.resolve(op.path, ctx)
        if (!file.isFile) return Planned.Failed("${op.path} does not exist")
        val before = file.readText()
        val after = applyHunks(before, op)
            ?: return Planned.Failed("A hunk in ${op.path} did not match. Re-read the file and retry.")
        Planned.Updated(file, before, after)
    }.getOrElse { Planned.Failed(it.message ?: "bad path") }

    /** Applies hunks in order; returns null when a context block cannot be found. */
    private fun applyHunks(before: String, op: PatchParser.Op.Update): String? {
        var lines = before.lines().toMutableList()
        var searchFrom = 0
        for (hunk in op.hunks) {
            val oldBlock = hunk.filter { it.kind != '+' }.map { it.text }
            val newBlock = hunk.filter { it.kind != '-' }.map { it.text }

            if (oldBlock.isEmpty()) {
                // Pure insertion: append at the hinted position or at the end.
                val at = (hunk.firstOrNull()?.line ?: lines.size) - 1
                val pos = at.coerceIn(0, lines.size)
                lines.addAll(pos, newBlock)
                searchFrom = pos + newBlock.size
                continue
            }

            val at = indexOfBlock(lines, oldBlock, searchFrom)
                ?: return null
            repeat(oldBlock.size) { lines.removeAt(at) }
            lines.addAll(at, newBlock)
            searchFrom = at + newBlock.size
        }
        return lines.joinToString("\n")
    }

    private fun indexOfBlock(lines: List<String>, block: List<String>, from: Int): Int? {
        if (block.isEmpty() || block.size > lines.size) return null
        for (i in from..(lines.size - block.size)) {
            var ok = true
            for (k in block.indices) {
                if (lines[i + k].trimEnd() != block[k].trimEnd()) { ok = false; break }
            }
            if (ok) return i
        }
        return null
    }
}
