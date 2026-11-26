package com.atonai.engine.tools

import com.atonai.data.perms.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs a shell command inside the workspace (TOOLS_SPEC.md §7). The timeout is
 * capped at [MAX_TIMEOUT_S] seconds — the recovered hard limit — and output is
 * truncated rather than allowed to blow up the context. A non-zero exit code is
 * reported in the output, not thrown.
 */
class ShellTool : Tool {
    override val name = "shell"
    override val action = Action.SHELL
    override val description =
        "Run a shell command in the workspace. Returns stdout+stderr and the exit " +
            "code. Timeout is capped at $MAX_TIMEOUT_S seconds."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("command") { put("type", "string") }
            putJsonObject("timeout") {
                put("type", "integer")
                put("description", "Seconds, maximum $MAX_TIMEOUT_S")
            }
        }
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive("command"),
        )))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        if (ctx.readOnly) return ToolResult.denied("This session is read-only.")
        val a = Args(args)
        val command = a.str("command")
        if (command.isBlank()) return ToolResult.error("Command is empty")
        // Ask once, keyed on the command so a rule can allow `git status`.
        ctx.permit.permit(Action.SHELL, command)

        val timeout = a.int("timeout", DEFAULT_TIMEOUT_S).coerceIn(1, MAX_TIMEOUT_S)
        val workdir = File(ctx.workdir).apply { mkdirs() }

        return withContext(Dispatchers.IO) {
            runCatching { execute(command, workdir, timeout) }
                .getOrElse { ToolResult.error("Shell failed: ${it.message}") }
        }
    }

    private fun execute(command: String, workdir: File, timeout: Int): ToolResult {
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(workdir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ToolResult.error("Command timed out after ${timeout}s.\n${tail(output)}")
        }

        val code = process.exitValue()
        val body = tail(output)
        val status = if (code == 0) "ok" else "error"
        return ToolResult(
            output = if (code == 0) body.ifBlank { "(no output)" } else "exit $code\n$body",
            status = status,
            error = code != 0,
        )
    }

    private fun tail(text: String): String =
        if (text.length <= MAX_OUTPUT) text else "...[truncated]\n" + text.takeLast(MAX_OUTPUT)

    companion object {
        const val MAX_TIMEOUT_S = 120
        const val DEFAULT_TIMEOUT_S = 60
        private const val MAX_OUTPUT = 16_000
    }
}
