package com.atonai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atonai.appGraph
import com.atonai.data.catalog.CatalogModel
import com.atonai.data.catalog.CatalogProvider
import com.atonai.data.perms.PermRule
import com.atonai.data.perms.PermissionReq
import com.atonai.engine.Engine
import com.atonai.engine.Msg
import com.atonai.engine.Session
import com.atonai.engine.SessionInfo
import com.atonai.engine.TurnEvent
import com.atonai.engine.tools.Question
import com.atonai.engine.tools.Todo
import com.atonai.ui.model.ChatItem
import com.atonai.ui.model.ChatProjector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * The single screen-state holder for the app (02_ARCHITECTURE.md — `AppViewModel`
 * owns one `UiState`). It grows across milestones; M4 needs chat + sessions.
 */
data class UiState(
    val currentId: String? = null,
    val title: String = "AtonAI",
    val items: List<ChatItem> = emptyList(),
    val busy: Set<String> = emptySet(),
    val sessions: List<SessionInfo> = emptyList(),
    val providers: List<CatalogProvider> = emptyList(),
    val models: List<CatalogModel> = emptyList(),
    val providerId: String? = null,
    val modelId: String? = null,
    val input: String = "",
    val tokens: Long = 0,
    val cost: Double = 0.0,
    val error: String? = null,
    val loadingSessions: Boolean = false,
    val pendingPerm: PermissionReq? = null,
    val pendingQuestion: Pair<Question, CompletableDeferred<String>>? = null,
    val todos: List<Todo> = emptyList(),
) {
    val isBusy: Boolean get() = currentId != null && currentId in busy
    val selectedProvider: CatalogProvider? get() = providers.firstOrNull { it.id == providerId }
    val canSend: Boolean get() = input.isNotBlank() && !isBusy && providerId != null
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app.appGraph
    private val engine: Engine = graph.engine

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var turnJob: Job? = null

    init {
        _state.update {
            it.copy(
                providers = graph.registry.catalog.providers,
                providerId = graph.registry.defaultProvider()?.id,
                modelId = graph.registry.catalog.defaultModelId,
            )
        }
        refreshSessions()
        openSession(null)
        observePermissions()
    }

    /** Mirrors the live permission question into [UiState] for the sheet. */
    private fun observePermissions() = viewModelScope.launch {
        graph.permQueue.pending.collect { slot ->
            _state.update { it.copy(pendingPerm = slot?.req) }
        }
    }

    /** Answers the outstanding permission question (sheet buttons). */
    fun replyPermission(id: String, choice: String) {
        graph.permQueue.reply(id, choice)
        // Persist an "Always allow" so the same request never asks again.
        val req = _state.value.pendingPerm
        if (choice == PermissionReq.ALWAYS_ALLOW && req != null) {
            viewModelScope.launch {
                req.resources.forEach { graph.permStore.put(PermRule(req.action, it, PermRule.ALLOW)) }
            }
        }
    }

    // ---- sessions ---------------------------------------------------------

    fun refreshSessions() = viewModelScope.launch {
        _state.update { it.copy(loadingSessions = true) }
        val list = engine.list()
        _state.update { it.copy(sessions = list, loadingSessions = false) }
    }

    fun openSession(id: String?) = viewModelScope.launch {
        val session = engine.open(id)
        ensureWorkspace(session)
        val providerId = session.providerId.ifBlank { graph.registry.defaultProvider()?.id.orEmpty() }
        val modelId = session.modelId.ifBlank {
            graph.registry.defaultModelId(providerId).ifBlank { graph.registry.catalog.defaultModelId.orEmpty() }
        }
        session.providerId = providerId
        session.modelId = modelId
        _state.update {
            it.copy(
                currentId = session.id,
                title = session.title.ifBlank { "New session" },
                items = ChatProjector.toItems(session, engine.isBusy(session.id)),
                providerId = providerId,
                modelId = modelId,
                models = graph.registry.provider(providerId)?.models.orEmpty(),
                tokens = session.totalTokens,
                cost = session.costUsd,
                error = null,
            )
        }
    }

    fun newSession() = openSession(null)

    fun deleteSession(id: String) = viewModelScope.launch {
        engine.delete(id)
        if (_state.value.currentId == id) openSession(null) else refreshSessions()
    }

    fun duplicateSession(id: String) = viewModelScope.launch {
        engine.duplicate(id)
        refreshSessions()
    }

    fun renameSession(id: String, title: String) = viewModelScope.launch {
        val session = engine.session(id) ?: engine.open(id)
        session.title = title
        session.titleGenerated = true
        engine.persist(session)
        if (_state.value.currentId == id) _state.update { it.copy(title = title) }
        refreshSessions()
    }

    // ---- composer ---------------------------------------------------------

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    fun selectProvider(providerId: String) {
        val modelId = graph.registry.defaultModelId(providerId)
        val session = engine.session(_state.value.currentId)
        session?.let { it.providerId = providerId; it.modelId = modelId }
        _state.update {
            it.copy(
                providerId = providerId,
                modelId = modelId,
                models = graph.registry.provider(providerId)?.models.orEmpty(),
            )
        }
        session?.let { s -> viewModelScope.launch { engine.persist(s) } }
    }

    fun selectModel(modelId: String) {
        val session = engine.session(_state.value.currentId)
        session?.let { it.modelId = modelId }
        _state.update { it.copy(modelId = modelId) }
        session?.let { s -> viewModelScope.launch { engine.persist(s) } }
    }

    fun send() {
        val snapshot = _state.value
        val text = snapshot.input.trim()
        if (text.isEmpty() || snapshot.isBusy) return
        val session = engine.session(snapshot.currentId) ?: return
        ensureWorkspace(session)

        session.msgs += Msg.user(text)
        session.lastPrompt = text
        _state.update { it.copy(input = "", error = null) }
        refreshItems(session)

        turnJob = viewModelScope.launch {
            engine.send(
                session,
                graph.runner(
                    askUser = { question -> askQuestion(question) },
                    onTodos = { todos -> onTodos(session, todos) },
                ),
                graph.systemPrompt(session.directory, session.agent),
            ).collect { event ->
                handle(event, session)
            }
            refreshItems(session)
            refreshSessions()
        }
    }

    /** Publishes a `question` tool call to the UI and suspends for the answer. */
    private suspend fun askQuestion(question: Question): String {
        val deferred = CompletableDeferred<String>()
        _state.update { it.copy(pendingQuestion = question to deferred) }
        return try {
            deferred.await()
        } finally {
            _state.update { it.copy(pendingQuestion = null) }
        }
    }

    /** A `todo` tool call replaces the session plan and the UI's copy of it. */
    private fun onTodos(session: Session, todos: List<Todo>) {
        session.todos = todos.toMutableList()
        _state.update { it.copy(todos = todos) }
    }

    /** Answers the outstanding `question` tool call (from the question sheet). */
    fun answerQuestion(text: String) {
        _state.value.pendingQuestion?.second?.complete(text)
        _state.update { it.copy(pendingQuestion = null) }
    }

    fun stop() {
        turnJob?.cancel()
        turnJob = null
        engine.session(_state.value.currentId)?.let { session ->
            session.msgs.lastOrNull()?.let { if (!it.completed) it.completed = true }
            viewModelScope.launch { engine.persist(session) }
            refreshItems(session)
        }
        refreshSessions()
    }

    private fun handle(event: TurnEvent, session: Session) {
        when (event) {
            is TurnEvent.TextDelta,
            is TurnEvent.ReasoningDelta,
            is TurnEvent.StepDone,
            is TurnEvent.ToolStarted,
            is TurnEvent.ToolFinished,
            -> refreshItems(session)

            is TurnEvent.Usage -> _state.update {
                it.copy(tokens = session.totalTokens, cost = session.costUsd)
            }

            is TurnEvent.Notice -> session.msgs += Msg.notice(event.text)

            is TurnEvent.Failed -> {
                _state.update { it.copy(error = event.message) }
                refreshItems(session)
            }
        }
    }

    private fun refreshItems(session: Session) {
        _state.update {
            it.copy(
                items = ChatProjector.toItems(session, engine.isBusy(session.id)),
                busy = if (engine.isBusy(session.id)) it.busy + session.id else it.busy - session.id,
                title = session.title.ifBlank { "New session" },
                tokens = session.totalTokens,
                cost = session.costUsd,
            )
        }
    }

    /**
     * Gives the session a real sandbox root before any tool can run. Without a
     * directory, `Sandbox.resolve` would canonicalise an empty path to the
     * process working directory — a path-safety hole. The folder is created
     * lazily and persisted the first time it is needed.
     */
    private fun ensureWorkspace(session: Session) {
        if (session.directory.isNotBlank()) return
        val dir = File(graph.workspaceRoot, session.id).apply { mkdirs() }
        session.directory = dir.path
        viewModelScope.launch { engine.persist(session) }
    }
}
