package com.atonai.engine

import com.atonai.engine.llm.Part
import com.atonai.engine.llm.ToolSpec

/**
 * Executes tool calls on behalf of the engine. M4 ships [NoTools] (chat only);
 * M5 provides the real registry. Keeping it behind an interface means the loop
 * is written once and does not change when tools arrive.
 */
interface ToolRunner {
    /** Tools advertised to the model this turn. Empty = pure chat. */
    val specs: List<ToolSpec>

    /** Runs one call and returns it with `status`/`output` filled in. */
    suspend fun run(call: Part.ToolCall, ctx: ToolRunCtx): Part.ToolCall
}

/** The slice of session state a tool runner needs. */
data class ToolRunCtx(
    val sessionId: String,
    val workdir: String,
    val readOnly: Boolean = false,
    val trusted: Boolean = false,
)

/** Default runner: no tools available, so the model only ever answers in text. */
object NoTools : ToolRunner {
    override val specs: List<ToolSpec> = emptyList()
    override suspend fun run(call: Part.ToolCall, ctx: ToolRunCtx): Part.ToolCall =
        call.copy(status = "error", output = "No tools are available yet.")
}
