package com.atonai.engine.tools

import java.io.File
import java.io.IOException

/**
 * Path safety for every file tool. The model may pass absolute paths, `~`,
 * Windows separators or `..`; all of it is normalised and then checked to stay
 * inside the session workdir. Symlinks are resolved so a link cannot escape.
 */
object Sandbox {

    /** Absolute, canonical [File] for [raw], or throws [ToolError]. */
    fun resolve(raw: String, ctx: ToolCtx): File {
        val text = raw.trim()
        if (text.isEmpty()) throw ToolError("Path is empty")

        val root = File(ctx.workdir).canonicalFile
        if (!root.exists()) root.mkdirs()

        val candidate = when {
            text == "~" -> root
            text.startsWith("~/") -> File(root, text.removePrefix("~/"))
            File(text).isAbsolute -> File(text)
            else -> File(root, text)
        }

        val canonical = try {
            candidate.canonicalFile
        } catch (e: IOException) {
            throw ToolError("Bad path: ${e.message}")
        }

        if (!isInside(canonical, root)) {
            throw ToolError(
                "Path \"$raw\" is outside the workspace (${root.path}). " +
                    "Use a relative path inside the project.",
            )
        }
        return canonical
    }

    /** Path relative to the workdir, for display in tool summaries. */
    fun display(file: File, ctx: ToolCtx): String = runCatching {
        val root = File(ctx.workdir).canonicalFile
        val rel = file.canonicalFile.relativeTo(root).path
        if (rel.isEmpty()) "." else rel
    }.getOrDefault(file.path)

    fun isInside(child: File, root: File): Boolean {
        val c = child.path
        val r = root.path
        return c == r || c.startsWith(r + File.separator)
    }

    /** Rejects `../` tricks in glob/grep patterns before the walker runs. */
    fun assertRelativePattern(pattern: String) {
        val normalised = pattern.replace('\\', '/')
        if (normalised.split('/').any { it == ".." }) {
            throw ToolError("Pattern may not contain '..'")
        }
    }
}
