package com.atonai.engine.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicCodecTest {

    @Test
    fun decodes_text_delta() {
        val events = AnthropicCodec.decode(
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}""",
        )
        assertEquals("Hi", (events.first { it is LlmEvent.TextDelta } as LlmEvent.TextDelta).text)
    }

    @Test
    fun decodes_thinking_delta() {
        val events = AnthropicCodec.decode(
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hm"}}""",
        )
        assertEquals("hm", (events.first() as LlmEvent.ReasoningDelta).text)
    }

    @Test
    fun assembles_tool_use_from_start_and_json_deltas() {
        val acc = StepAcc()
        val frames = listOf(
            """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"glob"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"pat"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"tern\":\"*.kt\"}"}}""",
        )
        frames.forEach { data ->
            AnthropicCodec.decode(data).forEach { if (it is LlmEvent.ToolCallDelta) acc.onToolDelta(it) }
        }
        val call = acc.toolCalls().single()
        assertEquals("toolu_1", call.id)
        assertEquals("glob", call.name)
        assertEquals("""{"pattern":"*.kt"}""", call.arguments)
    }

    @Test
    fun reads_usage_from_start_and_delta() {
        val start = AnthropicCodec.decode(
            """{"type":"message_start","message":{"usage":{"input_tokens":12,
               "cache_read_input_tokens":4,"cache_creation_input_tokens":2}}}""",
        ).filterIsInstance<LlmEvent.Usage>().first().usage
        assertEquals(12L, start.inTokens)
        assertEquals(4L, start.cacheRead)
        assertEquals(2L, start.cacheWrite)

        val delta = AnthropicCodec.decode(
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":7}}""",
        )
        assertEquals(7L, delta.filterIsInstance<LlmEvent.Usage>().first().usage.outTokens)
        assertTrue(delta.any { it is LlmEvent.Done })
    }

    @Test
    fun error_event_becomes_failure() {
        val events = AnthropicCodec.decode(
            """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""",
        )
        assertEquals("Overloaded", (events.single() as LlmEvent.Failed).message)
    }
}
