package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.util.Log
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSearchResultRow
import java.util.Locale

/**
 * Searches the locally-cached XMLTV file by programme title.
 *
 * Dual-path: uses SQLite FTS index when available, falls back to XML scan.
 * All I/O is local — no network calls.
 */
class XmltvSearchService(private val context: Context) {

    companion object {
        private const val TAG = "XmltvSearchService"
    }

    /**
     * Search programme titles in the local EPG file.
     *
     * @param query Case-insensitive substring to match
     * @return [XmltvSearchResult] or null if no local EPG file is available.
     *         [XmltvSearchResult.searchedFromIndex] indicates whether the SQLite path was used.
     */
    fun search(query: String): XmltvSearchResult? {
        val file = EpgFileManager.getInstance(context).getEpgFile()
            ?: fallbackFile()
            ?: return null
        val now = System.currentTimeMillis() / 1000L
        val pastOneDay = now - 86400L
        val futureSixDays = now + 6 * 86400L

        // Try SQLite index first
        val indexer = EpgIndexer.getInstance(context)
        if (indexer.state.value is EpgIndexState.Indexed) {
            try {
                val result = searchFromIndex(query, pastOneDay, futureSixDays)
                if (result != null) return result
            } catch (e: Exception) {
                Log.w(TAG, "SQLite search failed, falling back to XML scan", e)
            }
        }

        // Fallback: XML scan
        val timeWindow = Pair(pastOneDay, futureSixDays)
        return file.inputStream().buffered().use { stream ->
            XmltvParser.searchByTitle(
                inputStream = stream,
                query = query,
                timeWindowSeconds = timeWindow
            )
        }
    }

    private fun searchFromIndex(
        query: String,
        windowStart: Long,
        windowEnd: Long
    ): XmltvSearchResult? {
        val db = EpgIndexDatabase.getInstance(context)
        val dao = db.epgIndexDao()

        // Try FTS first, fall back to LIKE for special characters
        val rows: List<EpgSearchResultRow> = try {
            val ftsQuery = buildFtsQuery(query)
            // Room DAO suspend functions — but we're already on IO dispatcher via ViewModel
            // Use runBlocking-free approach: call blocking query on Room's built-in executor
            kotlinx.coroutines.runBlocking {
                dao.searchByTitleFts(ftsQuery, windowStart, windowEnd)
            }
        } catch (e: Exception) {
            Log.d(TAG, "FTS query failed ('$query'), falling back to LIKE: ${e.message}")
            val queryLower = query.lowercase(Locale.ROOT)
            kotlinx.coroutines.runBlocking {
                dao.searchByTitleLike(queryLower, windowStart, windowEnd)
            }
        }

        if (rows.isEmpty()) {
            // Double-check with LIKE in case FTS missed due to tokenization
            val queryLower = query.lowercase(Locale.ROOT)
            val likeRows = kotlinx.coroutines.runBlocking {
                dao.searchByTitleLike(queryLower, windowStart, windowEnd)
            }
            return rowsToSearchResult(likeRows, searchedFromIndex = true)
        }

        return rowsToSearchResult(rows, searchedFromIndex = true)
    }

    private fun buildFtsQuery(query: String): String {
        // Escape FTS special characters and wrap for prefix matching
        val sanitized = query
            .replace("\"", "")
            .replace("*", "")
            .replace("(", "")
            .replace(")", "")
            .replace(":", "")
            .trim()
        if (sanitized.isBlank()) throw IllegalArgumentException("Empty query after sanitization")
        return "\"$sanitized\"*"
    }

    private fun rowsToSearchResult(
        rows: List<EpgSearchResultRow>,
        searchedFromIndex: Boolean
    ): XmltvSearchResult {
        val channels = mutableMapOf<String, XmltvChannel>()
        val programmes = mutableListOf<XmltvProgramme>()

        for (row in rows) {
            if (row.channelId !in channels) {
                channels[row.channelId] = XmltvChannel(
                    id = row.channelId,
                    displayName = row.channelDisplayName,
                    iconUrl = row.channelIconUrl
                )
            }
            programmes.add(
                XmltvProgramme(
                    channelId = row.channelId,
                    startEpoch = row.startEpoch,
                    endEpoch = row.endEpoch,
                    title = row.title,
                    description = row.description,
                    category = row.category
                )
            )
        }

        return XmltvSearchResult(
            channels = channels,
            programmes = programmes,
            totalScanned = rows.size,
            truncated = rows.size >= 500,
            searchedFromIndex = searchedFromIndex
        )
    }

    private fun fallbackFile(): java.io.File? {
        val f = java.io.File(context.cacheDir, "xmltv_global.xml")
        return if (f.exists() && f.length() > 0) f else null
    }
}
