package com.atonai.engine.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseReaderTest {

    @Test
    fun parses_data_frame_terminated_by_blank_line() {
        val r = SseReader()
        assertNull(r.line("data: {\"a\":1}"))
        val frame = r.line("")
        assertEquals("{\"a\":1}", frame?.data)
        assertNull(frame?.event)
    }

    @Test
    fun strips_single_leading_space_and_keeps_event_name() {
        val r = SseReader()
        r.line("event: content_block_delta")
        r.line("data: hello")
        val frame = r.line("")
        assertEquals("content_block_delta", frame?.event)
        assertEquals("hello", frame?.data)
    }

    @Test
    fun joins_multiple_data_lines_with_newline() {
        val r = SseReader()
        r.line("data: line1")
        r.line("data: line2")
        val frame = r.line("")
        assertEquals("line1\nline2", frame?.data)
    }

    @Test
    fun ignores_comments_and_heartbeats() {
        val r = SseReader()
        assertNull(r.line(": keep-alive"))
        assertNull(r.line(""))
    }

    @Test
    fun detects_done_sentinel() {
        val r = SseReader()
        r.line("data: [DONE]")
        assertTrue(r.isDone())
    }
}
