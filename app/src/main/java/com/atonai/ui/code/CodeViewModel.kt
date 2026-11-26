package com.atonai.ui.code

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atonai.appGraph
import com.atonai.data.model.WorkspaceFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CodeUiState(
    val files: List<WorkspaceFile> = emptyList(),
    val openFile: String? = null,
    val editorText: String = "",
    val dirty: Boolean = false,
    val message: String? = null,
)

class CodeViewModel(app: Application) : AndroidViewModel(app) {

    private val workspace = app.appGraph.workspace

    private val _state = MutableStateFlow(CodeUiState())
    val state: StateFlow<CodeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(files = workspace.list()) }
    }

    fun open(path: String) {
        viewModelScope.launch {
            val content = workspace.read(path)
            _state.update {
                it.copy(openFile = path, editorText = content, dirty = false, message = null)
            }
        }
    }

    fun onEditorChange(value: String) =
        _state.update { it.copy(editorText = value, dirty = true) }

    fun newFile(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { workspace.write(name, "") }
                .onSuccess {
                    refresh()
                    open(name)
                    _state.update { s -> s.copy(message = "Created $name") }
                }
                .onFailure { e ->
                    _state.update { s -> s.copy(message = e.message ?: "Could not create file") }
                }
        }
    }

    fun save() {
        val path = _state.value.openFile ?: return
        viewModelScope.launch {
            runCatching { workspace.write(path, _state.value.editorText) }
                .onSuccess {
                    _state.update { it.copy(dirty = false, message = "Saved") }
                    refresh()
                }
                .onFailure { e ->
                    _state.update { it.copy(message = e.message ?: "Could not save") }
                }
        }
    }

    fun delete(path: String) {
        viewModelScope.launch {
            workspace.delete(path)
            _state.update { st ->
                if (st.openFile == path) st.copy(openFile = null, editorText = "", dirty = false)
                else st
            }
            refresh()
        }
    }

    fun closeFile() = _state.update { it.copy(openFile = null, editorText = "", dirty = false) }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
