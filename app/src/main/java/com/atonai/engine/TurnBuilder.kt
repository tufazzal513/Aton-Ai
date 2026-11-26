package com.atonai.engine

import com.atonai.engine.llm.LlmTurn
import com.atonai.engine.llm.Part

/**
 * Pure projection from stored [Msg]/[Part] data onto provider turns
 * (02_ARCHITECTURE.md §4). One function, no state, so it is trivially testable
 * and identical for both wire kinds.
 */
object TurnBuilder {

    /** Max characters kept per tool result before it is truncated for the wire. */
    const val TOOL_RESULT_CAP = 8_000

    fun build(msgs: List<Msg>, toolResultCap: Int = TOOL_RESULT_CAP): List<LlmTurn> {
        val turns = mutableListOf<LlmTurn>()
        for (msg in msgs) {
            when (msg.role) {
                Msg.USER -> if (msg.text.isNotBlank() || msg.images.isNotEmpty()) {
                    turns += LlmTurn.User(msg.text, msg.images.toList())
                }

                Msg.ASSISTANT -> buildAssistant(msg, turns, toolResultCap)

                Msg.NOTICE -> Unit // notices are UI-only, never sent to the model

                Msg.SHELL -> turns += LlmTurn.User("$ ${msg.text}\n${msg.llmText}".trimEnd())
            }
        }
        return turns
    }

    private fun buildAssistant(msg: Msg, into: MutableList<LlmTurn>, cap: Int) {
        val text = StringBuilder()
        val calls = mutableListOf<Part.ToolCall>()

        if (msg.parts.isEmpty()) {
            // Persisted assistant text lives in `text`; `llmText` is the raw
            // stream buffer and wins only when it is the sole source.
            val raw = msg.llmText.ifBlank { msg.text }
            if (raw.isNotBlank()) text.append(raw)
        } else {
            msg.parts.forEach { part ->
                when (part) {
                    is Part.Text -> text.append(part.text)
                    is Part.Reasoning -> Unit // reasoning is not re-sent
                    is Part.ToolCall -> calls += part
                }
            }
        }

        if (text.isBlank() && calls.isEmpty()) return
        into += LlmTurn.Assistant(text.toString(), calls)

        // Emit a tool result turn for every executed call, in order.
        msg.parts.filterIsInstance<Part.ToolCall>().forEach { call ->
            val content = when (call.status) {
                "ok" -> call.output
                "denied" -> "[denied by user]"
                "error" -> "[error] ${call.output}"
                else -> call.output
            }
            into += LlmTurn.ToolResult(call.id, truncate(content, cap))
        }
    }

    private fun truncate(s: String, cap: Int): String =
        if (s.length <= cap) s else s.take(cap) + "\n…[truncated ${s.length - cap} chars]"
}
