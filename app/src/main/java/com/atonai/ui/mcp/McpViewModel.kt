package com.atonai.ui.mcp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atonai.appGraph
import com.atonai.mcp.McpServerConfig
import com.atonai.mcp.McpTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class McpUiState(
    val servers: List<McpServerConfig> = emptyList(),
    val tools: Map<String, List<McpTool>> = emptyMap(),
    val busyServerId: String? = null,
    val status: Map<String, String> = emptyMap(),
)

class McpViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app.appGraph
    private val repo = graph.mcpRepo
    private val client = graph.mcpClient

    private val _state = MutableStateFlow(McpUiState())
    val state: StateFlow<McpUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.servers.collect { servers -> _state.update { it.copy(servers = servers) } }
        }
        viewModelScope.launch {
            repo.tools.collect { tools -> _state.update { it.copy(tools = tools) } }
        }
    }

    fun addServer(name: String, url: String, token: String?) {
        if (name.isBlank() || url.isBlank()) return
        repo.add(
            McpServerConfig(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                url = url.trim(),
                token = token?.takeIf { it.isNotBlank() },
            ),
        )
    }

    fun removeServer(id: String) = repo.remove(id)

    fun connect(config: McpServerConfig) {
        _state.update { it.copy(busyServerId = config.id, status = it.status + (config.id to "Connecting…")) }
        viewModelScope.launch {
            runCatching {
                client.initialize(config)
                client.listTools(config)
            }.onSuccess { tools ->
                repo.setTools(config.id, tools)
                _state.update {
                    it.copy(
                        busyServerId = null,
                        status = it.status + (config.id to "Connected • ${tools.size} tools"),
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        busyServerId = null,
                        status = it.status + (config.id to "Error: ${e.message}"),
                    )
                }
            }
        }
    }
}
