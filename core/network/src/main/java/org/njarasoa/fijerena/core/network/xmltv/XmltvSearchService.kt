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
        val state = indexer.state.value
        if (state is EpgIndexState.NotIndexed) {
            return null
        }

        val now = System.currentTimeMillis() / 1000L
        val twoHoursLater = now + 2 * 3600L

        return try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

            val providerRepo = ProviderRepository(context)
            // EPG is provider-scoped: with no active provider there is nothing to search.
            val activeProviderId =
                providerRepo.getActiveProvider()?.id
                    ?: return rowsToSearchResult(emptyList(), searchedFromIndex = true)
            val settingsDb = SettingsDatabase.getInstance(context)
            val sourceDao = settingsDb.epgSourceDao()
            val validSources = sourceDao.getEnabledSourcesForSearch(activeProviderId)
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
        val state = indexer.state.value
        if (state is EpgIndexState.NotIndexed) {
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
        // EPG is provider-scoped: with no active provider there are no sources to search.
        val activeProviderId = providerRepo.getActiveProvider()?.id
        val settingsDb = SettingsDatabase.getInstance(context)
        val sourceDao = settingsDb.epgSourceDao()
        val validSources =
            activeProviderId?.let { sourceDao.getEnabledSourcesForSearch(it) } ?: emptyList()
        val sourceIds = validSources.map { it.id }

        var result: XmltvSearchResult? = null

        if (sourceIds.isEmpty()) {
            result = rowsToSearchResult(emptyList(), searchedFromIndex = true, searchPath = EpgSearchPath.NONE)
        } else if (indexer.isFtsStale()) {
            throw IllegalStateException("Index optimizing, please wait...")
        } else {
            // 1. Try Raw FTS Query (Supports OR, NEAR, etc.)
            val rawFtsQuery = buildRawFtsQuery(query)
            val rawRows = try {
                withTimeoutOrNull(FTS_TIMEOUT_MS) {
                    dao.searchByTitleFts(rawFtsQuery, sourceIds, windowStart, windowEnd)
                }
            } catch (e: Exception) {
                // Catches SQLite syntax errors if user provided malformed FTS tokens
                null
            }

            if (rawRows != null && rawRows.isNotEmpty()) {
                result = rowsToSearchResult(rawRows, searchedFromIndex = true, searchPath = EpgSearchPath.FTS_PHRASE)
            } else {
                // 2. Fallback to Safe Phrase/AND matching if raw FTS returned nothing or failed
                val safeFtsQuery = buildSafeFtsQuery(query)
                val safeRows = try {
                    withTimeoutOrNull(FTS_TIMEOUT_MS) {
                        dao.searchByTitleFts(safeFtsQuery, sourceIds, windowStart, windowEnd)
                    }
                } catch (e: Exception) {
                    null
                }

                if (safeRows != null && safeRows.isNotEmpty()) {
                    result = rowsToSearchResult(safeRows, searchedFromIndex = true, searchPath = EpgSearchPath.FTS_AND)
                } else {
                    result = rowsToSearchResult(emptyList(), searchedFromIndex = true, searchPath = EpgSearchPath.NONE)
                }
            }
        }

        return result
    }

    /**
     * Builds a query that preserves FTS operators like OR, NEAR, and NOT.
     * Appends a prefix wildcard * to the last word if it's not a reserved token.
     */
    private fun buildRawFtsQuery(query: String): String {
        val trimmed = query.trim()
        val finalQuery = if (trimmed.contains(Regex("[A-Z]+")) || trimmed.contains("\"")) {
            // User likely provided manual FTS syntax
            trimmed
        } else {
            // Standard query: append wildcard to end for prefix matching
            "$trimmed*"
        }
        return finalQuery
    }

    /**
     * Builds a safe "fallback" query by stripping operators and wrapping in quotes.
     */
    private fun buildSafeFtsQuery(query: String): String {
        val sanitized = query
            .replace("\"", "")
            .replace("*", "")
            .replace("(", "")
            .replace(")", "")
            .replace(":", "")
            .trim()
        
        return if (sanitized.isBlank()) {
            "*"
        } else {
            "\"$sanitized\"*"
        }
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
        searchPath: EpgSearchPath = EpgSearchPath.NONE,
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
            searchPath = searchPath,
        )
    }
}
