package com.atonai.engine

import com.atonai.core.Clock
import com.atonai.core.Ids
import com.atonai.engine.llm.Part
import com.atonai.engine.tools.Todo
import kotlinx.serialization.Serializable

/** A persisted chat message (04_DATA_MODELS.md §2). */
@Serializable
data class Msg(
    val id: String = Ids.new(),
    val role: String,                  // user | assistant | notice | shell
    var kind: String = "",
    var text: String = "",
    var think: String = "",
    var llmText: String = "",
    var model: String = "",
    var time: Long = Clock.now(),
    var completed: Boolean = false,
    var error: String? = null,
    val images: MutableList<String> = mutableListOf(),
    val parts: MutableList<Part> = mutableListOf(),
) {
    companion object {
        const val USER = "user"
        const val ASSISTANT = "assistant"
        const val NOTICE = "notice"
        const val SHELL = "shell"

        fun user(text: String, images: List<String> = emptyList()) =
            Msg(role = USER, text = text, images = images.toMutableList(), completed = true)

        fun notice(text: String, kind: String = "notice") =
            Msg(role = NOTICE, text = text, kind = kind, completed = true)

        fun shell(command: String, output: String) =
            Msg(role = SHELL, text = command, llmText = output, completed = true)
    }
}

/** A persisted session (04_DATA_MODELS.md §1). */
@Serializable
data class Session(
    val id: String = Ids.new(),
    var title: String = "",
    var titleGenerated: Boolean = false,
    val created: Long = Clock.now(),
    var updated: Long = Clock.now(),
    var directory: String = "",
    var extraDirs: MutableList<String> = mutableListOf(),
    var providerId: String = "",
    var modelId: String = "",
    var agent: String = "general",
    var effort: String = "",
    var inTokens: Long = 0,
    var outTokens: Long = 0,
    var cacheRead: Long = 0,
    var cacheWrite: Long = 0,
    var costUsd: Double = 0.0,
    var lastPrompt: String = "",
    var todos: MutableList<Todo> = mutableListOf(),
    var slow: Boolean = false,
    var stuck: Boolean = false,
    val msgs: MutableList<Msg> = mutableListOf(),
    val instructionsSeen: MutableSet<String> = mutableSetOf(),
) {
    val totalTokens: Long get() = inTokens + outTokens
}

/** Lightweight row for the sessions list (04_DATA_MODELS.md §6). */
data class SessionInfo(
    val id: String,
    val title: String,
    val updated: Long,
    val model: String,
    val agent: String,
    val directory: String,
    val tokens: Long,
    val cost: Double,
) {
    /** Second line of the session row: model + folder or usage. */
    val preview: String
        get() = listOfNotNull(
            model.takeIf { it.isNotBlank() },
            directory.takeIf { it.isNotBlank() }?.substringAfterLast('/'),
        ).joinToString(" · ")

    companion object {
        fun of(s: Session, modelLabel: String) = SessionInfo(
            id = s.id,
            title = s.title.ifBlank { "Untitled" },
            updated = s.updated,
            model = modelLabel.ifBlank { s.modelId },
            agent = s.agent,
            directory = s.directory,
            tokens = s.totalTokens,
            cost = s.costUsd,
        )
    }
}
