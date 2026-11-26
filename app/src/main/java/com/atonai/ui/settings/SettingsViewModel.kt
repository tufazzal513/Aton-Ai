package com.atonai.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atonai.appGraph
import com.atonai.data.catalog.CatalogProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val providers: List<CatalogProvider> = emptyList(),
    val selectedProviderId: String? = null,
    val apiKeys: Map<String, String> = emptyMap(),
    val saved: Boolean = false,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app.appGraph

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val providers = graph.registry.catalog.providers
            val keys = providers.associate { p -> p.id to (graph.secrets.apiKey(p.id) ?: "") }
            _state.update {
                it.copy(
                    providers = providers,
                    selectedProviderId = graph.settings.selectedProviderId.first()
                        ?: graph.registry.defaultProvider()?.id,
                    apiKeys = keys,
                )
            }
        }
    }

    fun onApiKeyChange(providerId: String, value: String) =
        _state.update { it.copy(apiKeys = it.apiKeys + (providerId to value), saved = false) }

    fun selectProvider(providerId: String) {
        _state.update { it.copy(selectedProviderId = providerId) }
        viewModelScope.launch {
            val modelId = graph.registry.defaultModelId(providerId)
            graph.settings.setSelection(providerId, modelId)
        }
    }

    fun save() {
        val snapshot = _state.value
        viewModelScope.launch {
            snapshot.apiKeys.forEach { (providerId, key) ->
                graph.secrets.setApiKey(providerId, key)
            }
            _state.update { it.copy(saved = true) }
        }
    }
}
