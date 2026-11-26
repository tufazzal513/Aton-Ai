package com.atonai.engine.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Maintains the session's visible plan (TOOLS_SPEC.md §10). The list is not
 * stored here — it goes to `ToolCtx.setTodos`, which the view model turns into
 * the plan panel. Pure session state, so no permission is required.
 */
class TodoTool : Tool {
    override val name = "todo"
    override val readOnly = true
    override val description =
        "Replace the session plan with a new todo list. Use it to show the user " +
            "what you are working on and to track progress."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("todos") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("content") { put("type", "string") }
                        putJsonObject("status") {
                            put("type", "string")
                            put("description", "pending | in_progress | completed")
                        }
                        putJsonObject("priority") {
                            put("type", "string")
                            put("description", "low | medium | high")
                        }
                    }
                    putJsonArray("required") {
                        add(kotlinx.serialization.json.JsonPrimitive("content"))
                    }
                }
            }
        }
        put("required", JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("todos"))))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        val array = args["todos"] as? JsonArray
            ?: return ToolResult.error("`todos` must be an array")
        val todos = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val content = obj["content"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Todo(
                content = content,
                status = obj["status"]?.jsonPrimitive?.content ?: "pending",
                priority = obj["priority"]?.jsonPrimitive?.content ?: "medium",
            )
        }
        ctx.setTodos(todos)
        val rendered = todos.joinToString("\n") { t ->
            val mark = when (t.status) {
                "completed" -> "[x]"
                "in_progress" -> "[~]"
                else -> "[ ]"
            }
            "$mark ${t.content}"
        }
        return ToolResult.ok("Todo list updated:\n$rendered", todos.toString())
    }
}

/**
 * Lets the agent ask the user a question and block until it is answered
 * (TOOLS_SPEC.md §11). The UI supplies `ToolCtx.ask`; with no UI wired the
 * default returns a clear placeholder rather than hanging.
 */
class QuestionTool : Tool {
    override val name = "question"
    override val readOnly = true
    override val description =
        "Ask the user a question and wait for the answer. Provide options when " +
            "the choice is limited; otherwise free text is returned."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("question") { put("type", "string") }
            putJsonObject("options") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
            }
            putJsonObject("allow_free_text") { put("type", "boolean") }
        }
        put("required", JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("question"))))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        val a = Args(args)
        val text = a.str("question")
        val options = a.strings("options")
        val allowFree = a.bool("allow_free_text", options.isEmpty())
        val answer = ctx.ask(Question(text, options, allowFree))
        if (answer.isBlank()) return ToolResult.ok("The user did not answer.")
        return ToolResult.ok(answer)
    }
}
