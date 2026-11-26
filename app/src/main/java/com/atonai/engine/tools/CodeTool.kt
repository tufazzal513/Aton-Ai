package com.atonai.engine.tools

import com.atonai.data.perms.Action
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Runs a JavaScript snippet (TOOLS_SPEC.md §15). The reference embeds Mozilla
 * Rhino (~1.5 MB); we keep the engine behind [JsSandbox] so the APK can ship
 * without it and the tool reports "unavailable" instead of crashing. If an
 * engine is supplied the sandbox has no require/network/timers/file access.
 */
class CodeTool(private val sandbox: JsSandbox? = null) : Tool {
    override val name = "code"
    override val action = Action.CODE
    override val description =
        "Run a JavaScript snippet and return everything printed with console.log. " +
            "The sandbox has no network, timers or file access."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("code") { put("type", "string") }
            putJsonObject("timeout_ms") {
                put("type", "integer")
                put("description", "Wall-clock cap, default ${DEFAULT_TIMEOUT_MS} ms")
            }
        }
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive("code"),
        )))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        val engine = sandbox
            ?: return ToolResult.ok(
                "The JavaScript sandbox is not available in this build. " +
                    "Use shell for computation instead.",
            )
        val a = Args(args)
        val code = a.str("code")
        val timeout = a.int("timeout_ms", DEFAULT_TIMEOUT_MS).coerceIn(100, MAX_TIMEOUT_MS)
        ctx.permit.permit(Action.CODE, code.take(60))
        return runCatching { engine.eval(code, timeout) }
            .getOrElse { ToolResult.error("JavaScript error: ${it.message}") }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000
        const val MAX_TIMEOUT_MS = 180_000
    }
}

/** Pluggable JS engine so the dependency stays optional. */
interface JsSandbox {
    /** Evaluates [code] with a [timeoutMs] wall-clock cap; returns captured output. */
    suspend fun eval(code: String, timeoutMs: Int): ToolResult
}
