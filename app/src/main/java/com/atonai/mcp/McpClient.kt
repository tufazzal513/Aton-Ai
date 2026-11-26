package com.atonai.mcp

import com.atonai.engine.llm.Llm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicLong

/**
 * Speaks the MCP "Streamable HTTP" JSON-RPC transport: initialize,
 * tools/list and tools/call. No external MCP SDK is required.
 */
class McpClient(
    private val client: OkHttpClient = Llm.defaultClient(),
) {

    private val ids = AtomicLong(0)

    /** Sends initialize + notifications/initialized. Returns the server info map. */
    suspend fun initialize(config: McpServerConfig): JsonObject = withContext(Dispatchers.IO) {
        val result = rpc(config, "initialize", buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("clientInfo", buildJsonObject {
                put("name", "AtonAI")
                put("version", "1.0.0")
            })
            put("capabilities", buildJsonObject {})
        })
        // Fire-and-forget initialized notification.
        runCatching { rpc(config, "notifications/initialized", null, expectResult = false) }
        result ?: JsonObject(emptyMap())
    }

    suspend fun listTools(config: McpServerConfig): List<McpTool> = withContext(Dispatchers.IO) {
        val result = rpc(config, "tools/list", null) ?: return@withContext emptyList()
        val arr = result["tools"] as? JsonArray ?: return@withContext emptyList()
        arr.mapNotNull { el ->
            runCatching {
                mcpJson.decodeFromJsonElement(
                    McpTool.serializer(),
                    normalizeTool(el.jsonObject),
                )
            }.getOrNull()
        }
    }

    suspend fun callTool(
        config: McpServerConfig,
        name: String,
        arguments: JsonObject,
    ): String = withContext(Dispatchers.IO) {
        val result = rpc(config, "tools/call", buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        })
        result?.toString() ?: "No result"
    }

    private fun normalizeTool(obj: JsonObject): JsonObject = buildJsonObject {
        obj["name"]?.let { put("name", it) }
        obj["title"]?.let { put("title", it) }
        obj["description"]?.let { put("description", it) }
        obj["inputSchema"]?.let { put("inputSchema", it) }
    }

    private fun rpc(
        config: McpServerConfig,
        method: String,
        params: JsonObject?,
        expectResult: Boolean = true,
    ): JsonObject? {
        val id = ids.incrementAndGet()
        val request = JsonRpcRequest(id = id, method = method, params = params)
        val body = mcpJson.encodeToString(JsonRpcRequest.serializer(), request)

        val builder = Request.Builder()
            .url(config.url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json, text/event-stream")

        if (!config.token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer ${config.token}")
        }

        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("MCP HTTP ${response.code}: ${text.take(300)}")
            }
            if (!expectResult) return null

            val payload = extractJson(text)
            val parsed = mcpJson.decodeFromString(JsonRpcResponse.serializer(), payload)
            parsed.error?.let { error("MCP error ${it.code}: ${it.message}") }
            return parsed.result
        }
    }

    /** Handles both plain JSON and SSE-framed (`data: {...}`) responses. */
    private fun extractJson(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) return trimmed
        val dataLine = trimmed.lineSequence()
            .firstOrNull { it.startsWith("data:") }
            ?: error("Empty MCP response")
        return dataLine.removePrefix("data:").trim()
    }
}
