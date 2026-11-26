package com.atonai.data

import android.content.Context
import com.atonai.data.model.WorkspaceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the app's private workspace directory. All file operations are
 * sandboxed to this directory - paths are validated before use.
 */
class WorkspaceManager(context: Context) {

    val root: File = File(context.filesDir, "workspace").apply { mkdirs() }

    fun list(): List<WorkspaceFile> = root.listFiles()
        .orEmpty()
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        .map { toModel(it) }

    suspend fun read(name: String): String = withContext(Dispatchers.IO) {
        resolve(name).takeIf { it.isFile }?.readText() ?: ""
    }

    suspend fun write(name: String, content: String): WorkspaceFile = withContext(Dispatchers.IO) {
        val file = resolve(name)
        file.parentFile?.mkdirs()
        file.writeText(content)
        toModel(file)
    }

    suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        resolve(name).delete()
    }

    fun exists(name: String): Boolean = resolve(name).exists()

    /** Resolves a workspace-relative name, refusing path traversal. */
    private fun resolve(name: String): File {
        val clean = name.replace('\\', '/').trim().trimStart('/')
        require(clean.isNotEmpty()) { "Empty file name" }
        require(!clean.contains("../")) { "Invalid path" }
        val file = File(root, clean)
        require(file.canonicalPath.startsWith(root.canonicalPath)) { "Path escapes workspace" }
        return file
    }

    private fun toModel(file: File): WorkspaceFile = WorkspaceFile(
        name = file.name,
        path = file.relativeTo(root).path,
        isDirectory = file.isDirectory,
        sizeBytes = if (file.isFile) file.length() else 0L,
    )
}
