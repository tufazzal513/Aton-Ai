package com.atonai.data.perms

/**
 * Matches a request against the persisted rule list. Rules are evaluated
 * most-specific-first: an exact pattern beats a wildcard, and a longer pattern
 * beats a shorter one, so a narrower glob refines a broader one.
 *
 * Pure and dependency-free so the whole decision table is unit-testable.
 */
object PermMatcher {

    /** The winning rule for [action]/[resource], or null when nothing matches. */
    fun match(rules: List<PermRule>, action: String, resource: String): PermRule? =
        rules
            .filter { it.action == action && matches(it.pattern, resource) }
            .maxByOrNull { specificity(it.pattern) }

    /**
     * Final decision string: the matched rule's decision, or [fallback] when no
     * rule applies. The caller supplies the policy default per action.
     */
    fun decide(
        rules: List<PermRule>,
        action: String,
        resource: String,
        fallback: String,
    ): String = match(rules, action, resource)?.decision ?: fallback

    /** Glob compare; `*` spans anything (paths here are short). */
    fun matches(pattern: String, value: String): Boolean {
        if (pattern == "*" || pattern == "**") return true
        if (pattern == value) return true
        val regex = globToRegex(pattern)
        return regex.matches(value) || value.startsWith(pattern)
    }

    /** Higher = more specific, used to break ties deterministically. */
    fun specificity(pattern: String): Int =
        pattern.count { it != '*' && it != '?' } * 2 + if (pattern.contains('/')) 1 else 0

    fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> {
                    // Both `*` and `**` span path separators here: permission
                    // patterns are short and users expect `lib/*` to cover `lib`.
                    sb.append(".*")
                    if (i + 1 < glob.length && glob[i + 1] == '*') i++
                }
                '?' -> sb.append('.')
                '.', '(', ')', '+', '|', '^', '$', '@', '%', '[', ']', '{', '}', '\\' ->
                    sb.append('\\').append(c)
                else -> sb.append(c)
            }
            i++
        }
        sb.append('$')
        return Regex(sb.toString())
    }

    /**
     * The policy default per action (02_ARCHITECTURE.md §8, 03_TOOLS_SPEC.md):
     * read-only work is allowed, everything with a side effect asks.
     */
    fun policyDefault(action: String): String = when (action) {
        Action.READ, Action.NONE -> PermRule.ALLOW
        else -> PermRule.ASK
    }
}
