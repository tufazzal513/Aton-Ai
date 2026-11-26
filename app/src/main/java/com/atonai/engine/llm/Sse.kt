package com.atonai.engine.llm

/**
 * Low-level Server-Sent-Events frame reader. Feed it raw lines; it returns the
 * completed `(event, data)` frame when a blank line terminates one.
 *
 * Handles: multiple `data:` lines concatenated with `\n`, `event:` names,
 * comment lines starting with `:`, and CRLF. This is the single piece of SSE
 * logic the whole client depends on, so it is deliberately small and pure.
 */
class SseReader {

    data class Frame(val event: String?, val data: String)

    private var event: String? = null
    private val data = StringBuilder()

    /** Feeds one raw line; returns a [Frame] when the line ends a frame. */
    fun line(raw: String): Frame? {
        val line = raw.trimEnd('\r', '\n')

        if (line.isEmpty()) {
            if (data.isEmpty() && event == null) return null
            val frame = Frame(event, data.toString())
            event = null
            data.setLength(0)
            return frame
        }
        if (line.startsWith(":")) return null       // comment / heartbeat

        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)

        when (field) {
            "event" -> event = value
            "data" -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(value)
            }
        }
        return null
    }

    /** True when the accumulated data is the OpenAI end-of-stream sentinel. */
    fun isDone(): Boolean = data.toString().trim() == "[DONE]"
}
