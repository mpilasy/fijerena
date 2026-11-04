package org.njarasoa.fijerena.core.network.xmltv

import java.net.URI
import java.util.Locale

/** When two guide source URLs are the same guide (the one guide source per URL plan, OG-Q5). */
object GuideUrls {
    /**
     * The key two URLs of one guide share: trimmed, the scheme and host in lower case, no default
     * port (80 for http, 443 for https) and no `/` at the end of the path. The path and the query
     * stay as they are. A URL that cannot be read is its trimmed text.
     */
    fun matchKey(url: String): String {
        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return trimmed
        val host = uri.host?.lowercase(Locale.ROOT) ?: return trimmed
        val port =
            when {
                uri.port == -1 -> ""
                scheme == "http" && uri.port == 80 -> ""
                scheme == "https" && uri.port == 443 -> ""
                else -> ":${uri.port}"
            }
        val path = uri.rawPath.orEmpty().trimEnd('/')
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val userInfo = uri.rawUserInfo?.let { "$it@" }.orEmpty()
        return "$scheme://$userInfo$host$port$path$query"
    }
}
