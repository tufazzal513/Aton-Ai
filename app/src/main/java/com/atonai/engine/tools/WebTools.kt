package com.atonai.engine.tools

import com.atonai.data.perms.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a URL and strips it down to readable text (TOOLS_SPEC.md §8). Uses the
 * platform's own HTTP stack so no extra dependency is needed, and always asks
 * for the **network** permission because it leaves the device.
 */
class WebFetchTool : Tool {
    override val name = "webfetch"
    override val action = Action.NETWORK
    override val readOnly = true
    override val description =
        "Fetch a URL and return its readable text (HTML tags, scripts and styles " +
            "are stripped). Output is truncated."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") { put("type", "string") }
            putJsonObject("format") { put("type", "string"); put("description", "text | markdown") }
        }
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive("url"),
        )))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        val url = Args(args).str("url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult.error("Only http:// and https:// URLs are supported")
        }
        ctx.permit.permit(Action.NETWORK, host(url))

        return withContext(Dispatchers.IO) {
            runCatching { fetch(url) }
                .getOrElse { ToolResult.error("Fetch failed: ${it.message}") }
        }
    }

    private fun fetch(url: String): ToolResult {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "AtonAI/1.0")
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) return ToolResult.error("HTTP $code for $url")
            val type = conn.contentType.orEmpty()
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            if (type.contains("json") || type.startsWith("text/plain")) {
                return ToolResult.ok(truncate(raw))
            }
            return ToolResult.ok(truncate(htmlToText(raw)))
        } finally {
            conn.disconnect()
        }
    }

    /** Removes scripts/styles and tags, collapses whitespace. */
    private fun htmlToText(html: String): String {
        val noScripts = html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
        val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(noScripts)?.groupValues?.get(1)
        val text = noScripts
            .replace(Regex("(?is)<br\\s*/?>"), "\n")
            .replace(Regex("(?is)</p>"), "\n")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return if (title.isNullOrBlank()) text else "# $title\n\n$text"
    }

    private fun host(url: String): String = runCatching { URL(url).host }.getOrDefault(url)

    private fun truncate(text: String): String =
        if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS) + "\n...[truncated]"

    private companion object {
        const val MAX_CHARS = 16_000
    }
}

/**
 * Searches the web (TOOLS_SPEC.md §9). `ASSUMPTION`: there is no built-in
 * provider, so if no endpoint is configured the tool says so clearly instead of
 * failing the turn. A future settings screen can inject an endpoint.
 */
class WebSearchTool(
    private val endpoint: () -> String = { "" },
) : Tool {
    override val name = "websearch"
    override val action = Action.NETWORK
    override val readOnly = true
    override val description =
        "Search the web for current information. Requires a search backend to be " +
            "configured in settings; otherwise it reports that it is unavailable."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") { put("type", "string") }
        }
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive("query"),
        )))
    }

    override suspend fun run(args: JsonObject, ctx: ToolCtx): ToolResult {
        val query = Args(args).str("query")
        val base = endpoint()
        if (base.isBlank()) {
            return ToolResult.ok("Web search is not configured. Set a search endpoint in settings.")
        }
        ctx.permit.permit(Action.NETWORK, query)
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("$base${java.net.URLEncoder.encode(query, "UTF-8")}").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 20_000
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) ToolResult.error("Search backend returned HTTP $code")
                    else ToolResult.ok(conn.inputStream.bufferedReader().use { it.readText().take(16_000) })
                } finally {
                    conn.disconnect()
                }
            }.getOrElse { ToolResult.error("Search failed: ${it.message}") }
        }
    }
}
