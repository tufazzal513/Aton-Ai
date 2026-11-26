package com.atonai.engine.llm

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The only place the app talks to an LLM. Exactly two wire kinds are supported
 * (decision D-06): `openai` and `anthropic`. Everything else — Gemini, Groq,
 * local servers — goes through the OpenAI-compatible path.
 */
class Llm(
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * Streams one completion as a cold [Flow]. Collecting it issues the request;
     * cancelling the collector cancels the HTTP call (04 §3, cancellable).
     */
    fun stream(req: LlmRequest): Flow<LlmEvent> = callbackFlow {
        val (url, payload, headers) = build(req)

        val builder = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA))
            .header("Accept", "text/event-stream")
        headers.forEach { (k, v) -> builder.header(k, v) }

        val call = client.newCall(builder.build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isClosedForSend) return
                trySend(LlmEvent.Failed(cleanError(e.message ?: "Network error"), retryable = true))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    val body = res.body
                    if (!res.isSuccessful || body == null) {
                        val text = runCatching { body?.string() }.getOrNull().orEmpty()
                        trySend(
                            LlmEvent.Failed(
                                cleanError("HTTP ${res.code}: ${text.take(400)}"),
                                retryable = res.code in 429..599,
                            ),
                        )
                        close()
                        return
                    }

                    val reader = SseReader()
                    val source = body.source()
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (reader.isDone()) break
                            val frame = reader.line(line) ?: continue
                            val data = frame.data
                            if (data == "[DONE]") break
                            decodeFrame(req.kind, data).forEach { trySend(it) }
                        }
                    } catch (e: IOException) {
                        if (!isClosedForSend) {
                            trySend(LlmEvent.Failed(cleanError(e.message ?: "stream error")))
                        }
                    }
                    close()
                }
            }
        })

        awaitClose { call.cancel() }
    }

    private fun decodeFrame(kind: String, data: String): List<LlmEvent> =
        if (kind == "anthropic") AnthropicCodec.decode(data) else OpenAiCodec.decode(data)

    private data class Built(val url: String, val payload: String, val headers: Map<String, String>)

    private fun build(req: LlmRequest): Built {
        val base = req.baseUrl.trimEnd('/')
        return when (req.kind) {
            "anthropic" -> {
                val headers = buildMap {
                    put("anthropic-version", "2023-06-01")
                    req.apiKey?.takeIf { it.isNotBlank() }?.let { put("x-api-key", it) }
                }
                Built("$base/messages", AnthropicCodec.encode(req), headers)
            }

            else -> {
                val headers = buildMap {
                    req.apiKey?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
                }
                Built("$base/chat/completions", OpenAiCodec.encode(req), headers)
            }
        }
    }

    companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)   // streaming: no read timeout
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        /** Trims provider HTML/JSON noise down to a short single line. */
        fun cleanError(raw: String): String {
            val oneLine = raw.replace(Regex("\\s+"), " ").trim()
            return if (oneLine.length <= 300) oneLine else oneLine.take(300) + "…"
        }

        /** Heuristic: does this error look like a rate/quota limit worth switching on? */
        fun limitKindFor(message: String): String? {
            val m = message.lowercase()
            return when {
                hasQuotaWord(m) -> "quota"
                "rate limit" in m || "429" in m || "too many requests" in m -> "rate"
                "overloaded" in m || "503" in m || "502" in m || "500" in m -> "server"
                else -> null
            }
        }

        fun hasQuotaWord(m: String): Boolean =
            "quota" in m || "insufficient" in m || "credit" in m || "billing" in m || "exceeded your current quota" in m
    }
}
