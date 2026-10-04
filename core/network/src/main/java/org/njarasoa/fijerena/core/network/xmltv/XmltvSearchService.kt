package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSearchResultRow
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import java.util.Locale

/**
 * Thrown when the FTS index is marked stale (mid-ingest or mid-rebuild) and the LIKE fallback
 * couldn't answer either (timed out or failed). Distinct from a genuine "no results" so callers
 * can tell the user to wait instead of silently reporting nothing found.
 */
class EpgIndexBusyException : Exception("EPG index optimizing, please wait...")

/**
 * Searches programme titles in the SQLite FTS index.
 * All I/O is local — no network calls, no XML files on disk.
 */
class XmltvSearchService(
    private val context: Context,
) {
    companion object {
        private const val TAG = "XmltvSearchService"
        private const val FTS_TIMEOUT_MS = 10_000L

        // Pre-compiled regex — avoid recompiling on every search call
        private val WHITESPACE_REGEX = Regex("\\s+")

        // FTS4's actual reserved boolean operators, as whole words — not "any capital letter",
        // which matched an ordinary capitalized query (a channel name like "CNN" or "HBO", or
        // just "Movie") and mistook it for hand-written FTS syntax, silently dropping the prefix
        // wildcard below and returning only exact-term matches.
        private val FTS_OPERATOR_REGEX = Regex("\\b(AND|OR|NOT|NEAR)\\b")
    }

    /**
     * Search programme titles in the local EPG index: every programme that hasn't ended yet, with
     * no upper limit — however far ahead the guide goes.
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

        return try {
            searchFromIndex(query, now, Long.MAX_VALUE)
        } catch (e: EpgIndexBusyException) {
            throw e
        } catch (e: CancellationException) {
            throw e
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
            activeProviderId?.let { sourceDao.getEnabledSourcesForProvider(it) } ?: emptyList()
        val sourceIds = validSources.map { it.id }

        var result: XmltvSearchResult? = null

        if (sourceIds.isEmpty()) {
            result = rowsToSearchResult(emptyList(), searchedFromIndex = true, searchPath = EpgSearchPath.NONE)
        } else if (indexer.isFtsStale()) {
            // FTS doesn't match epg_programme right now; scan titles directly instead of refusing.
            val pattern = escapeLike(sanitizeQuery(query).lowercase(Locale.ROOT))
            if (pattern.isBlank()) {
                result = rowsToSearchResult(emptyList(), searchedFromIndex = true, searchPath = EpgSearchPath.NONE)
            } else {
                val likeRows =
                    try {
                        withTimeoutOrNull(FTS_TIMEOUT_MS) {
                            dao.searchByTitleLike(pattern, sourceIds, windowStart, windowEnd)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "LIKE fallback search failed", e)
                        null
                    }
                        ?: throw EpgIndexBusyException()
                result = rowsToSearchResult(likeRows, searchedFromIndex = true, searchPath = EpgSearchPath.LIKE_FALLBACK)
            }
        } else {
            // 1. Try Raw FTS Query (Supports OR, NEAR, etc.)
            val rawFtsQuery = buildRawFtsQuery(query)
            val rawRows =
                try {
                    withTimeoutOrNull(FTS_TIMEOUT_MS) {
                        dao.searchByTitleFts(rawFtsQuery, sourceIds, windowStart, windowEnd)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // A newer keystroke cancelled this search — not a query failure. Rethrowing
                    // (rather than falling through to null, which the fallback path below treats as
                    // "no results, try the safe query") stops a second, equally pointless FTS query
                    // from running against an already-cancelled search.
                    throw e
                } catch (e: Exception) {
                    // Catches SQLite syntax errors if user provided malformed FTS tokens
                    null
                }

            if (rawRows != null && rawRows.isNotEmpty()) {
                result = rowsToSearchResult(rawRows, searchedFromIndex = true, searchPath = EpgSearchPath.FTS_PHRASE)
            } else {
                // 2. Fallback to Safe Phrase/AND matching if raw FTS returned nothing or failed
                val safeFtsQuery = buildSafeFtsQuery(query)
                val safeRows =
                    try {
                        withTimeoutOrNull(FTS_TIMEOUT_MS) {
                            dao.searchByTitleFts(safeFtsQuery, sourceIds, windowStart, windowEnd)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
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
        val finalQuery =
            if (FTS_OPERATOR_REGEX.containsMatchIn(trimmed) || trimmed.contains("\"")) {
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
        val sanitized =
            query
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

    private fun escapeLike(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

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
