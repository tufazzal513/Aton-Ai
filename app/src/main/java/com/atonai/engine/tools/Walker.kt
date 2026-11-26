package com.atonai.engine.tools

import java.io.File

/**
 * Directory walker with the ignore rules the original uses (M6): always skip
 * VCS and build output, plus anything matched by `.gitignore` in the root.
 *
 * Only a small gitignore subset is honoured — `name`, `/dir`, `dir/`, `*.ext`,
 * `prefix*`, `a/b`, and `!` negation is deliberately unsupported (kept simple,
 * documented in TOOLS_SPEC.md).
 */
class Walker(
    private val root: File,
    private val maxFiles: Int = 20_000,
    private val maxDepth: Int = 24,
) {
    private val ignore = loadIgnore()

    /** All regular files under [start], relative to [root], sorted. */
    fun files(start: File = root): List<File> {
        val out = ArrayList<File>(256)
        walk(start, 0, out)
        return out.sortedBy { it.path }
    }

    private fun walk(dir: File, depth: Int, out: MutableList<File>) {
        if (depth > maxDepth || out.size >= maxFiles) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (out.size >= maxFiles) return
            val rel = relPath(child)
            if (isIgnored(rel, child.isDirectory)) continue
            if (child.isDirectory) walk(child, depth + 1, out) else out.add(child)
        }
    }

    fun relPath(file: File): String = runCatching {
        file.relativeTo(root).path.replace(File.separatorChar, '/')
    }.getOrDefault(file.name)

    fun isIgnored(rel: String, isDir: Boolean): Boolean {
        if (rel.isEmpty()) return false
        val name = rel.substringAfterLast('/')
        if (name in ALWAYS_IGNORE_NAMES) return true
        if (isDir && name in ALWAYS_IGNORE_DIRS) return true
        if (rel.contains("/build/") && isDir && name == "build") return true
        return ignore.any { rule -> rule.matches(rel, name, isDir) }
    }

    private fun loadIgnore(): List<Rule> {
        val file = File(root, ".gitignore")
        if (!file.isFile) return emptyList()
        return runCatching { file.readLines() }.getOrDefault(emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .mapNotNull { Rule.of(it) }
    }

    private sealed interface Rule {
        fun matches(rel: String, name: String, isDir: Boolean): Boolean

        companion object {
            fun of(raw: String): Rule? {
                if (raw.startsWith("!")) return null          // negation unsupported
                val line = raw.removePrefix("./").trimEnd('/')
                if (line.isEmpty()) return null
                return when {
                    line.contains('/') -> Prefix(line)
                    line.contains('*') || line.contains('?') -> Glob(line)
                    else -> Name(line)
                }
            }
        }

        class Name(private val name: String) : Rule {
            override fun matches(rel: String, name: String, isDir: Boolean) = this.name == name
        }

        class Prefix(private val prefix: String) : Rule {
            override fun matches(rel: String, name: String, isDir: Boolean) =
                rel == prefix || rel.startsWith("$prefix/") || rel.endsWith("/$prefix")
        }

        class Glob(private val pattern: String) : Rule {
            private val regex = globToRegex(pattern)
            override fun matches(rel: String, name: String, isDir: Boolean) =
                regex.matches(name) || regex.matches(rel)
        }
    }

    companion object {
        private val ALWAYS_IGNORE_NAMES = setOf(
            ".DS_Store", "Thumbs.db", ".git", ".hg", ".svn",
        )
        private val ALWAYS_IGNORE_DIRS = setOf(
            ".git", ".gradle", ".idea", "node_modules", "__pycache__",
            ".venv", "venv", "build", "dist", ".next", ".cache", "Pods",
        )

        /** Minimal glob→regex: `*` any run except `/`, `?` one char, `**` any run. */
        fun globToRegex(glob: String): Regex {
            val sb = StringBuilder("^")
            var i = 0
            while (i < glob.length) {
                when (val c = glob[i]) {
                    '*' -> {
                        if (i + 1 < glob.length && glob[i + 1] == '*') {
                            sb.append(".*")
                            i++
                        } else {
                            sb.append("[^/]*")
                        }
                    }
                    '?' -> sb.append("[^/]")
                    '.', '(', ')', '+', '|', '^', '$', '@', '%', '[', ']', '{', '}' -> {
                        sb.append('\\').append(c)
                    }
                    else -> sb.append(c)
                }
                i++
            }
            sb.append('$')
            return Regex(sb.toString())
        }
    }
}
