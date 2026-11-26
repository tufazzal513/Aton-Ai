package com.atonai.engine.tools

/**
 * Parser for the `*** Begin Patch` envelope (see [PatchTool]). It is lenient in
 * exactly two ways: the surrounding `*** Begin/End Patch` lines are optional,
 * and `@@` context markers may carry arbitrary trailing text.
 */
object PatchParser {

    data class HunkLine(val kind: Char, val text: String, val line: Int = 0)

    sealed interface Op {
        val path: String

        data class Add(override val path: String, val lines: List<String>) : Op
        data class Delete(override val path: String) : Op
        data class Update(override val path: String, val hunks: List<List<HunkLine>>) : Op
    }

    fun parse(raw: String): List<Op> {
        val out = ArrayList<Op>()
        val lines = raw.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("*** Add File:") -> {
                    val path = line.removePrefix("*** Add File:").trim()
                    val body = ArrayList<String>()
                    i++
                    while (i < lines.size && !lines[i].startsWith("***")) {
                        val l = lines[i]
                        if (l.startsWith("+")) body.add(l.substring(1))
                        else if (l.isNotBlank()) body.add(l)
                        i++
                    }
                    out.add(Op.Add(path, body))
                }

                line.startsWith("*** Delete File:") -> {
                    out.add(Op.Delete(line.removePrefix("*** Delete File:").trim()))
                    i++
                }

                line.startsWith("*** Update File:") -> {
                    val path = line.removePrefix("*** Update File:").trim()
                    val hunks = ArrayList<List<HunkLine>>()
                    var current: MutableList<HunkLine>? = null
                    var lineNo = 0
                    i++
                    while (i < lines.size && !lines[i].startsWith("***")) {
                        val l = lines[i]
                        when {
                            l.startsWith("@@") -> {
                                current = ArrayList()
                                hunks.add(current)
                                lineNo = parseLineNo(l)
                            }
                            l.startsWith("+") -> current?.add(HunkLine('+', l.substring(1), lineNo))
                            l.startsWith("-") -> current?.add(HunkLine('-', l.substring(1), lineNo))
                            l.startsWith(" ") -> current?.add(HunkLine(' ', l.substring(1), lineNo))
                            l.isEmpty() -> current?.add(HunkLine(' ', "", lineNo))
                            else -> current?.add(HunkLine(' ', l, lineNo))
                        }
                        if (current != null && !l.startsWith("@@")) lineNo++
                        i++
                    }
                    out.add(Op.Update(path, hunks))
                }

                else -> i++
            }
        }
        return out
    }

    /** `@@ -12,3 +12,4 @@` → 12; a bare `@@` → 0 (unknown, match anywhere). */
    private fun parseLineNo(marker: String): Int {
        val m = Regex("-\\s*(\\d+)").find(marker) ?: return 0
        return m.groupValues[1].toIntOrNull() ?: 0
    }
}
