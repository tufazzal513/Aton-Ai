package com.atonai.engine

import com.atonai.engine.llm.LlmTurn
import com.atonai.engine.llm.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnBuilderTest {

    @Test
    fun plain_user_and_assistant_become_text_turns() {
        val msgs = listOf(
            Msg.user("hello"),
            Msg(role = Msg.ASSISTANT, text = "hi there", completed = true),
        )
        val turns = TurnBuilder.build(msgs)
        assertEquals(2, turns.size)
        assertEquals("hello", (turns[0] as LlmTurn.User).text)
        assertEquals("hi there", (turns[1] as LlmTurn.Assistant).text)
    }

    @Test
    fun notices_never_reach_the_model() {
        val turns = TurnBuilder.build(listOf(Msg.notice("switched model"), Msg.user("q")))
        assertEquals(1, turns.size)
        assertTrue(turns.single() is LlmTurn.User)
    }

    @Test
    fun tool_calls_become_assistant_then_result_turns() {
        val msg = Msg(role = Msg.ASSISTANT, completed = true).apply {
            parts += Part.Text("let me read")
            parts += Part.ToolCall(id = "c1", name = "read", arguments = """{"path":"a.kt"}""", status = "ok", output = "file body")
        }
        val turns = TurnBuilder.build(listOf(Msg.user("do it"), msg))
        assertEquals(3, turns.size)
        val assistant = turns[1] as LlmTurn.Assistant
        assertEquals("let me read", assistant.text)
        assertEquals("read", assistant.toolCalls.single().name)
        val result = turns[2] as LlmTurn.ToolResult
        assertEquals("c1", result.callId)
        assertEquals("file body", result.content)
    }

    @Test
    fun text_only_assistant_falls_back_to_llm_text() {
        val msg = Msg(role = Msg.ASSISTANT, llmText = "raw output", completed = true)
        val turns = TurnBuilder.build(listOf(msg))
        assertEquals("raw output", (turns.single() as LlmTurn.Assistant).text)
    }

    @Test
    fun long_tool_results_are_truncated() {
        val msg = Msg(role = Msg.ASSISTANT, completed = true).apply {
            parts += Part.ToolCall(id = "c1", name = "shell", status = "ok", output = "x".repeat(20_000))
        }
        val turns = TurnBuilder.build(listOf(msg), toolResultCap = 100)
        val result = turns.filterIsInstance<LlmTurn.ToolResult>().single()
        assertTrue(result.content.length < 200)
        assertTrue(result.content.contains("truncated"))
    }

    @Test
    fun reasoning_parts_are_not_resent() {
        val msg = Msg(role = Msg.ASSISTANT, completed = true).apply {
            parts += Part.Reasoning("secret thoughts")
            parts += Part.Text("answer")
        }
        val turns = TurnBuilder.build(listOf(msg))
        assertEquals("answer", (turns.single() as LlmTurn.Assistant).text)
    }
}
