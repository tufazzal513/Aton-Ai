package com.atonai.data.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Reads the compact asset format (04_DATA_MODELS.md §8).
 *
 * Provider object: `{ id, name, kind, api, env, models: [ [9-tuple], ... ], custom? }`
 * Model tuple: `[id, name, context, reasoning(0|1), costIn, costOut,
 *                costCacheRead, costCacheWrite, maxOutput]`
 *
 * Written by hand rather than via @Serializable so the tuple layout is explicit
 * and every field has a forgiving default — a malformed model drops out instead
 * of failing the whole catalog.
 */
object CatalogParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): Catalog {
        val root = json.parseToJsonElement(text).jsonObject
        val providers = root["providers"]?.jsonArray.orEmpty().mapNotNull { element ->
            runCatching { provider(element.jsonObject) }.getOrNull()
        }
        return Catalog(
            providers = providers,
            defaultProviderId = root.str("defaultProviderId"),
            defaultModelId = root.str("defaultModelId"),
        )
    }

    private fun provider(obj: JsonObject): CatalogProvider {
        val id = obj.str("id") ?: error("provider without id")
        return CatalogProvider(
            id = id,
            name = obj.str("name") ?: id,
            kind = obj.str("kind") ?: "openai",
            api = obj.str("api").orEmpty(),
            env = obj.str("env").orEmpty(),
            models = obj["models"]?.jsonArray.orEmpty().mapNotNull { m ->
                runCatching { model(m.jsonArray) }.getOrNull()
            },
            custom = obj.bool("custom") ?: false,
        )
    }

    /** A model is either the 9-element tuple or (for our own asset) an object. */
    private fun model(arr: JsonArray): CatalogModel? {
        if (arr.size < 1) return null
        fun s(i: Int) = arr.getOrNull(i)?.jsonPrimitive?.contentOrNull()
        fun l(i: Int, d: Long = 0L) = arr.getOrNull(i)?.jsonPrimitive?.longOrNull() ?: d
        fun d(i: Int) = arr.getOrNull(i)?.jsonPrimitive?.doubleOrNull() ?: 0.0
        val id = s(0) ?: return null
        return CatalogModel(
            id = id,
            name = s(1) ?: id,
            context = l(2, 128_000L),
            reasoning = l(3).toInt(),
            costIn = d(4),
            costOut = d(5),
            costCacheRead = d(6),
            costCacheWrite = d(7),
            maxOutput = l(8, 8_192L),
        )
    }

    // --- lenient JSON helpers: return null instead of throwing on shape mismatch ---

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.jsonPrimitive?.let { runCatching { it.boolean }.getOrNull() }

    private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else content

    private fun JsonPrimitive.longOrNull(): Long? =
        runCatching { long }.getOrNull() ?: runCatching { double.toLong() }.getOrNull()

    private fun JsonPrimitive.doubleOrNull(): Double? = runCatching { double }.getOrNull()
}
