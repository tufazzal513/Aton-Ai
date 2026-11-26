package com.atonai.engine.tools

import com.atonai.engine.perm.PermGate
import kotlinx.serialization.json.JsonObject

/**
 * One capability the model can call (TOOLS_SPEC.md §1). Implementations are
 * pure with respect to the UI: they read [ToolCtx] for sandbox, permissions and
 * side-effect channels. Everything a tool touches lives under
 * `ctx.workdir` — see [Sandbox].
 */
interface Tool {
    /** Wire name, e.g. `read`. */
    val name: String
    val description: String

    /** JSON-Schema object for the arguments, sent verbatim to the model. */
    val schema: JsonObject

    /** `true` for tools that never mutate state (read, glob, grep, webfetch…). */
    val readOnly: Boolean get() = false

    /** Permission action used by M7 rules; defaults to the tool name. */
    val action: String get() = name

    suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult
}

/** Outcome of one tool call. */
data class ToolResult(
    val output: String,
    val status: String = STATUS_OK,
    /** Extra text shown only in the UI (diff, summary), never sent to the model. */
    val extra: String = "",
    val error: Boolean = false,
) {
    companion object {
        const val STATUS_OK = "ok"
        const val STATUS_ERROR = "error"
        const val STATUS_DENIED = "denied"

        fun ok(output: String, extra: String = "") = ToolResult(output, STATUS_OK, extra)
        fun error(message: String) = ToolResult(message, STATUS_ERROR, error = true)
        fun denied(message: String) = ToolResult(message, STATUS_DENIED, error = true)
    }
}

/** Raised when a tool argues with its own arguments; the repair pass may fix it. */
class ToolError(message: String) : Exception(message)

/** A question the agent asks the user (M9 `question` tool). */
data class Question(
    val text: String,
    val options: List<String> = emptyList(),
    val allowFreeText: Boolean = true,
)

/** One entry in the session plan (M9 `todo` tool). */
@kotlinx.serialization.Serializable
data class Todo(
    val content: String,
    val status: String = "pending",
    val priority: String = "medium",
)

/**
 * Everything a tool may use. Mirrors the original's `ToolCtx` field set
 * (00_RECON_SUMMARY §"ToolCtx exact fields") minus the pieces that arrive in
 * later milestones ([permit], [ask], [spawn], [setTodos]).
 */
data class ToolCtx(
    val workdir: String,
    val planDir: String = "",
    val sessionId: String = "",
    val agent: String = "general",
    val background: Boolean = false,
    val readOnly: Boolean = false,
    val trusted: Boolean = false,
    /** Text appended to the system prompt for nested instructions (M8). */
    val nested: String = "",
    /** Permission gate (M7); every side effect must pass through it. */
    val permit: PermGate = PermGate.permissive(),
    /**
     * Asks the user a question and suspends until answered (M9 `question` tool).
     * The default string means "no UI wired yet".
     */
    val ask: suspend (Question) -> String = { "(no answer available)" },
    /** Publishes the session plan for the M9 `todo` tool. */
    val setTodos: (List<Todo>) -> Unit = {},
)
