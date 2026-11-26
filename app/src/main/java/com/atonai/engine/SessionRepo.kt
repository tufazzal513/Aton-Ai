package com.atonai.engine

import com.atonai.core.Clock
import com.atonai.core.Ids
import com.atonai.core.JsonIface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists sessions as one JSON file each under `filesDir/sessions/<id>.json`
 * (decision D-04). Writes go to a `.tmp` file and are then atomically renamed,
 * so a crash mid-write never corrupts an existing session.
 */
class SessionRepo(private val root: File) {

    init {
        if (!root.exists()) root.mkdirs()
    }

    private fun file(id: String) = File(root, "$id.json")

    suspend fun list(): List<SessionInfo> = withContext(Dispatchers.IO) {
        root.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { readFile(it) }
            ?.map { SessionInfo.of(it, it.modelId) }
            ?.sortedByDescending { it.updated }
            .orEmpty()
    }

    suspend fun load(id: String): Session? = withContext(Dispatchers.IO) {
        readFile(file(id))
    }

    suspend fun save(session: Session) = withContext(Dispatchers.IO) {
        session.updated = Clock.now()
        val target = file(session.id)
        val tmp = File(root, "${session.id}.json.tmp")
        tmp.writeText(JsonIface.json.encodeToString(Session.serializer(), session))
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            // Fallback for filesystems that dislike replace-rename.
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        file(id).delete()
        File(root, "$id.json.tmp").delete()
        Unit
    }

    suspend fun duplicate(id: String): Session? = withContext(Dispatchers.IO) {
        val original = readFile(file(id)) ?: return@withContext null
        val copy = original.copy(
            id = Ids.new(),
            title = original.title + " (copy)",
            created = Clock.now(),
            updated = Clock.now(),
        )
        save(copy)
        copy
    }

    suspend fun rename(id: String, title: String) = withContext(Dispatchers.IO) {
        val s = readFile(file(id)) ?: return@withContext
        s.title = title
        s.titleGenerated = true
        save(s)
    }

    private fun readFile(f: File): Session? =
        runCatching { JsonIface.json.decodeFromString(Session.serializer(), f.readText()) }
            .getOrNull()
}
