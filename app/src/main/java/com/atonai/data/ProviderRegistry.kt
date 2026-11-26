package com.atonai.data

import android.content.Context
import com.atonai.data.catalog.Catalog
import com.atonai.data.catalog.CatalogModel
import com.atonai.data.catalog.CatalogParser
import com.atonai.data.catalog.CatalogProvider
import java.util.zip.GZIPInputStream

/**
 * Loads the provider/model catalog from assets (positional tuple format, see
 * [CatalogParser]). The catalog is shipped gzipped (`providers.json.gz`, ~85 KB
 * instead of ~470 KB) and inflated on first use.
 */
class ProviderRegistry(private val context: Context) {

    /** Parsed once on first use; the asset never changes at runtime. */
    val catalog: Catalog by lazy { load() }

    private fun load(): Catalog = runCatching {
        CatalogParser.parse(readCatalogText())
    }.getOrElse { Catalog() }

    /** Assets may carry the catalog either gzipped or as plain JSON. */
    private fun readCatalogText(): String {
        val gz = runCatching { context.assets.open("providers.json.gz") }.getOrNull()
        if (gz != null) {
            return GZIPInputStream(gz).bufferedReader().use { it.readText() }
        }
        return context.assets.open("providers.json").bufferedReader().use { it.readText() }
    }

    fun provider(id: String?): CatalogProvider? = catalog.provider(id)

    fun defaultProvider(): CatalogProvider? = catalog.defaultProvider()

    fun defaultModelId(providerId: String): String {
        val p = provider(providerId) ?: defaultProvider()
        val models = p?.models.orEmpty()
        return catalog.defaultModelId?.takeIf { id -> models.any { it.id == id } }
            ?: models.firstOrNull()?.id
            ?: ""
    }

    fun model(providerId: String, modelId: String?): CatalogModel? =
        provider(providerId)?.model(modelId)

    fun systemPrompt(): String = systemPrompt("general")

    /**
     * The base system prompt for an agent (M10). `prompts/<agent>.txt` wins;
     * when an agent has no file we fall back to `prompts/system.txt`. Any
     * missing asset degrades to [DEFAULT_PROMPT] rather than failing a turn.
     */
    fun systemPrompt(agent: String): String = runCatching {
        val asset = when {
            agent == "general" -> "prompts/general.txt"
            else -> "prompts/$agent.txt"
        }
        context.assets.open(asset).bufferedReader().use { it.readText() }
    }.recoverCatching {
        context.assets.open("prompts/system.txt").bufferedReader().use { it.readText() }
    }.getOrDefault(DEFAULT_PROMPT)

    companion object {
        private const val DEFAULT_PROMPT =
            "You are AtonAI, a helpful coding agent for Android."
    }
}
