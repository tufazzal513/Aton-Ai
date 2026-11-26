package com.atonai.mcp

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Stores configured MCP servers and tracks per-server tool lists. */
class McpRepository(context: Context) {

    private val file = File(context.filesDir, "mcp_servers.json")

    private val _servers = MutableStateFlow(loadServers())
    val servers: StateFlow<List<McpServerConfig>> = _servers.asStateFlow()

    private val _tools = MutableStateFlow<Map<String, List<McpTool>>>(emptyMap())
    val tools: StateFlow<Map<String, List<McpTool>>> = _tools.asStateFlow()

    fun add(config: McpServerConfig) {
        _servers.value = _servers.value + config
        persist()
    }

    fun remove(id: String) {
        _servers.value = _servers.value.filterNot { it.id == id }
        _tools.value = _tools.value - id
        persist()
    }

    fun setTools(serverId: String, tools: List<McpTool>) {
        _tools.value = _tools.value + (serverId to tools)
    }

    private fun loadServers(): List<McpServerConfig> =
        runCatching {
            if (!file.exists()) emptyList()
            else mcpJson.decodeFromString(
                ListSerializer(McpServerConfig.serializer()),
                file.readText(),
            )
        }.getOrDefault(emptyList())

    private fun persist() {
        runCatching {
            file.writeText(
                mcpJson.encodeToString(
                    ListSerializer(McpServerConfig.serializer()),
                    _servers.value,
                ),
            )
        }
    }
}
