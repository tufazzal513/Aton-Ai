package com.atonai.engine.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PlanToolsTest {

    private lateinit var root: File
    private lateinit var ctx: ToolCtx

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "plan-${System.nanoTime()}")
        root.mkdirs()
        ctx = ToolCtx(workdir = root.path)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun todo_is_published_to_the_context() = runBlocking {
        var received: List<Todo> = emptyList()
        val ctxWithTodos = ctx.copy(setTodos = { received = it })
        val args = buildJsonObject {
            putJsonArray("todos") {
                add(JsonObject(mapOf("content" to kotlinx.serialization.json.JsonPrimitive("write tests"),
                    "status" to kotlinx.serialization.json.JsonPrimitive("in_progress"))))
                add(JsonObject(mapOf("content" to kotlinx.serialization.json.JsonPrimitive("ship"))))
            }
        }
        val result = TodoTool().run(args, ctxWithTodos)
        assertFalse(result.error)
        assertEquals(2, received.size)
        assertEquals("write tests", received[0].content)
        assertEquals("in_progress", received[0].status)
        assertTrue(result.output.startsWith("Todo list updated:"))
        assertTrue(result.output.contains("[~] write tests"))
        assertTrue(result.output.contains("[ ] ship"))
    }

    @Test
    fun todo_rejects_non_array() = runBlocking {
        val result = TodoTool().run(buildJsonObject { put("todos", "nope") }, ctx)
        assertTrue(result.error)
    }

    @Test
    fun question_returns_the_context_answer() = runBlocking {
        val ctxWithAsk = ctx.copy(ask = { "yes please" })
        val result = QuestionTool().run(buildJsonObject { put("question", "continue?") }, ctxWithAsk)
        assertEquals("yes please", result.output)
    }

    @Test
    fun question_passes_options_through() = runBlocking {
        var seen: Question? = null
        val ctxWithAsk = ctx.copy(ask = { seen = it; "a" })
        QuestionTool().run(
            buildJsonObject {
                put("question", "pick one")
                putJsonArray("options") { add(kotlinx.serialization.json.JsonPrimitive("a")); add(kotlinx.serialization.json.JsonPrimitive("b")) }
            },
            ctxWithAsk,
        )
        assertEquals(listOf("a", "b"), seen?.options)
    }

    @Test
    fun blank_answer_is_reported_clearly() = runBlocking {
        val ctxWithAsk = ctx.copy(ask = { "" })
        val result = QuestionTool().run(buildJsonObject { put("question", "q") }, ctxWithAsk)
        assertTrue(result.output.contains("did not answer"))
    }
}
