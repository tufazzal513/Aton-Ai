package com.atonai.core

/** Formats byte/token/duration counts for the UI without locale surprises. */
object Fmt {

    fun bytes(n: Long): String = when {
        n < 1024 -> "$n B"
        n < 1024 * 1024 -> "${round1(n / 1024.0)} KB"
        n < 1024L * 1024 * 1024 -> "${round1(n / (1024.0 * 1024))} MB"
        else -> "${round1(n / (1024.0 * 1024 * 1024))} GB"
    }

    fun tokens(n: Long): String = when {
        n < 1_000 -> "$n"
        n < 1_000_000 -> "${round1(n / 1_000.0)}k"
        else -> "${round1(n / 1_000_000.0)}M"
    }

    /** US dollars, always 2–4 significant decimals so tiny costs stay visible. */
    fun usd(amount: Double): String = when {
        amount <= 0.0 -> "$0.00"
        amount < 0.01 -> "$%.4f".format(amount)
        else -> "$%.2f".format(amount)
    }

    fun duration(ms: Long): String = when {
        ms < 1_000 -> "${ms}ms"
        ms < 60_000 -> "${round1(ms / 1_000.0)}s"
        else -> "${ms / 60_000}m ${(ms % 60_000) / 1_000}s"
    }

    private fun round1(v: Double): String = "%.1f".format(v)
}

/** Masks a secret for logs: `sk-abc…` style, never the full key. */
fun maskKey(key: String?): String {
    if (key.isNullOrBlank()) return "(none)"
    if (key.length <= 8) return "…"
    return key.take(4) + "…" + key.takeLast(2)
}
