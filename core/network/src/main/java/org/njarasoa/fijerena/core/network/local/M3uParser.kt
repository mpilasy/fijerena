package org.njarasoa.fijerena.core.network.local

import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

data class M3uEntry(
    val name: String,
    val groupTitle: String,
    val logo: String?,
    val tvgId: String?,
    val url: String,
    val isLive: Boolean
)

object M3uParser {

    fun parse(content: String): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        val lines = content.lines()

        if (lines.isEmpty() || !lines[0].trim().startsWith("#EXTM3U")) {
            return emptyList()
        }

        var i = 1
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:")) {
                val infoLine = line.removePrefix("#EXTINF:")
                val name = extractName(infoLine)
                val groupTitle = extractAttribute(infoLine, "group-title") ?: "Uncategorized"
                val logo = extractAttribute(infoLine, "tvg-logo")
                val tvgId = extractAttribute(infoLine, "tvg-id")

                // Next non-empty, non-comment line should be the URL
                i++
                while (i < lines.size && (lines[i].isBlank() || lines[i].trim().startsWith("#"))) {
                    i++
                }
                if (i < lines.size) {
                    val url = lines[i].trim()
                    if (url.isNotBlank()) {
                        val isLive = isLiveUrl(url)
                        entries.add(M3uEntry(name, groupTitle, logo, tvgId, url, isLive))
                    }
                }
            }
            i++
        }
        return entries
    }

    fun entriesToCategories(entries: List<M3uEntry>, idPrefix: String = "local"): List<MediaCategory> {
        return entries
            .map { it.groupTitle }
            .distinct()
            .mapIndexed { index, group ->
                MediaCategory(
                    id = "${idPrefix}_cat_$index",
                    name = group
                )
            }
    }

    fun entriesToItems(entries: List<M3uEntry>, categories: List<MediaCategory>, idPrefix: String = "local"): List<MediaItem> {
        val categoryMap = categories.associateBy { it.name }
        return entries.mapIndexed { index, entry ->
            val category = categoryMap[entry.groupTitle]
            MediaItem(
                id = "${idPrefix}_m3u_$index",
                name = entry.name,
                mediaType = if (entry.isLive) MediaType.LIVE_CHANNEL else MediaType.VIDEO_FILE,
                categoryId = category?.id ?: "local_cat_0",
                thumbnailUrl = entry.logo,
                streamUri = entry.url,
                providerData = buildMap {
                    entry.tvgId?.let { put("epgChannelId", it) }
                }
            )
        }
    }

    private fun extractName(infoLine: String): String {
        val commaIndex = infoLine.lastIndexOf(',')
        return if (commaIndex >= 0) infoLine.substring(commaIndex + 1).trim() else "Unknown"
    }

    private fun extractAttribute(infoLine: String, attribute: String): String? {
        val regex = Regex("""$attribute="([^"]*)"""")
        return regex.find(infoLine)?.groupValues?.getOrNull(1)
    }

    private fun isLiveUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".m3u8") || lower.endsWith(".ts") ||
            lower.contains("/live/") || lower.contains(":8080/") ||
            lower.contains(":25461/")
    }
}
