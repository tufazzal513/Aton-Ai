package com.atonai.engine

import com.atonai.core.Clock
import com.atonai.core.Ids
import com.atonai.data.ProviderRegistry
import com.atonai.data.catalog.CatalogProvider
import com.atonai.data.prefs.SecretStore
import com.atonai.engine.llm.Llm
import com.atonai.engine.llm.LlmEvent
import com.atonai.engine.llm.LlmRequest
import com.atonai.engine.llm.Part
import com.atonai.engine.llm.Retry
import com.atonai.engine.llm.StepAcc
import com.atonai.engine.llm.ToolSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * The single place sessions are loaded, mutated and driven by an LLM. One
 * instance is shared by the UI and the foreground service (02_ARCHITECTURE.md).
 *
 * The engine owns the session's message list; the UI only reads it and re-runs
 * [com.atonai.ui.model.ChatProjector] after each event.
 */
class Engine(
    private val repo: SessionRepo,
    private val registry: ProviderRegistry,
    private val secrets: SecretStore,
    private val llm: Llm,
) {

    private val live = LinkedHashMap<String, Session>()
    private val busy = LinkedHashSet<String>()

    fun session(id: String?): Session? = id?.let { live[it] }
    fun isBusy(id: String?): Boolean = id != null && id in busy

    suspend fun list(): List<SessionInfo> = repo.list()

    /** Loads (or creates) a session and caches it in [live]. */
    suspend fun open(id: String?): Session {
        id?.let { live[it] }?.let { return it }
        val loaded = id?.let { repo.load(it) }
        val session = loaded ?: Session(title = "New session")
        live[session.id] = session
        return session
    }

    suspend fun delete(id: String) {
        busy.remove(id)
        live.remove(id)
        repo.delete(id)
    }

    suspend fun duplicate(id: String): Session? = repo.duplicate(id)?.also { live[it.id] = it }

    suspend fun persist(session: Session) = repo.save(session)

    /**
     * Runs one full assistant turn as a cold [Flow] of [TurnEvent]s. Cancelling
     * the collector stops the turn and leaves the partial message persisted.
     * With [run] = [NoTools] this is a plain streaming chat loop.
     */
    fun send(
        session: Session,
        run: ToolRunner,
        systemPrompt: String,
        maxSteps: Int = MAX_STEPS,
    ): Flow<TurnEvent> {
        val provider = registry.provider(session.providerId) ?: registry.defaultProvider()
        if (provider == null) return flowOf(TurnEvent.Failed("No provider configured"))
        val model = session.modelId.ifBlank { registry.defaultModelId(provider.id) }

        return flow {
            busy += session.id
            try {
                val apiKey = secrets.apiKey(provider.id)
                var step = 0
                while (true) {
                    val acc = StepAcc()
                    val stepMsg = Msg(role = Msg.ASSISTANT, model = model)
                    session.msgs += stepMsg
                    var failed: String? = null

                    Retry.stream(llm, buildRequest(session, provider, model, systemPrompt, run.specs, apiKey))
                        .collect { event ->
                            when (event) {
                                is LlmEvent.TextDelta -> {
                                    acc.onText(event.text)
                                    stepMsg.text = acc.textValue
                                    emit(TurnEvent.TextDelta(event.text))
                                }

                                is LlmEvent.ReasoningDelta -> {
                                    acc.onReasoning(event.text)
                                    stepMsg.think = acc.reasoningValue
                                    emit(TurnEvent.ReasoningDelta(event.text))
                                }

                                is LlmEvent.ToolCallDelta -> acc.onToolDelta(event)

                                is LlmEvent.Usage -> {
                                    val delta = Cost.add(registry.model(provider.id, model), session, event.usage)
                                    emit(TurnEvent.Usage(event.usage, delta, session.costUsd))
                                }

                                is LlmEvent.Done -> Unit

                                is LlmEvent.Retry -> emit(
                                    TurnEvent.Notice("Retrying (${event.attempt + 1}/${event.max})…"),
                                )

                                is LlmEvent.Failed -> failed = event.message
                            }
                        }

                    stepMsg.parts.clear()
                    stepMsg.parts.addAll(acc.parts())
                    stepMsg.llmText = acc.textValue
                    stepMsg.completed = true
                    session.updated = Clock.now()

                    if (failed != null) {
                        stepMsg.error = failed
                        emit(TurnEvent.Failed(failed))
                        return@flow
                    }

                    val calls = acc.toolCalls()
                    if (calls.isEmpty()) {
                        emit(TurnEvent.StepDone(acc.textValue, acc.parts(), stop = true))
                        return@flow
                    }

                    emit(TurnEvent.StepDone(acc.textValue, acc.parts(), stop = false))

                    for (call in calls) {
                        val running = call.copy(status = "running")
                        replaceCall(stepMsg, running)
                        emit(TurnEvent.ToolStarted(running))

                        val result = run.tryRun(call, session)
                        replaceCall(stepMsg, result)
                        emit(TurnEvent.ToolFinished(result))
                    }

                    step++
                    if (step >= maxSteps) {
                        emit(TurnEvent.Notice("Too many tool calls (max $maxSteps)"))
                        return@flow
                    }
                }
            } finally {
                busy.remove(session.id)
                runCatching { repo.save(session) }
            }
        }
    }

    private fun replaceCall(msg: Msg, call: Part.ToolCall) {
        val idx = msg.parts.indexOfFirst { it is Part.ToolCall && it.id == call.id }
        if (idx >= 0) msg.parts[idx] = call
    }

    private fun buildRequest(
        session: Session,
        provider: CatalogProvider,
        model: String,
        systemPrompt: String,
        tools: List<ToolSpec>,
        apiKey: String?,
    ): LlmRequest = LlmRequest(
        kind = provider.kind,
        baseUrl = provider.api,
        apiKey = apiKey,
        model = model,
        system = systemPrompt,
        turns = TurnBuilder.build(session.msgs),
        tools = tools,
        effort = session.effort,
    )

    private suspend fun ToolRunner.tryRun(call: Part.ToolCall, session: Session): Part.ToolCall =
        runCatching {
            run(call, ToolRunCtx(sessionId = session.id, workdir = session.directory))
        }.getOrElse { e -> call.copy(status = "error", output = e.message ?: "tool failed") }

    companion object {
        const val MAX_STEPS = 60
        fun newSessionId(): String = Ids.new()
    }
}
