package com.atonai.ui.model

import com.atonai.engine.Msg
import com.atonai.engine.Session
import com.atonai.engine.llm.Part

/**
 * Pure projection from a stored [Session] onto the rows the chat list renders.
 * Keeping it separate means the persisted model can change shape without
 * touching the UI, and it is unit-testable without Compose.
 */
object ChatProjector {

    fun toItems(session: Session?, busy: Boolean = false): List<ChatItem> {
        val msgs = session?.msgs ?: return emptyList()
        return msgs.map { msg -> toItem(msg, busy && msg === msgs.lastOrNull()) }
    }

    private fun toItem(msg: Msg, streaming: Boolean): ChatItem = when (msg.role) {
        Msg.USER -> ChatItem.User(msg.id, msg.text, msg.images.toList())
        Msg.NOTICE -> ChatItem.Notice(msg.id, msg.text)
        Msg.SHELL -> ChatItem.Shell(msg.id, msg.text, msg.llmText)
        else -> ChatItem.Assistant(
            id = msg.id,
            blocks = blocks(msg),
            streaming = streaming && !msg.completed,
            error = msg.error,
        )
    }

    private fun blocks(msg: Msg): List<Block> {
        if (msg.parts.isEmpty()) {
            return buildList {
                if (msg.text.isNotBlank()) add(Block.Text(msg.text))
                msg.error?.let { add(Block.Text("⚠ $it")) }
            }
        }
        return msg.parts.map { part ->
            when (part) {
                is Part.Text -> Block.Text(part.text)
                is Part.Reasoning -> Block.Reasoning(part.text)
                is Part.ToolCall -> Block.Tool(
                    id = part.id,
                    name = part.name,
                    input = part.arguments,
                    output = part.output,
                    status = part.status,
                    summary = summarize(part),
                    extra = part.extra,
                )
            }
        }
    }

    /** One-line card title, e.g. `read lib/Main.kt`, `shell npm test`. */
    fun summarize(call: Part.ToolCall): String {
        val firstArg = extractFirstString(call.arguments)
        return when {
            firstArg != null -> "${call.name} $firstArg"
            call.name.isNotBlank() -> call.name
            else -> "tool"
        }
    }

    /** Pulls the first string value out of a JSON object without a full parser. */
    private fun extractFirstString(json: String): String? {
        val m = Regex("\"\\w+\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json) ?: return null
        return m.groupValues[1].take(60)
    }
}
