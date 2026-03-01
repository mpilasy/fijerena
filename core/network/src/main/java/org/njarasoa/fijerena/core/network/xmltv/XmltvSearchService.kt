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
        // Pre-compiled regex — avoid recompiling on every search call
        private val WHITESPACE_REGEX = Regex("\\s+")
    }

    /**
     * Search programme titles in the local EPG index.
     *
     * @param query Case-insensitive substring to match
     * @return [XmltvSearchResult] or null if no index is available.
     */
    suspend fun search(query: String): XmltvSearchResult? {
        val indexer = EpgIndexer.getInstance(context)
        if (indexer.state.value !is EpgIndexState.Indexed) {
            return null
        }

        val now = System.currentTimeMillis() / 1000L
        val futureSixDays = now + 6 * 86400L

        return try {
            searchFromIndex(query, now, futureSixDays)
        } catch (e: Exception) {
            Log.w(TAG, "SQLite search failed", e)
            null
        }
    }

    suspend fun getNowPlaying(nowEpoch: Long): XmltvSearchResult? {
        val indexer = EpgIndexer.getInstance(context)
        if (indexer.state.value !is EpgIndexState.Indexed) {
            return null
        }

        return try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()
            val rows = dao.getNowPlaying(nowEpoch)
            rowsToSearchResult(rows, searchedFromIndex = true)
        } catch (e: Exception) {
            Log.w(TAG, "SQLite getNowPlaying failed", e)
            null
        }
    }

    private suspend fun searchFromIndex(
        query: String,
        windowStart: Long,
        windowEnd: Long
    ): XmltvSearchResult? {
        val db = EpgIndexDatabase.getInstance(context)
        val dao = db.epgIndexDao()

        // 1. Try FTS phrase match ("word1 word2"*)
        val rows: List<EpgSearchResultRow> = try {
            val ftsQuery = buildFtsQuery(query)
            dao.searchByTitleFts(ftsQuery, windowStart, windowEnd)
        } catch (e: Exception) {
            Log.d(TAG, "FTS phrase query failed ('$query'): ${e.message}")
            emptyList()
        }

        if (rows.isNotEmpty()) {
            return rowsToSearchResult(rows, searchedFromIndex = true)
        }

        // 2. Try FTS AND match (word1* word2* — each word independently, any order)
        val andFtsQuery = buildFtsAndQuery(query)
        if (andFtsQuery != null) {
            val andRows = try {
                dao.searchByTitleFts(andFtsQuery, windowStart, windowEnd)
            } catch (e: Exception) {
                Log.d(TAG, "FTS AND query failed ('$query'): ${e.message}")
                emptyList()
            }
            if (andRows.isNotEmpty()) {
                return rowsToSearchResult(andRows, searchedFromIndex = true)
            }
        }

        // 3. Fall back to LIKE with full query
        val queryLower = query.lowercase(Locale.ROOT)
        val likeRows = dao.searchByTitleLike(queryLower, windowStart, windowEnd)
        if (likeRows.isNotEmpty()) {
            return rowsToSearchResult(likeRows, searchedFromIndex = true)
        }

        // 4. Fall back to LIKE AND: search by shortest word, then filter for all words in memory
        val words = queryLower.split(WHITESPACE_REGEX).filter { it.length >= 2 }
        if (words.size >= 2) {
            val shortestWord = words.minBy { it.length }
            val broadRows = dao.searchByTitleLike(shortestWord, windowStart, windowEnd)
            val filtered = broadRows.filter { row ->
                val titleLower = row.title.lowercase(Locale.ROOT)
                words.all { word -> titleLower.contains(word) }
            }
            return rowsToSearchResult(filtered, searchedFromIndex = true)
        }

        return rowsToSearchResult(emptyList(), searchedFromIndex = true)
    }

    private fun sanitizeQuery(query: String): String {
        return query
            .replace("\"", "")
            .replace("*", "")
            .replace("(", "")
            .replace(")", "")
            .replace(":", "")
            .trim()
    }

    private fun buildFtsQuery(query: String): String {
        val sanitized = sanitizeQuery(query)
        if (sanitized.isBlank()) throw IllegalArgumentException("Empty query after sanitization")
        return "\"$sanitized\"*"
    }

    /**
     * Build an FTS AND query: each word becomes a prefix token, implicit AND.
     * "sports news" → "sports* news*"
     * Returns null if query has fewer than 2 words (AND not applicable).
     */
    private fun buildFtsAndQuery(query: String): String? {
        val sanitized = sanitizeQuery(query)
        val words = sanitized.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (words.size < 2) return null
        return words.joinToString(" ") { "$it*" }
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
                    category = row.category,
                    sourceId = row.sourceId
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
