package com.atonai.engine.perm

import com.atonai.data.perms.Action
import com.atonai.data.perms.PermissionReq
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermQueueTest {

    private fun req(id: String = "q1") = PermissionReq(
        id = id,
        sessionId = "s1",
        action = Action.WRITE,
        resources = listOf("a.kt"),
        message = "write a.kt",
        options = PermissionReq.defaultOptions,
    )

    @Test
    fun ask_publishes_the_question_until_answered() = runBlocking {
        val queue = PermQueue()
        val answer = async { queue.ask(req()) }
        // Yield so the async body runs and publishes on the single test thread.
        val slot = queue.pending.filterNotNull().first()
        assertEquals("q1", slot.req.id)
        assertTrue(queue.reply("q1", PermissionReq.ALLOW_ONCE))
        assertEquals(PermissionReq.ALLOW_ONCE, answer.await())
        assertNull(queue.pending.value)
    }

    @Test
    fun reply_with_wrong_id_is_ignored() = runBlocking {
        val queue = PermQueue()
        val answer = async { queue.ask(req()) }
        queue.pending.filterNotNull().first()
        assertFalse(queue.reply("other", PermissionReq.ALLOW_ONCE))
        assertTrue(queue.reply("q1", PermissionReq.DENY))
        assertEquals(PermissionReq.DENY, answer.await())
    }

    @Test
    fun cancel_denies_the_outstanding_question() = runBlocking {
        val queue = PermQueue()
        val answer = async { queue.ask(req()) }
        queue.pending.filterNotNull().first()
        queue.cancel()
        assertEquals(PermissionReq.DENY, answer.await())
    }

    @Test
    fun reply_without_a_question_returns_false() {
        assertFalse(PermQueue().reply("q1", PermissionReq.ALLOW_ONCE))
    }
}
