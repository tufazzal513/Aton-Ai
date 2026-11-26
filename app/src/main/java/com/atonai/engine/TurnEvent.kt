package com.atonai.engine

import com.atonai.engine.llm.Part
import com.atonai.engine.llm.Usage

/** Events emitted while one assistant turn runs (consumed by the UI). */
sealed interface TurnEvent {
    data class TextDelta(val text: String) : TurnEvent
    data class ReasoningDelta(val text: String) : TurnEvent

    /** A completed assistant step: its text and ordered parts. */
    data class StepDone(val text: String, val parts: List<Part>, val stop: Boolean) : TurnEvent

    data class ToolStarted(val call: Part.ToolCall) : TurnEvent
    data class ToolFinished(val call: Part.ToolCall) : TurnEvent

    data class Usage(val usage: Usage, val deltaUsd: Double, val totalUsd: Double) : TurnEvent

    data class Notice(val text: String) : TurnEvent
    data class Failed(val message: String) : TurnEvent
}
