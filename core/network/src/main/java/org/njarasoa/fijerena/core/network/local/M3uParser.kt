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
    private const val EXTINF_PREFIX = "#EXTINF:"
    private const val EXTM3U_HEADER = "#EXTM3U"

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
        // Use trimStart to avoid full string allocation
        if (!firstLine.trimStart().startsWith(EXTM3U_HEADER)) {
            return@sequence
        }

        var pendingEntry: PendingEntry? = null

        while (iterator.hasNext()) {
            val line = iterator.next()

            // Skip empty lines efficiently
            if (line.isEmpty()) continue

            // Check using startsWith on the original line (assuming standard formatting)
            // or trimStart only if needed. Most M3U files don't have leading spaces, but we should be safe.
            // Using trimStart() creates a new string only if there is whitespace.
            val trimmedLine = if (line.isNotEmpty() && line[0].isWhitespace()) line.trimStart() else line
            if (trimmedLine.isEmpty()) continue

            if (trimmedLine.startsWith(EXTINF_PREFIX)) {
                // Pass the original string and offset to avoid removing prefix allocation
                val offset = EXTINF_PREFIX.length

                // Extract directly from the line with offset
                val name = extractName(trimmedLine, offset)
                val groupTitle = extractAttribute(trimmedLine, GROUP_TITLE_PREFIX, offset) ?: "Uncategorized"
                val logo = extractAttribute(trimmedLine, LOGO_PREFIX, offset)
                val tvgId = extractAttribute(trimmedLine, ID_PREFIX, offset)

                pendingEntry = PendingEntry(name, groupTitle, logo, tvgId)
            } else if (!trimmedLine.startsWith("#")) {
                if (pendingEntry != null) {
                    // Ensure URL is clean by trimming the end (handles trailing spaces/newlines)
                    val url = trimmedLine.trimEnd()

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

    private fun extractName(line: String, startIndex: Int): String {
        val commaIndex = line.lastIndexOf(',')
        // Ensure comma is after the metadata start
        return if (commaIndex >= startIndex) {
             line.substring(commaIndex + 1).trim()
        } else {
            "Unknown"
        }
    }

    private fun extractAttribute(line: String, prefix: String, startIndex: Int): String? {
        val foundIndex = line.indexOf(prefix, startIndex)
        if (foundIndex == -1) return null

        val valueStartIndex = foundIndex + prefix.length
        val valueEndIndex = line.indexOf('"', valueStartIndex)
        if (valueEndIndex == -1) return null

        return line.substring(valueStartIndex, valueEndIndex)
    }

    private fun isLiveUrl(url: String): Boolean {
        // Performance note: In benchmarks, creating a lowercase copy was found to be faster
        // than multiple case-insensitive scans (endsWith/contains with ignoreCase=true).
        // This is likely because the allocation cost is amortized compared to multiple
        // character-by-character comparisons.
        val lower = url.lowercase()
        return lower.endsWith(".m3u8") || lower.endsWith(".ts") ||
            lower.contains("/live/") || lower.contains(":8080/") ||
            lower.contains(":25461/")
    }
}
