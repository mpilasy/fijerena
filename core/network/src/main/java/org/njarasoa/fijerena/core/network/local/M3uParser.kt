package org.njarasoa.fijerena.core.network.local

import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import java.io.BufferedReader

data class M3uEntry(
    val name: String,
    val groupTitle: String,
    val logo: String?,
    val tvgId: String?,
    val url: String,
    val isLive: Boolean
)

object M3uParser {

    private const val GROUP_TITLE_PREFIX = "group-title=\""
    private const val LOGO_PREFIX = "tvg-logo=\""
    private const val ID_PREFIX = "tvg-id=\""

    private data class PendingEntry(
        val name: String,
        val groupTitle: String,
        val logo: String?,
        val tvgId: String?
    )

    fun parse(content: String): List<M3uEntry> {
        return parse(content.reader().buffered()).toList()
    }

    fun parse(reader: BufferedReader): Sequence<M3uEntry> = sequence {
        val iterator = reader.lineSequence().iterator()

        if (!iterator.hasNext()) return@sequence

        val firstLine = iterator.next()
        if (!firstLine.trim().startsWith("#EXTM3U")) {
            return@sequence
        }

        var pendingEntry: PendingEntry? = null

        while (iterator.hasNext()) {
            val line = iterator.next().trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:")) {
                val infoLine = line.removePrefix("#EXTINF:")
                val name = extractName(infoLine)
                val groupTitle = extractAttribute(infoLine, GROUP_TITLE_PREFIX) ?: "Uncategorized"
                val logo = extractAttribute(infoLine, LOGO_PREFIX)
                val tvgId = extractAttribute(infoLine, ID_PREFIX)

                pendingEntry = PendingEntry(name, groupTitle, logo, tvgId)
            } else if (!line.startsWith("#")) {
                if (pendingEntry != null) {
                    val url = line
                    if (url.isNotBlank()) {
                        val isLive = isLiveUrl(url)
                        yield(
                            M3uEntry(
                                pendingEntry.name,
                                pendingEntry.groupTitle,
                                pendingEntry.logo,
                                pendingEntry.tvgId,
                                url,
                                isLive
                            )
                        )
                    }
                    pendingEntry = null
                }
            }
        }
    }

    fun processEntries(reader: BufferedReader, idPrefix: String = "local"): Pair<List<MediaCategory>, List<MediaItem>> {
        val categories = mutableListOf<MediaCategory>()
        val categoryMap = mutableMapOf<String, MediaCategory>()
        val items = mutableListOf<MediaItem>()

        parse(reader).forEachIndexed { index, entry ->
            val category = categoryMap.getOrPut(entry.groupTitle) {
                val newCat = MediaCategory(
                    id = "${idPrefix}_cat_${categories.size}",
                    name = entry.groupTitle
                )
                categories.add(newCat)
                newCat
            }

            items.add(
                MediaItem(
                    id = "${idPrefix}_m3u_$index",
                    name = entry.name,
                    mediaType = if (entry.isLive) MediaType.LIVE_CHANNEL else MediaType.VIDEO_FILE,
                    categoryId = category.id,
                    thumbnailUrl = entry.logo,
                    streamUri = entry.url,
                    providerData = buildMap {
                        entry.tvgId?.let { put("epgChannelId", it) }
                    }
                )
            )
        }
        return categories to items
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

    private fun extractAttribute(infoLine: String, prefix: String): String? {
        val startIndex = infoLine.indexOf(prefix)
        if (startIndex == -1) return null

        val valueStartIndex = startIndex + prefix.length
        val valueEndIndex = infoLine.indexOf('"', valueStartIndex)
        if (valueEndIndex == -1) return null

        return infoLine.substring(valueStartIndex, valueEndIndex)
    }

    private fun isLiveUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".m3u8") || lower.endsWith(".ts") ||
            lower.contains("/live/") || lower.contains(":8080/") ||
            lower.contains(":25461/")
    }
}
