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
    val isLive: Boolean,
)

object M3uParser {
    private const val GROUP_TITLE_PREFIX = "group-title=\""
    private const val LOGO_PREFIX = "tvg-logo=\""
    private const val ID_PREFIX = "tvg-id=\""
    private const val EXTINF_PREFIX = "#EXTINF:"
    private const val BYTE_ORDER_MARK = '\uFEFF'
    private const val NO_GROUP = "Uncategorized"

    /** A URL path that ends in one of these is a live stream; the query after it does not count. */
    private val LIVE_PATH_ENDS = listOf(".m3u8", ".ts", ".php")

    private data class PendingEntry(
        val name: String,
        val groupTitle: String,
        val logo: String?,
        val tvgId: String?,
    )

    /**
     * The entries of an extended M3U playlist. The `#EXTM3U` header is optional (some playlists
     * leave it out), and a byte-order mark or spaces before a line are not part of it.
     */
    fun parse(reader: BufferedReader): Sequence<M3uEntry> =
        sequence {
            var pendingEntry: PendingEntry? = null

            for (line in reader.lineSequence()) {
                // trimStart() makes a new string only when the line starts with a space or a mark.
                val first = line.firstOrNull()
                val trimmedLine =
                    if (first != null && (first.isWhitespace() || first == BYTE_ORDER_MARK)) {
                        line.trimStart { it.isWhitespace() || it == BYTE_ORDER_MARK }
                    } else {
                        line
                    }

                if (trimmedLine.startsWith(EXTINF_PREFIX)) {
                    // The original string and an offset: no removePrefix allocation.
                    val offset = EXTINF_PREFIX.length

                    val name = extractName(trimmedLine, offset)
                    val groupTitle = extractAttribute(trimmedLine, GROUP_TITLE_PREFIX, offset)?.ifBlank { null } ?: NO_GROUP
                    val logo = extractAttribute(trimmedLine, LOGO_PREFIX, offset)
                    val tvgId = extractAttribute(trimmedLine, ID_PREFIX, offset)

                    pendingEntry = PendingEntry(name, groupTitle, logo, tvgId)
                } else if (pendingEntry != null && trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                    // Ensure URL is clean by trimming the end (handles trailing spaces/newlines)
                    val url = trimmedLine.trimEnd()

                    if (url.isNotBlank()) {
                        yield(
                            M3uEntry(
                                pendingEntry.name,
                                pendingEntry.groupTitle,
                                pendingEntry.logo,
                                pendingEntry.tvgId,
                                url,
                                isLiveUrl(url),
                            ),
                        )
                    }
                    pendingEntry = null
                }
            }
        }

    fun processEntries(
        reader: BufferedReader,
        idPrefix: String = "local",
    ): Pair<List<MediaCategory>, List<MediaItem>> {
        val categories = mutableListOf<MediaCategory>()
        val categoryMap = mutableMapOf<String, MediaCategory>()
        val items = mutableListOf<MediaItem>()

        parse(reader).forEach { entry ->
            val category =
                categoryMap.getOrPut(entry.groupTitle) {
                    val newCat =
                        MediaCategory(
                            // Derived from the group name, not insertion order, so watch state
                            // and favorites (keyed on this id) survive the playlist being
                            // reordered or gaining/losing entries.
                            id = "${idPrefix}_cat_${entry.groupTitle.hashCode()}",
                            name = entry.groupTitle,
                        )
                    categories.add(newCat)
                    newCat
                }

            items.add(
                MediaItem(
                    id = "${idPrefix}_m3u_${entry.url.hashCode()}",
                    name = entry.name,
                    mediaType = if (entry.isLive) MediaType.LIVE_CHANNEL else MediaType.VIDEO_FILE,
                    categoryId = category.id,
                    thumbnailUrl = entry.logo,
                    streamUri = entry.url,
                    providerData =
                        buildMap {
                            entry.tvgId?.let { put("epgChannelId", it) }
                        },
                ),
            )
        }
        return categories to items
    }

    fun entriesToCategories(
        entries: List<M3uEntry>,
        idPrefix: String = "local",
    ): List<MediaCategory> =
        entries
            .map { it.groupTitle }
            .distinct()
            .map { group ->
                MediaCategory(
                    id = "${idPrefix}_cat_${group.hashCode()}",
                    name = group,
                )
            }

    fun entriesToItems(
        entries: List<M3uEntry>,
        categories: List<MediaCategory>,
        idPrefix: String = "local",
    ): List<MediaItem> {
        val categoryMap = categories.associateBy { it.name }
        return entries.map { entry ->
            val category = categoryMap[entry.groupTitle]
            MediaItem(
                id = "${idPrefix}_m3u_${entry.url.hashCode()}",
                name = entry.name,
                mediaType = if (entry.isLive) MediaType.LIVE_CHANNEL else MediaType.VIDEO_FILE,
                categoryId = category?.id ?: "local_cat_0",
                thumbnailUrl = entry.logo,
                streamUri = entry.url,
                providerData =
                    buildMap {
                        entry.tvgId?.let { put("epgChannelId", it) }
                    },
            )
        }
    }

    /**
     * The name after the first comma outside quotes: a quoted attribute can hold a comma
     * (`group-title="News, Weather"`), and so can the name. With unbalanced quotes, the last comma.
     */
    private fun extractName(
        line: String,
        startIndex: Int,
    ): String {
        var inQuotes = false
        var commaIndex = -1
        var index = startIndex
        while (commaIndex == -1 && index < line.length) {
            val char = line[index]
            if (char == '"') {
                inQuotes = !inQuotes
            } else if (char == ',' && !inQuotes) {
                commaIndex = index
            }
            index++
        }
        val nameComma = if (commaIndex == -1) line.lastIndexOf(',') else commaIndex
        // Ensure comma is after the metadata start
        return if (nameComma >= startIndex) {
            line.substring(nameComma + 1).trim()
        } else {
            "Unknown"
        }
    }

    private fun extractAttribute(
        line: String,
        prefix: String,
        startIndex: Int,
    ): String? {
        val foundIndex = line.indexOf(prefix, startIndex)
        if (foundIndex == -1) return null

        val valueStartIndex = foundIndex + prefix.length
        val valueEndIndex = line.indexOf('"', valueStartIndex)
        if (valueEndIndex == -1) return null

        return line.substring(valueStartIndex, valueEndIndex)
    }

    /**
     * Live when the URL's path ends in a stream or script extension (`.m3u8`, `.ts`, `.php`), or the
     * URL has `/live/` or an Xtream port. The query and fragment do not count: `….m3u8?token=…` and
     * `…/play.php?id=…` are live.
     */
    private fun isLiveUrl(url: String): Boolean {
        // One lowercase copy measured faster than several case-insensitive scans
        // (endsWith/contains with ignoreCase = true).
        val lower = url.lowercase()
        val pathEnd = lower.indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 } ?: lower.length
        return LIVE_PATH_ENDS.any { lower.startsWith(it, pathEnd - it.length) } ||
            lower.contains("/live/") ||
            lower.contains(":8080/") ||
            lower.contains(":25461/")
    }
}
