package com.atonai.engine.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Retry wrapper for [Llm.stream]. Provider 429/5xx and socket failures are
 * transient, so a step that failed **before any text arrived** is retried with
 * exponential backoff (0.8 s, 1.6 s, 3.2 s). Once text has been emitted the
 * stream is not retried — replaying it would duplicate the answer in the UI.
 */
object Retry {

    const val MAX_ATTEMPTS = 3
    private const val BASE_DELAY_MS = 800L

    fun stream(llm: Llm, req: LlmRequest): Flow<LlmEvent> = flow {
        var attempt = 0
        while (true) {
            val seen = Seen()
            var failure: LlmEvent.Failed? = null

            llm.stream(req).collect { event ->
                when (event) {
                    is LlmEvent.TextDelta -> seen.text = true
                    is LlmEvent.ReasoningDelta -> seen.text = true
                    is LlmEvent.Failed -> failure = event
                    else -> Unit
                }
                if (event !is LlmEvent.Failed) emit(event)
            }

            val failed = failure
            if (failed == null) return@flow

            val canRetry = failed.retryable && !seen.text && attempt < MAX_ATTEMPTS - 1
            if (!canRetry) {
                emit(failed)
                return@flow
            }

            attempt++
            emit(LlmEvent.Retry(attempt, MAX_ATTEMPTS, failed.message))
            delay(BASE_DELAY_MS shl (attempt - 1))
        }
    }

    /** Mutable flag captured by the collector, avoids a `var` inside a lambda. */
    private class Seen {
        var text = false
    }
}