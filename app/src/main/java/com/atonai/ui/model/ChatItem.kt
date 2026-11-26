package com.atonai.ui.model

/** High-level chat rows the list renders (04_DATA_MODELS.md §4). */
sealed interface ChatItem {
    val id: String

    data class User(
        override val id: String,
        val text: String,
        val images: List<String> = emptyList(),
    ) : ChatItem

    data class Assistant(
        override val id: String,
        val blocks: List<Block>,
        val streaming: Boolean = false,
        val error: String? = null,
    ) : ChatItem

    data class Notice(override val id: String, val text: String) : ChatItem

    data class Shell(override val id: String, val command: String, val output: String) : ChatItem
}

/** Rendered blocks inside an assistant item (04_DATA_MODELS.md §5). */
sealed interface Block {
    data class Text(val text: String) : Block

    data class Reasoning(val text: String) : Block

    data class Tool(
        val id: String,
        val name: String,
        val input: String,
        val output: String,
        val status: String,
        val summary: String,
        val extra: String = "",
    ) : Block
}
