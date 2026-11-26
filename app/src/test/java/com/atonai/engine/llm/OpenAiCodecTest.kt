package com.atonai.engine.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCodecTest {

    @Test
    fun decodes_text_delta() {
        val events = OpenAiCodec.decode(
            """{"choices":[{"delta":{"content":"Hello"},"index":0}]}""",
        )
        assertEquals(1, events.size)
        assertEquals("Hello", (events[0] as LlmEvent.TextDelta).text)
    }

    @Test
    fun decodes_reasoning_delta() {
        val events = OpenAiCodec.decode(
            """{"choices":[{"delta":{"reasoning_content":"think"},"index":0}]}""",
        )
        assertTrue(events.any { it is LlmEvent.ReasoningDelta && it.text == "think" })
    }

    @Test
    fun assembles_tool_call_across_fragments() {
        val acc = StepAcc()
        listOf(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"read","arguments":"{\"pa"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"th\":\"a.kt\"}"}}]}}]}""",
        ).forEach { data ->
            OpenAiCodec.decode(data).forEach { if (it is LlmEvent.ToolCallDelta) acc.onToolDelta(it) }
        }
        val calls = acc.toolCalls()
        assertEquals(1, calls.size)
        assertEquals("call_1", calls[0].id)
        assertEquals("read", calls[0].name)
        assertEquals("""{"path":"a.kt"}""", calls[0].arguments)
    }

    @Test
    fun reads_usage_and_finish_reason() {
        val events = OpenAiCodec.decode(
            """{"choices":[{"delta":{},"finish_reason":"stop"}],
               "usage":{"prompt_tokens":10,"completion_tokens":5,
               "prompt_tokens_details":{"cached_tokens":3}}}""",
        )
        val usage = events.filterIsInstance<LlmEvent.Usage>().first().usage
        assertEquals(10L, usage.inTokens)
        assertEquals(5L, usage.outTokens)
        assertEquals(3L, usage.cacheRead)
        assertNotNull(events.filterIsInstance<LlmEvent.Done>().first())
    }

    @Test
    fun ignores_keepalive_frames() {
        assertTrue(OpenAiCodec.decode("""{"choices":[]}""").isEmpty())
    }

    @Test
    fun encode_puts_system_first_and_tools() {
        val req = LlmRequest(
            kind = "openai",
            baseUrl = "https://x/v1",
            apiKey = "k",
            model = "m",
            system = "SYS",
            turns = listOf(LlmTurn.User("hi")),
            tools = listOf(
                ToolSpec("read", "Read a file", kotlinx.serialization.json.buildJsonObject {}),
            ),
        )
        val body = OpenAiCodec.encode(req)
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"name\":\"read\""))
        assertTrue(body.contains("\"stream\":true"))
    }
}
