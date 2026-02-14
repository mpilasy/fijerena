package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.util.Log
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSearchResultRow
import java.util.Locale

/**
 * Searches programme titles in the SQLite FTS index.
 * All I/O is local — no network calls, no XML files on disk.
 */
class XmltvSearchService(private val context: Context) {

    companion object {
        private const val TAG = "XmltvSearchService"
    }

    /**
     * Search programme titles in the local EPG index.
     *
     * @param query Case-insensitive substring to match
     * @return [XmltvSearchResult] or null if no index is available.
     */
    fun search(query: String): XmltvSearchResult? {
        val indexer = EpgIndexer.getInstance(context)
        if (indexer.state.value !is EpgIndexState.Indexed) {
            return null
        }

        val now = System.currentTimeMillis() / 1000L
        val pastOneDay = now - 86400L
        val futureSixDays = now + 6 * 86400L

        return try {
            searchFromIndex(query, pastOneDay, futureSixDays)
        } catch (e: Exception) {
            Log.w(TAG, "SQLite search failed", e)
            null
        }
    }

    private fun searchFromIndex(
        query: String,
        windowStart: Long,
        windowEnd: Long
    ): XmltvSearchResult? {
        val db = EpgIndexDatabase.getInstance(context)
        val dao = db.epgIndexDao()

        val rows: List<EpgSearchResultRow> = try {
            val ftsQuery = buildFtsQuery(query)
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
            val queryLower = query.lowercase(Locale.ROOT)
            val likeRows = kotlinx.coroutines.runBlocking {
                dao.searchByTitleLike(queryLower, windowStart, windowEnd)
            }
            return rowsToSearchResult(likeRows, searchedFromIndex = true)
        }

        return rowsToSearchResult(rows, searchedFromIndex = true)
    }

    private fun buildFtsQuery(query: String): String {
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
}
