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
import kotlinx.serialization.json.putJsonObject

/**
 * Anthropic Messages API: request body + streaming frame decoder.
 * Streaming uses named events (`message_start`, `content_block_delta`, ...), so
 * [decode] dispatches on the event name AND the payload `type`.
 */
object AnthropicCodec {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun encode(req: LlmRequest): String = buildJsonObject {
        put("model", req.model)
        put("stream", true)
        put("max_tokens", req.maxTokens)
        if (req.system.isNotBlank()) put("system", req.system)
        req.temperature?.let { put("temperature", it) }
        put("messages", buildMessages(req))
        if (req.tools.isNotEmpty()) put("tools", buildTools(req.tools))
    }.let { json.encodeToString(JsonObject.serializer(), it) }

    private fun buildMessages(req: LlmRequest): JsonArray = buildJsonArray {
        var pendingToolResults = mutableListOf<JsonObject>()

        fun flushToolResults() {
            if (pendingToolResults.isEmpty()) return
            add(buildJsonObject {
                put("role", "user")
                put("content", JsonArray(pendingToolResults))
            })
            pendingToolResults = mutableListOf()
        }

        req.turns.forEach { turn ->
            when (turn) {
                is LlmTurn.User -> {
                    flushToolResults()
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", turn.text)
                    })
                }

                is LlmTurn.Assistant -> {
                    flushToolResults()
                    add(buildJsonObject {
                        put("role", "assistant")
                        put("content", buildJsonArray {
                            if (turn.text.isNotBlank()) {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", turn.text)
                                })
                            }
                            turn.toolCalls.forEach { call ->
                                add(buildJsonObject {
                                    put("type", "tool_use")
                                    put("id", call.id)
                                    put("name", call.name)
                                    put("input", parseArgs(call.arguments))
                                })
                            }
                        })
                    })
                }

                is LlmTurn.ToolResult -> {
                    // Consecutive tool results share one user message.
                    pendingToolResults += buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", turn.callId)
                        put("content", turn.content)
                    }
                }
            }
        }
        flushToolResults()
    }

    private fun buildTools(tools: List<ToolSpec>): JsonArray = buildJsonArray {
        tools.forEach { spec ->
            add(buildJsonObject {
                put("name", spec.name)
                put("description", spec.description)
                put("input_schema", spec.schema)
            })
        }
    }

    private fun parseArgs(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }

    fun decode(data: String): List<LlmEvent> {
        val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
            ?: return emptyList()
        val out = mutableListOf<LlmEvent>()
        when (obj["type"]?.jsonPrimitive?.contentOrNull()) {
            "content_block_delta" -> {
                val delta = obj["delta"]?.jsonObject ?: return out
                when (delta["type"]?.jsonPrimitive?.contentOrNull()) {
                    "text_delta" -> delta["text"]?.jsonPrimitive?.contentOrNull()
                        ?.let { out += LlmEvent.TextDelta(it) }
                    "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.contentOrNull()
                        ?.let { out += LlmEvent.ReasoningDelta(it) }
                    "input_json_delta" -> {
                        val index = obj["index"]?.jsonPrimitive?.intOrNull() ?: 0
                        out += LlmEvent.ToolCallDelta(
                            index = index,
                            argsFragment = delta["partial_json"]?.jsonPrimitive?.contentOrNull(),
                        )
                    }
                }
            }

            "content_block_start" -> {
                val block = obj["content_block"]?.jsonObject ?: return out
                if (block["type"]?.jsonPrimitive?.contentOrNull() == "tool_use") {
                    val index = obj["index"]?.jsonPrimitive?.intOrNull() ?: 0
                    out += LlmEvent.ToolCallDelta(
                        index = index,
                        id = block["id"]?.jsonPrimitive?.contentOrNull(),
                        name = block["name"]?.jsonPrimitive?.contentOrNull(),
                    )
                }
            }

            "message_delta" -> {
                obj["usage"]?.jsonObject?.let { u ->
                    out += LlmEvent.Usage(
                        Usage(
                            outTokens = u["output_tokens"]?.jsonPrimitive?.intOrNull()?.toLong() ?: 0L,
                        ),
                    )
                }
                obj["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull()
                    ?.let { out += LlmEvent.Done(it) }
            }

            "message_start" -> {
                obj["message"]?.jsonObject?.get("usage")?.jsonObject?.let { u ->
                    out += LlmEvent.Usage(
                        Usage(
                            inTokens = u["input_tokens"]?.jsonPrimitive?.intOrNull()?.toLong() ?: 0L,
                            cacheRead = u["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull()?.toLong() ?: 0L,
                            cacheWrite = u["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull()?.toLong() ?: 0L,
                        ),
                    )
                }
            }

            "message_stop" -> out += LlmEvent.Done()
            "error" -> obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull()
                ?.let { out += LlmEvent.Failed(it) }
        }
        return out
    }

    private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else content
    private fun JsonPrimitive.intOrNull(): Int? = content.toIntOrNull()
}
