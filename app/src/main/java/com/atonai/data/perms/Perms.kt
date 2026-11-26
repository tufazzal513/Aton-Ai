package com.atonai.data.perms

import kotlinx.serialization.Serializable

/**
 * Permission model (04_DATA_MODELS.md §10). A rule is `(action, pattern,
 * decision)`: the first two are matched against a request, the third is the
 * stored answer. Decisions are the same three literals the original uses.
 */
@Serializable
data class PermRule(
    val action: String,      // read | write | shell | network | code | plugin | external_directory
    val pattern: String,     // glob on path, command prefix, host or tool name
    val decision: String,    // allow | ask | deny
) {
    companion object {
        const val ALLOW = "allow"
        const val ASK = "ask"
        const val DENY = "deny"
        val decisions = listOf(ALLOW, ASK, DENY)
    }
}

/** One outstanding question shown to the user (04_DATA_MODELS.md §10). */
@Serializable
data class PermissionReq(
    val id: String,
    val sessionId: String,
    val action: String,
    val resources: List<String>,
    val message: String,
    val options: List<String>,
) {
    companion object {
        /** Option literals recovered from the reference DEX. */
        const val ALLOW_ONCE = "Allow once"
        const val ALWAYS_ALLOW = "Always allow"
        const val DENY = "Deny"

        val defaultOptions = listOf(ALLOW_ONCE, ALWAYS_ALLOW, DENY)
    }
}

/** Raised when a rule or the user says no; aborts the tool call. */
class DeniedException(message: String) : Exception(message)

/** Permission classes a tool can declare (03_TOOLS_SPEC.md §"permission classes"). */
object Action {
    const val READ = "read"
    const val WRITE = "write"
    const val SHELL = "shell"
    const val NETWORK = "network"
    const val CODE = "code"
    const val PLUGIN = "plugin"
    const val EXTERNAL = "external_directory"
    const val NONE = "none"
}
