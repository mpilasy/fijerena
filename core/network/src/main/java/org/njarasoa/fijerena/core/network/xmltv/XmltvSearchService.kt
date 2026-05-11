package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSearchResultRow
import java.util.Locale

/**
 * Searches programme titles in the SQLite FTS index.
 * All I/O is local — no network calls, no XML files on disk.
 */
class XmltvSearchService(
    private val context: Context,
) {
    private val rebuildDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val rebuildScope = CoroutineScope(SupervisorJob() + rebuildDispatcher)

    companion object {
        private const val TAG = "XmltvSearchService"
        private const val FTS_TIMEOUT_MS = 10_000L

        // Pre-compiled regex — avoid recompiling on every search call
        private val WHITESPACE_REGEX = Regex("\\s+")
    }

    /**
     * Trigger a background FTS rebuild after detecting corruption.
     * Safe to call multiple times — the rebuild mutex in EpgIndexer serializes.
     */
    private fun triggerBackgroundFtsRebuild() {
        val indexer = EpgIndexer.getInstance(context)
        rebuildScope.launch {
            try {
                Log.i(TAG, "Starting background FTS rebuild...")
                indexer.rebuildFtsAndUpdateState()
                indexer.markFtsClean()
                Log.i(TAG, "Background FTS rebuild completed")
            } catch (e: Exception) {
                Log.e(TAG, "Background FTS rebuild failed: ${e.message}", e)
            }
        }
    }

    /**
     * Search channels by name and return the next 6 hours of programmes
     * on all matching channels.
     *
     * @param query Case-insensitive substring to match against channel display names
     * @return [XmltvSearchResult] or null if no index is available.
     */
    suspend fun searchByChannel(query: String): XmltvSearchResult? {
        val indexer = EpgIndexer.getInstance(context)
        if (indexer.state.value !is EpgIndexState.Indexed) {
            return null
        }

        val now = System.currentTimeMillis() / 1000L
        val twoHoursLater = now + 2 * 3600L

        return try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

            val providerRepo = ProviderRepository(context)
            val activeProviderId = providerRepo.getActiveProvider()?.id ?: -1L
            val settingsDb = SettingsDatabase.getInstance(context)
            val sourceDao = settingsDb.epgSourceDao()
            val validSources = sourceDao.getEnabledSourcesForSearch(if (activeProviderId != -1L) activeProviderId else null)
            val sourceIds = validSources.map { it.id }
            if (sourceIds.isEmpty()) return rowsToSearchResult(emptyList(), searchedFromIndex = true)

            val queryLower = query.lowercase(Locale.ROOT)
            val matchedChannels = dao.searchChannelsByName(queryLower, sourceIds)
            if (matchedChannels.isEmpty()) {
                return rowsToSearchResult(emptyList(), searchedFromIndex = true)
            }

            val channelIds = matchedChannels.map { it.xmltvId }
            val rows = dao.getProgrammesForChannels(channelIds, now, twoHoursLater)
            rowsToSearchResult(rows, searchedFromIndex = true)
        } catch (e: Exception) {
            Log.w(TAG, "Channel search failed", e)
            null
        }
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

    private suspend fun searchFromIndex(
        query: String,
        windowStart: Long,
        windowEnd: Long,
    ): XmltvSearchResult? {
        val db = EpgIndexDatabase.getInstance(context)
        val dao = db.epgIndexDao()
        val indexer = EpgIndexer.getInstance(context)

        val providerRepo = ProviderRepository(context)
        val activeProviderId = providerRepo.getActiveProvider()?.id ?: -1L
        val settingsDb = SettingsDatabase.getInstance(context)
        val sourceDao = settingsDb.epgSourceDao()
        val validSources = sourceDao.getEnabledSourcesForSearch(if (activeProviderId != -1L) activeProviderId else null)
        val sourceIds = validSources.map { it.id }
        if (sourceIds.isEmpty()) return rowsToSearchResult(emptyList(), searchedFromIndex = true)

        var ftsReturnedEmpty = false

        // Skip FTS entirely when the index is being rebuilt in the background.
        if (!indexer.isFtsStale()) {
            // 1. Try FTS phrase match ("word1 word2"*) with timeout
            val ftsQuery = buildFtsQuery(query)
            val rows =
                try {
                    withTimeoutOrNull(FTS_TIMEOUT_MS) {
                        dao.searchByTitleFts(ftsQuery, sourceIds, windowStart, windowEnd)
                    }
                } catch (e: Exception) {
                    null
                }

            if (rows == null) {
                Log.w(TAG, "FTS query timed out after $FTS_TIMEOUT_MS ms for query: $query")
                // Don't trigger rebuild on timeout — it makes things slower
            } else if (rows.isNotEmpty()) {
                return rowsToSearchResult(rows, searchedFromIndex = true)
            } else {
                ftsReturnedEmpty = true
                // 2. Try FTS AND match (word1* word2*)
                val andFtsQuery = buildFtsAndQuery(query)
                if (andFtsQuery != null) {
                    val andRows =
                        try {
                            withTimeoutOrNull(FTS_TIMEOUT_MS) {
                                dao.searchByTitleFts(andFtsQuery, sourceIds, windowStart, windowEnd)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    if (andRows == null) {
                        Log.w(TAG, "FTS AND query timed out after $FTS_TIMEOUT_MS ms for query: $query")
                    } else if (andRows.isNotEmpty()) {
                        return rowsToSearchResult(andRows, searchedFromIndex = true)
                    }
                }
            }
        } else {
            // FTS index is stale — skip FTS, use LIKE
        }

        // 3. Fall back to LIKE with full query
        val queryLower = query.lowercase(Locale.ROOT)
        val likeRows = dao.searchByTitleLike(queryLower, sourceIds, windowStart, windowEnd)
        if (likeRows.isNotEmpty()) {
            // If FTS returned 0 but LIKE found results, the FTS index is out of sync
            if (ftsReturnedEmpty && !indexer.isFtsStale()) {
                Log.w(TAG, "FTS returned 0 rows but LIKE found ${likeRows.size} — FTS index corrupted, triggering rebuild")
                indexer.markFtsStale()
                triggerBackgroundFtsRebuild()
            }
            return rowsToSearchResult(likeRows, searchedFromIndex = true)
        }

        // 4. Fall back to LIKE AND: search by shortest word, then filter for all words in memory
        val words = queryLower.split(WHITESPACE_REGEX).filter { it.length >= 2 }
        if (words.size >= 2) {
            val shortestWord = words.minBy { it.length }
            val broadRows = dao.searchByTitleLike(shortestWord, sourceIds, windowStart, windowEnd)
            val filtered =
                broadRows.filter { row ->
                    // Performance optimization: Avoid allocating a new lowercase String for every item
                    // by using contains(..., ignoreCase = true) which utilizes zero-allocation string comparison
                    words.all { word -> row.title.contains(word, ignoreCase = true) }
                }
            return rowsToSearchResult(filtered, searchedFromIndex = true)
        }

        return rowsToSearchResult(emptyList(), searchedFromIndex = true)
    }

    private fun sanitizeQuery(query: String): String =
        query
            .replace("\"", "")
            .replace("*", "")
            .replace("(", "")
            .replace(")", "")
            .replace(":", "")
            .trim()

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
        searchedFromIndex: Boolean,
    ): XmltvSearchResult {
        val channels = mutableMapOf<String, XmltvChannel>()
        val programmes = mutableListOf<XmltvProgramme>()

        for (row in rows) {
            if (row.channelId !in channels) {
                channels[row.channelId] =
                    XmltvChannel(
                        id = row.channelId,
                        displayName = row.channelDisplayName,
                        iconUrl = row.channelIconUrl,
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
                    sourceId = row.sourceId,
                ),
            )
        }

        return XmltvSearchResult(
            channels = channels,
            programmes = programmes,
            totalScanned = rows.size,
            truncated = rows.size >= 500,
            searchedFromIndex = searchedFromIndex,
        )
    }
}
