package com.atonai.engine

import com.atonai.data.catalog.CatalogModel
import com.atonai.engine.llm.Usage

/**
 * Turns token usage into a dollar amount using a model's published prices
 * (USD per 1M tokens). Cache reads/writes are priced separately when the model
 * provides a rate; otherwise they fall back to the input rate.
 */
object Cost {

    fun of(model: CatalogModel?, usage: Usage): Double {
        if (model == null) return 0.0
        val inRate = model.costIn
        val outRate = model.costOut
        val cacheReadRate = if (model.costCacheRead > 0) model.costCacheRead else inRate
        val cacheWriteRate = if (model.costCacheWrite > 0) model.costCacheWrite else inRate

        // Prompt tokens that were served from cache are reported inside
        // inTokens by some providers; keep the arithmetic conservative and
        // subtract cacheRead so it is not double-counted.
        val freshIn = (usage.inTokens - usage.cacheRead).coerceAtLeast(0)

        return (
            freshIn * inRate +
                usage.cacheRead * cacheReadRate +
                usage.cacheWrite * cacheWriteRate +
                usage.outTokens * outRate
            ) / 1_000_000.0
    }

    /** Running total for a session given the next step's usage. */
    fun add(model: CatalogModel?, session: Session, usage: Usage): Double {
        session.inTokens += usage.inTokens
        session.outTokens += usage.outTokens
        session.cacheRead += usage.cacheRead
        session.cacheWrite += usage.cacheWrite
        val delta = of(model, usage)
        session.costUsd += delta
        return delta
    }
}
