package com.atonai.engine.perm

import com.atonai.data.perms.PermissionReq
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the single outstanding permission question and the deferred the tool is
 * blocked on. The UI observes [pending] and calls [reply] with the chosen option
 * (`Allow once` / `Always allow` / `Deny`).
 *
 * Only one question is shown at a time — tools run sequentially in the loop, so
 * a queue of one is enough (02_ARCHITECTURE.md §8).
 */
class PermQueue {

    private val _pending = MutableStateFlow<PendingPerm?>()
    val pending: StateFlow<PendingPerm?> = _pending.asStateFlow()

    /** Suspends until the user answers; the gate awaits this. */
    suspend fun ask(req: PermissionReq): String {
        val slot = PendingPerm(req)
        _pending.value = slot
        try {
            return slot.reply.await()
        } finally {
            if (_pending.value === slot) _pending.value = null
        }
    }

    /** Answers the question with [id]; returns false if it is not the live one. */
    fun reply(id: String, choice: String): Boolean {
        val slot = _pending.value ?: return false
        if (slot.req.id != id) return false
        slot.reply.complete(choice)
        _pending.value = null
        return true
    }

    /** Cancels any outstanding question (e.g. when a turn is stopped). */
    fun cancel() {
        _pending.value?.reply?.complete(PermissionReq.DENY)
        _pending.value = null
    }
}
