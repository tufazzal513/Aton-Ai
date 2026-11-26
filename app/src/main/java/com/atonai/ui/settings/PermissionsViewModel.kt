package com.atonai.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atonai.appGraph
import com.atonai.data.perms.PermRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Screen state for the permission-rules editor (05_UI_MAP.md §7). */
data class PermState(
    val rules: List<PermRule> = emptyList(),
    val draftAction: String = "write",
    val draftPattern: String = "",
    val draftDecision: String = PermRule.ASK,
)

class PermissionsViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app.appGraph
    private val _state = MutableStateFlow(PermState())
    val state: StateFlow<PermState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            graph.permStore.flow.collect { rules -> _state.update { it.copy(rules = rules) } }
        }
    }

    fun onAction(value: String) = _state.update { it.copy(draftAction = value) }
    fun onPattern(value: String) = _state.update { it.copy(draftPattern = value) }
    fun onDecision(value: String) = _state.update { it.copy(draftDecision = value) }

    fun add() {
        val s = _state.value
        val pattern = s.draftPattern.trim()
        if (pattern.isBlank()) return
        viewModelScope.launch {
            graph.permStore.put(PermRule(s.draftAction, pattern, s.draftDecision))
            _state.update { it.copy(draftPattern = "") }
        }
    }

    fun remove(rule: PermRule) = viewModelScope.launch {
        graph.permStore.remove(rule.action, rule.pattern)
    }

    /** Cycles a rule's decision Allow → Ask → Deny → Allow. */
    fun cycle(rule: PermRule) {
        val order = PermRule.decisions
        val next = order[(order.indexOf(rule.decision).coerceAtLeast(0) + 1) % order.size]
        viewModelScope.launch { graph.permStore.put(rule.copy(decision = next)) }
    }
}
