package com.atonai.engine.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * OpenAI-compatible chat completions: request body + streaming frame decoder.
 * This also covers every provider whose `kind` is `openai` (Groq, Together,
 * local servers, Gemini's OpenAI endpoint, ...).
 */
object OpenAiCodec {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun encode(req: LlmRequest): String = buildJsonObject {
        put("model", req.model)
        put("stream", true)
        put("stream_options", buildJsonObject { put("include_usage", true) })
        if (req.maxTokens > 0) put("max_tokens", req.maxTokens)
        req.temperature?.let { put("temperature", it) }
        put("messages", buildMessages(req))
        if (req.tools.isNotEmpty()) put("tools", buildTools(req.tools))
    }.let { json.encodeToString(JsonObject.serializer(), it) }

    private fun buildMessages(req: LlmRequest): JsonArray = buildJsonArray {
        if (req.system.isNotBlank()) {
            add(buildJsonObject {
                put("role", "system")
                put("content", req.system)
            })
        }
        req.turns.forEach { turn ->
            when (turn) {
                is LlmTurn.User -> add(buildJsonObject {
                    put("role", "user")
                    put("content", turn.text)
                })

                is LlmTurn.Assistant -> add(buildJsonObject {
                    put("role", "assistant")
                    put("content", turn.text)
                    if (turn.toolCalls.isNotEmpty()) {
                        put("tool_calls", buildJsonArray {
                            turn.toolCalls.forEach { call ->
                                add(buildJsonObject {
                                    put("id", call.id)
                                    put("type", "function")
                                    put("function", buildJsonObject {
                                        put("name", call.name)
                                        put("arguments", call.arguments.ifBlank { "{}" })
                                    })
                                })
                            }
                        })
                    }
                })

                is LlmTurn.ToolResult -> add(buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", turn.callId)
                    put("content", turn.content)
                })
            }
        }
    }

    private fun buildTools(tools: List<ToolSpec>): JsonArray = buildJsonArray {
        tools.forEach { spec ->
            add(buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", spec.name)
                    put("description", spec.description)
                    put("parameters", spec.schema)
                })
            })
        }
    }

    /**
     * Decodes one `data:` JSON frame into zero or more events. Returns an empty
     * list for frames that carry nothing (role-only deltas, keep-alives).
     */
    fun decode(data: String): List<LlmEvent> {
        val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
            ?: return emptyList()
        val out = mutableListOf<LlmEvent>()

        obj.usage()?.let { out += LlmEvent.Usage(it) }

        val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return out
        choice["delta"]?.jsonObject?.let { delta ->
            delta["content"]?.jsonPrimitive?.strOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { out += LlmEvent.TextDelta(it) }
            // Some providers expose reasoning as a distinct field.
            delta["reasoning"]?.jsonPrimitive?.strOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { out += LlmEvent.ReasoningDelta(it) }
            delta["reasoning_content"]?.jsonPrimitive?.strOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { out += LlmEvent.ReasoningDelta(it) }
            delta["tool_calls"]?.jsonArray?.forEach { tc ->
                val tco = tc.jsonObject
                val index = tco["index"]?.jsonPrimitive?.intOrNull() ?: 0
                val fn = tco["function"]?.jsonObject
                out += LlmEvent.ToolCallDelta(
                    index = index,
                    id = tco["id"]?.jsonPrimitive?.strOrNull(),
                    name = fn?.get("name")?.jsonPrimitive?.strOrNull(),
                    argsFragment = fn?.get("arguments")?.jsonPrimitive?.strOrNull(),
                )
            }
        }
        choice["finish_reason"]?.jsonPrimitive?.strOrNull()?.let {
            out += LlmEvent.Done(it)
        }
        return out
    }

    private fun JsonObject.usage(): Usage? {
        val u = this["usage"]?.jsonObject ?: return null
        fun n(k: String) = u[k]?.jsonPrimitive?.intOrNull()?.toLong() ?: 0L
        return Usage(
            inTokens = n("prompt_tokens"),
            outTokens = n("completion_tokens"),
            cacheRead = u["prompt_tokens_details"]?.jsonObject?.get("cached_tokens")
                ?.jsonPrimitive?.intOrNull()?.toLong() ?: 0L,
        )
    }

    private fun JsonPrimitive.strOrNull(): String? = if (isString) content else content
    private fun JsonPrimitive.intOrNull(): Int? = content.toIntOrNull()
}
