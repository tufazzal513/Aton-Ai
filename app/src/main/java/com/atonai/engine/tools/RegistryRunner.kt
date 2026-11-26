package com.atonai.engine.tools

import com.atonai.data.perms.PermRule
import com.atonai.engine.ToolRunCtx
import com.atonai.engine.ToolRunner
import com.atonai.engine.llm.Part
import com.atonai.engine.llm.ToolSpec
import com.atonai.engine.perm.PermGate
import com.atonai.engine.perm.PermQueue

/**
 * The real [ToolRunner] (M6). It looks the tool up by name, parses the
 * streamed JSON arguments, runs it inside the session sandbox and maps every
 * failure onto a `status` the UI understands — a tool never throws out of the
 * agent loop.
 *
 * M7 adds the permission gate: [rules] supplies the persisted [PermRule] list
 * and [queue] surfaces questions to the UI, so a mutating tool pauses instead
 * of acting.
 */
class RegistryRunner(
    private val registry: ToolRegistry,
    private val rules: () -> List<PermRule> = { emptyList() },
    private val queue: PermQueue? = null,
    private val askUser: (suspend (Question) -> String)? = null,
    private val onTodos: ((List<Todo>) -> Unit)? = null,
) : ToolRunner {

    override val specs: List<ToolSpec> = registry.specs(readOnly = false)

    override suspend fun run(call: Part.ToolCall, ctx: ToolRunCtx): Part.ToolCall {
        val tool = registry.get(call.name)
            ?: return call.copy(status = "error", output = "Unknown tool \"${call.name}\".")

        val args = runCatching { Args.parse(call.arguments) }
            .getOrElse {
                return call.copy(
                    status = "error",
                    output = "Bad arguments: ${it.message}. Send valid JSON.",
                )
            }

        val gate = PermGate(
            rules = rules,
            ask = { req ->
                queue?.ask(req) ?: PermRule.ALLOW
            },
            sessionId = { ctx.sessionId },
        )

        val toolCtx = ToolCtx(
            workdir = ctx.workdir,
            sessionId = ctx.sessionId,
            readOnly = ctx.readOnly,
            trusted = ctx.trusted,
            permit = gate,
            ask = askUser ?: { "(no answer available)" },
            setTodos = onTodos ?: {},
        )

        val result = runCatching { tool.run(args, toolCtx) }
            .getOrElse { e ->
                if (e is com.atonai.data.perms.DeniedException) {
                    ToolResult.denied(e.message ?: "denied")
                } else {
                    ToolResult.error((e as? ToolError)?.message ?: (e.message ?: "tool failed"))
                }
            }

        return call.copy(
            status = if (result.error) result.status else ToolResult.STATUS_OK,
            output = result.output,
            extra = result.extra,
        )
    }
}
