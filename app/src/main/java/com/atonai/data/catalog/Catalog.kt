package com.atonai.data.catalog

import kotlinx.serialization.Serializable

/**
 * One model in the catalog (04_DATA_MODELS.md §8). The asset stores models as a
 * 9-element positional array to stay compact; [CatalogParser] lifts each tuple
 * into this shape.
 */
@Serializable
data class CatalogModel(
    val id: String,
    val name: String,
    val context: Long,
    val reasoning: Int,          // 0/1 — supports reasoning effort
    val costIn: Double,
    val costOut: Double,
    val costCacheRead: Double,
    val costCacheWrite: Double,
    val maxOutput: Long,
) {
    val supportsReasoning: Boolean get() = reasoning != 0
}

/** One provider in the catalog. `kind` is the wire protocol ("openai"/"anthropic"). */
@Serializable
data class CatalogProvider(
    val id: String,
    val name: String,
    val kind: String,
    val api: String,
    val env: String = "",
    val models: List<CatalogModel> = emptyList(),
    val custom: Boolean = false,
) {
    fun model(id: String?): CatalogModel? = models.firstOrNull { it.id == id }
}

/** The whole catalog plus the default selection. */
@Serializable
data class Catalog(
    val providers: List<CatalogProvider> = emptyList(),
    val defaultProviderId: String? = null,
    val defaultModelId: String? = null,
) {
    fun provider(id: String?): CatalogProvider? = providers.firstOrNull { it.id == id }

    fun defaultProvider(): CatalogProvider? =
        provider(defaultProviderId) ?: providers.firstOrNull()

    fun defaultProviderName(): String = defaultProvider()?.name ?: "—"

    fun isEmpty(): Boolean = providers.isEmpty()
}
