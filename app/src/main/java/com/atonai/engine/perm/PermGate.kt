package com.atonai.engine.perm

import com.atonai.data.perms.Action
import com.atonai.data.perms.DeniedException
import com.atonai.data.perms.PermMatcher
import com.atonai.data.perms.PermRule
import com.atonai.data.perms.PermissionReq
import com.atonai.core.Ids
import kotlinx.coroutines.CompletableDeferred

/** One question waiting for the user, with the deferred the tool blocks on. */
class PendingPerm(
    val req: PermissionReq,
    val reply: CompletableDeferred<String> = CompletableDeferred(),
)

/**
 * The permission gate injected into [ToolCtx]. A tool calls [permit] before any
 * side effect; the gate consults the rule list, then falls back to the policy
 * default, and suspends on the user when the answer is "ask".
 *
 *   allow → return normally
 *   deny  → throw [DeniedException]
 *   ask   → put a [PendingPerm] in the queue, suspend until the UI replies
 */
class PermGate(
    private val rules: () -> List<PermRule>,
    private val ask: suspend (PermissionReq) -> String,
    private val sessionId: () -> String = { "" },
) {

    /**
     * Returns when the action is allowed; throws [DeniedException] otherwise.
     * [resource] is the path/host/command the action applies to.
     */
    suspend fun permit(action: String, resource: String, detail: String = "") {
        when (val decision = PermMatcher.decide(rules(), action, resource, PermMatcher.policyDefault(action))) {
            PermRule.ALLOW -> return
            PermRule.DENY -> throw DeniedException("Permission denied for $action: $resource")
            else -> {
                val choice = ask(buildReq(action, resource, detail))
                if (choice == PermissionReq.DENY) {
                    throw DeniedException("Permission denied for $action: $resource")
                }
            }
        }
    }

    /** Convenience for the common "may I touch this path" question. */
    suspend fun permitPath(path: String, write: Boolean, detail: String = "") =
        permit(if (write) Action.WRITE else Action.READ, path, detail)

    private fun buildReq(action: String, resource: String, detail: String) = PermissionReq(
        id = Ids.new(),
        sessionId = sessionId(),
        action = action,
        resources = listOf(resource),
        message = detail.ifBlank { "AtonAI wants to $action $resource" },
        options = PermissionReq.defaultOptions,
    )

    companion object {
        /** A gate that allows everything — useful for tests and read-only modes. */
        fun permissive(): PermGate = PermGate(
            rules = { emptyList() },
            ask = { PermissionReq.ALLOW_ONCE },
        )

        /** A gate that denies everything not explicitly allowed. */
        fun hostile(): PermGate = PermGate(
            rules = { emptyList() },
            ask = { PermissionReq.DENY },
        )
    }
}
