package com.atonai.engine.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A single message part as persisted and rendered (04_DATA_MODELS.md §3).
 * Ordered so a step can interleave text → tool → text.
 */
@Serializable
sealed interface Part {
    @Serializable
    data class Text(val text: String) : Part

    @Serializable
    data class Reasoning(val text: String) : Part

    @Serializable
    data class ToolCall(
        val id: String,
        val name: String = "",
        val arguments: String = "",      // raw JSON, assembled from deltas
        val status: String = "pending",  // pending | running | ok | error | denied
        val output: String = "",
        val extra: String = "",          // diff / summary / spill path
    ) : Part
}

/** Provider turn, i.e. exactly what each wire format accepts (04 §4). */
sealed interface LlmTurn {
    data class User(val text: String, val images: List<String> = emptyList()) : LlmTurn
    data class Assistant(val text: String, val toolCalls: List<Part.ToolCall>) : LlmTurn
    data class ToolResult(val callId: String, val content: String) : LlmTurn
}

/** A tool exposed to the model: name + description + JSON-Schema for arguments. */
data class ToolSpec(
    val name: String,
    val description: String,
    val schema: JsonObject,
)

/** Everything needed to start one streamed completion. */
data class LlmRequest(
    val kind: String,                 // "openai" | "anthropic"
    val baseUrl: String,
    val apiKey: String?,
    val model: String,
    val system: String,
    val turns: List<LlmTurn>,
    val tools: List<ToolSpec> = emptyList(),
    val effort: String = "",          // "" | "low" | "medium" | "high"
    val maxTokens: Int = 4096,
    val temperature: Double? = null,
)

/** Usage counters reported by a completed stream. */
data class Usage(
    val inTokens: Long = 0,
    val outTokens: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
)

/** Incremental events emitted while a completion streams. */
sealed interface LlmEvent {
    data class TextDelta(val text: String) : LlmEvent
    data class ReasoningDelta(val text: String) : LlmEvent

    /** Tool-call fragment: `index` identifies which call the fragment belongs to. */
    data class ToolCallDelta(
        val index: Int,
        val id: String? = null,
        val name: String? = null,
        val argsFragment: String? = null,
    ) : LlmEvent

    data class Usage(val usage: com.atonai.engine.llm.Usage) : LlmEvent
    data class Done(val stopReason: String? = null) : LlmEvent
    data class Failed(val message: String, val retryable: Boolean = false) : LlmEvent

    /** Emitted between attempts; never shown as an error, only as a notice. */
    data class Retry(val attempt: Int, val max: Int, val reason: String) : LlmEvent
}

/**
 * Accumulates one streamed assistant step: text, reasoning and tool calls
 * (04_DATA_MODELS.md §5). Tool arguments arrive as fragments and are not valid
 * JSON until the stream ends, so [Call] keeps the raw text.
 */
class StepAcc {
    private val text = StringBuilder()
    private val reasoning = StringBuilder()
    private val calls = LinkedHashMap<Int, Call>()

    fun onText(delta: String) {
        text.append(delta)
    }

    fun onReasoning(delta: String) {
        reasoning.append(delta)
    }

    fun onToolDelta(delta: LlmEvent.ToolCallDelta) {
        val call = calls.getOrPut(delta.index) { Call() }
        delta.id?.let { call.id = it }
        delta.name?.let { call.name = it }
        delta.argsFragment?.let { call.args.append(it) }
    }

    val textValue: String get() = text.toString()
    val reasoningValue: String get() = reasoning.toString()

    /** Finished tool calls, in arrival order. */
    fun toolCalls(): List<Part.ToolCall> = calls.entries.map { (index, c) ->
        Part.ToolCall(
            id = c.id.ifBlank { "call_$index" },
            name = c.name,
            arguments = c.args.toString(),
        )
    }

    /** Ordered parts for persistence: reasoning, text, then tool calls. */
    fun parts(): List<Part> = buildList {
        if (reasoning.isNotEmpty()) add(Part.Reasoning(reasoningValue))
        if (text.isNotEmpty()) add(Part.Text(textValue))
        addAll(toolCalls())
    }

    private class Call {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
    }
}
