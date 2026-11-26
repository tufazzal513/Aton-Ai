package com.atonai.engine.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Typed argument access with clear error messages. Models frequently send a
 * number as a string or an array as a comma list; the getters accept the common
 * variants instead of failing the whole call.
 */
class Args(private val obj: JsonObject) {

    fun str(name: String, default: String? = null): String {
        val value = (obj[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: (obj[name] as? JsonPrimitive)?.content
        return value ?: default ?: throw ToolError("Missing required argument \"$name\"")
    }

    fun optStr(name: String): String? =
        (obj[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    fun int(name: String, default: Int): Int =
        (obj[name] as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toIntOrNull() } ?: default

    fun bool(name: String, default: Boolean): Boolean =
        (obj[name] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.toBooleanStrictOrNull() } ?: default

    fun strings(name: String): List<String> = when (val v = obj[name]) {
        is kotlinx.serialization.json.JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.content }
        is JsonPrimitive -> v.content.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(raw: String): JsonObject {
            val text = raw.trim().ifEmpty { "{}" }
            return runCatching { json.parseToJsonElement(text) as? JsonObject }
                .getOrNull()
                ?: throw ToolError("Arguments are not a JSON object: ${raw.take(120)}")
        }
    }
}
