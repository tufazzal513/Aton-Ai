package com.atonai.engine.tools

/**
 * Minimal line diff for the UI: a longest-common-subsequence over lines, then
 * unified hunks with three lines of context. It is deliberately simple —
 * correct rather than minimal, and O(n·m) is fine for the file sizes the tools
 * accept.
 */
object Diff {

    data class Line(val kind: Char, val text: String) // ' ' | '+' | '-'

    /** Unified diff text (`---`/`+++`/`@@` headers) or "" when nothing changed. */
    fun unified(name: String, before: String, after: String, context: Int = 3): String {
        if (before == after) return ""
        val a = before.lines()
        val b = after.lines()
        val ops = lcs(a, b)
        val hunks = hunks(ops, context)
        if (hunks.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("--- a/").append(name).append('\n')
        sb.append("+++ b/").append(name).append('\n')
        for (h in hunks) {
            sb.append("@@ -").append(h.aStart + 1).append(',').append(h.aCount)
                .append(" +").append(h.bStart + 1).append(',').append(h.bCount).append(" @@\n")
            h.lines.forEach { sb.append(it.kind).append(it.text).append('\n') }
        }
        return sb.toString()
    }

    /** Added / removed line counts, for the tool card summary. */
    fun stats(before: String, after: String): Pair<Int, Int> {
        if (before == after) return 0 to 0
        val ops = lcs(before.lines(), after.lines())
        return ops.count { it.kind == '+' } to ops.count { it.kind == '-' }
    }

    private data class Hunk(val aStart: Int, val aCount: Int, val bStart: Int, val bCount: Int, val lines: List<Line>)

    private fun lcs(a: List<String>, b: List<String>): List<Line> {
        // Guard: very large inputs fall back to a block replace.
        if (a.size.toLong() * b.size.toLong() > 4_000_000L) {
            return a.map { Line('-', it) } + b.map { Line('+', it) }
        }
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val out = ArrayList<Line>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { out.add(Line(' ', a[i])); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { out.add(Line('-', a[i])); i++ }
                else -> { out.add(Line('+', b[j])); j++ }
            }
        }
        while (i < n) { out.add(Line('-', a[i])); i++ }
        while (j < m) { out.add(Line('+', b[j])); j++ }
        return out
    }

    private fun hunks(ops: List<Line>, context: Int): List<Hunk> {
        val changed = ops.indices.filter { ops[it].kind != ' ' }
        if (changed.isEmpty()) return emptyList()

        val groups = ArrayList<IntRange>()
        var start = (changed.first() - context).coerceAtLeast(0)
        var end = (changed.first() + context).coerceAtMost(ops.lastIndex)
        for (idx in changed.drop(1)) {
            if (idx - context <= end + 1) {
                end = (idx + context).coerceAtMost(ops.lastIndex)
            } else {
                groups.add(start..end)
                start = (idx - context).coerceAtLeast(0)
                end = (idx + context).coerceAtMost(ops.lastIndex)
            }
        }
        groups.add(start..end)

        var aLine = 1
        var bLine = 1
        var cursor = 0
        val result = ArrayList<Hunk>(groups.size)
        for (g in groups) {
            // advance counters over the skipped part
            while (cursor < g.first) {
                when (ops[cursor].kind) {
                    ' ' -> { aLine++; bLine++ }
                    '-' -> aLine++
                    else -> bLine++
                }
                cursor++
            }
            var aCount = 0
            var bCount = 0
            val slice = ArrayList<Line>(g.last - g.first + 1)
            for (k in g) {
                val op = ops[k]
                when (op.kind) {
                    ' ' -> { aCount++; bCount++ }
                    '-' -> aCount++
                    else -> bCount++
                }
                slice.add(op)
            }
            result.add(Hunk(aLine - 1, aCount, bLine - 1, bCount, slice))
            aLine += aCount
            bLine += bCount
            cursor = g.last + 1
        }
        return result
    }
}
