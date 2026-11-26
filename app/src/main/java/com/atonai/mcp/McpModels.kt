package com.atonai.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A configured MCP endpoint. */
@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val token: String? = null,
)

/** A tool advertised by an MCP server. */
@Serializable
data class McpTool(
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val inputSchema: JsonObject? = null,
)

/** Minimal JSON-RPC 2.0 request used by the MCP transport. */
@Serializable
internal data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Long,
    val method: String,
    val params: JsonObject? = null,
)

@Serializable
internal data class JsonRpcResponse(
    val jsonrpc: String? = null,
    val id: Long? = null,
    val result: JsonObject? = null,
    val error: JsonRpcError? = null,
)

@Serializable
internal data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonObject? = null,
)

internal val mcpJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
