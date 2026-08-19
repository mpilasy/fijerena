package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSearchResultRow
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse

class XmltvEpgService(
    private val context: Context,
    private val providerId: Long,
) {
    companion object {
        private const val TAG = "XmltvEpgService"
        private const val PARSED_CACHE_TTL_MS = 12L * 60 * 60 * 1000
        // v2: entries cached before the per-source dedup fix can hold duplicate
        // programmes, which crashes the lazy lists keyed on listing id.
        private const val KEY_CACHED_EPG = "xmltv_epg_data_v2"
        private const val KEY_CACHE_TIMESTAMP = "xmltv_cache_timestamp_v2"
        private const val MISS_CACHE_MS = 60_000L
    }

    private val cache: SharedPreferences =
        context.getSharedPreferences(
            "xmltv_cache_$providerId",
            Context.MODE_PRIVATE,
        )

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // In-memory cache — avoid re-deserializing full EPG JSON from SharedPreferences on every call
    private var parsedEpgCache: Map<String, EpgResponse>? = null
    private var parsedEpgTimestamp: Long = 0L

    // Cached channel match maps — avoids full DB scan + 5 map builds on every getNowPlayingForItems call
    private var cachedChannelMaps: ChannelMatchMaps? = null

    // A miss is cached too, briefly. Only the success path used to write cachedChannelMaps, so
    // whenever the maps came out empty — no enabled source for this provider, or no indexed channel
    // belonging to one — every single call redid the query, and getNowPlayingForItems runs on every
    // channel-list render and channel switch. Bounded rather than permanent: the index can finish
    // building after a miss, and the clearCache() that would reset it is called on a different
    // XmltvEpgService instance than the one MediaRepository holds, so a permanent negative would
    // strand EPG until process death.
    private var missCachedUntilMs = 0L

    private data class ChannelMatchMaps(
        val byId: Map<String, String>,
        val byIdLower: Map<String, String>,
        val byName: Map<String, String>,
        val byNormalized: Map<String, String>,
        // Performance Optimization: Use parallel arrays instead of List<Pair<String, String>>
        // to avoid object boxing, allocation overhead, and iterator instantiation during hot loops.
        val normalizedNames: Array<String>,
        val normalizedIds: Array<String>,
    )

    private suspend fun buildChannelMatchMaps(): ChannelMatchMaps? {
        // Return cached maps if available — avoids full DB scan on every call
        cachedChannelMaps?.let { return it }
        if (System.currentTimeMillis() < missCachedUntilMs) return null

        // EPG is provider-scoped: without a real provider there is nothing to match against.
        if (providerId <= 0L) return cacheMiss()

        val settingsDb = SettingsDatabase.getInstance(context)
        val sourceDao = settingsDb.epgSourceDao()
        val validSources = sourceDao.getEnabledSourcesForSearch(providerId)
        val sourceIds = validSources.map { it.id }
        if (sourceIds.isEmpty()) return cacheMiss()

        val db = EpgIndexDatabase.getInstance(context)
        // Scoped in SQL rather than loading every indexed channel and filtering in memory: the
        // table holds every channel of every source, and this runs per channel-list render.
        val allXmltvChannels = db.epgIndexDao().getChannelsForSources(sourceIds)
        if (allXmltvChannels.isEmpty()) return cacheMiss()

        val byId = mutableMapOf<String, String>()
        val byIdLower = mutableMapOf<String, String>()
        val byName = mutableMapOf<String, String>()
        val byNormalized = mutableMapOf<String, String>()

        val normalizedNamesList = ArrayList<String>(allXmltvChannels.size)
        val normalizedIdsList = ArrayList<String>(allXmltvChannels.size)

        for (ch in allXmltvChannels) {
            byId[ch.xmltvId] = ch.xmltvId
            byIdLower[ch.xmltvId.lowercase()] = ch.xmltvId
            byName[ch.displayName] = ch.xmltvId
            val norm = normalizeName(ch.displayName)
            if (norm.isNotEmpty()) {
                byNormalized[norm] = ch.xmltvId
                // Filter out entries < 4 length here to avoid checking during the loop
                if (norm.length >= 4) {
                    normalizedNamesList.add(norm)
                    normalizedIdsList.add(ch.xmltvId)
                }
            }
        }

        return ChannelMatchMaps(
            byId = byId,
            byIdLower = byIdLower,
            byName = byName,
            byNormalized = byNormalized,
            normalizedNames = normalizedNamesList.toTypedArray(),
            normalizedIds = normalizedIdsList.toTypedArray()
        ).also {
            cachedChannelMaps = it
            missCachedUntilMs = 0L
        }
    }

    /** Suppress the lookup above for [MISS_CACHE_MS] before it is retried. Always returns null. */
    private fun cacheMiss(): ChannelMatchMaps? {
        missCachedUntilMs = System.currentTimeMillis() + MISS_CACHE_MS
        return null
    }

    private fun matchItems(
        items: List<MediaItem>,
        maps: ChannelMatchMaps,
    ): Map<String, String> {
        val matchedIds = mutableMapOf<String, String>()
        for (item in items) {
            val matched = matchChannel(
                item,
                maps.byId,
                maps.byIdLower,
                maps.byName,
                maps.byNormalized,
                maps.normalizedNames,
                maps.normalizedIds
            )
            if (matched != null) {
                matchedIds[item.id] = matched
            }
        }
        return matchedIds
    }

    /**
     * Get EPG data for channels from the SQLite index.
     * Returns empty map if no index is available.
     */
    suspend fun getEpgForChannels(channels: List<MediaItem>): Map<String, EpgResponse> =
        withContext(Dispatchers.IO) {
            val indexer = EpgIndexer.getInstance(context)
            if (indexer.state.value !is EpgIndexState.Indexed) {
                return@withContext emptyMap()
            }

            val cachedResult = getCachedEpg()
            if (cachedResult != null) {
                // Only use cache if ALL requested channels are present in it.
                // The player calls this with a single channel; a stale cache from a
                // previous single-channel call would otherwise hide the current one.
                val allPresent = channels.all { cachedResult.containsKey(it.id) }
                if (allPresent) {
                    return@withContext cachedResult
                }
            }

            try {
                val maps = buildChannelMatchMaps() ?: return@withContext emptyMap()
                val matchedIds = matchItems(channels, maps)

                if (matchedIds.isEmpty()) {
                    return@withContext emptyMap()
                }

                val dao = EpgIndexDatabase.getInstance(context).epgIndexDao()
                val nowSeconds = System.currentTimeMillis() / 1000
                val windowStart = nowSeconds - 24 * 3600
                val windowEnd = nowSeconds + 24 * 3600

                val uniqueXmltvIds = matchedIds.values.distinct()

                // ⚡ Bolt: Performance Optimization
                // Replaced chunked().flatMap() and .groupBy() with explicit loops to avoid
                // intermediate list and Map.Entry allocations.
                // A channel id can be indexed from several EPG sources, which yields the same
                // programme once per source. Keep one row per start time: listing ids are
                // built from channel id + start epoch and must stay unique.
                val programmesByChannel = mutableMapOf<String, LinkedHashMap<Long, EpgSearchResultRow>>()
                for (chunk in uniqueXmltvIds.chunked(500)) {
                    val rows = dao.getProgrammesForChannels(chunk, windowStart, windowEnd)
                    for (row in rows) {
                        val byStart = programmesByChannel.getOrPut(row.channelId) { LinkedHashMap() }
                        byStart.putIfAbsent(row.startEpoch, row)
                    }
                }

                val result = mutableMapOf<String, EpgResponse>()
                for ((itemId, xmltvId) in matchedIds) {
                    val progs = programmesByChannel[xmltvId]?.values ?: continue
                    if (progs.isEmpty()) continue
                    result[itemId] =
                        EpgResponse(
                            listings =
                                progs.map { row ->
                                    EpgProgram(
                                        id = "${row.channelId}_${row.startEpoch}",
                                        epgId = row.channelId,
                                        title = row.title,
                                        start = row.startEpoch.toString(),
                                        end = row.endEpoch.toString(),
                                        description = row.description,
                                        channelId = row.channelId,
                                    )
                                },
                        )
                }

                // Merge fresh results into existing cache so previous channels aren't lost
                val merged = (cachedResult ?: emptyMap()) + result
                cacheEpg(merged)
                merged
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query XMLTV EPG from index: ${e.message}", e)
                emptyMap()
            }
        }

    /**
     * Lightweight now-playing query: returns only the currently airing programme per item.
     * Uses the same 6-level channel matching but queries only current programmes.
     */
    suspend fun getNowPlayingForItems(items: List<MediaItem>): Map<String, EpgProgram> =
        withContext(Dispatchers.IO) {
            val indexer = EpgIndexer.getInstance(context)
            if (indexer.state.value !is EpgIndexState.Indexed) {
                return@withContext emptyMap()
            }

            try {
                val maps = buildChannelMatchMaps() ?: return@withContext emptyMap()
                val matchedIds = matchItems(items, maps)

                if (matchedIds.isEmpty()) return@withContext emptyMap()

                val dao = EpgIndexDatabase.getInstance(context).epgIndexDao()
                val nowEpoch = System.currentTimeMillis() / 1000

                val uniqueXmltvIds = matchedIds.values.distinct()

                // ⚡ Bolt: Performance Optimization
                // Replaced chunked().flatMap() and .associateBy() with explicit loops to avoid
                // intermediate list and Map.Entry allocations.
                val nowPlayingByChannel = mutableMapOf<String, EpgSearchResultRow>()
                for (chunk in uniqueXmltvIds.chunked(500)) {
                    val rows = dao.getNowPlayingForChannels(chunk, nowEpoch)
                    for (row in rows) {
                        nowPlayingByChannel[row.channelId] = row
                    }
                }

                val result = mutableMapOf<String, EpgProgram>()
                for ((itemId, xmltvId) in matchedIds) {
                    val row = nowPlayingByChannel[xmltvId] ?: continue
                    result[itemId] =
                        EpgProgram(
                            id = "${row.channelId}_${row.startEpoch}",
                            epgId = row.channelId,
                            title = row.title,
                            start = row.startEpoch.toString(),
                            end = row.endEpoch.toString(),
                            description = row.description,
                            channelId = row.channelId,
                        )
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query now-playing from index: ${e.message}", e)
                emptyMap()
            }
        }

    fun clearCache() {
        cache.edit { clear() }
        parsedEpgCache = null
        cachedChannelMaps = null
        missCachedUntilMs = 0L
    }

    private fun getCachedEpg(): Map<String, EpgResponse>? {
        val timestamp = cache.getLong(KEY_CACHE_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - timestamp > PARSED_CACHE_TTL_MS) {
            parsedEpgCache = null
            return null
        }

        // Return in-memory cache if still valid (avoids JSON deserialization)
        parsedEpgCache?.let { if (parsedEpgTimestamp == timestamp) return it }

        val cachedJson = cache.getString(KEY_CACHED_EPG, null) ?: return null
        return try {
            json.decodeFromString<Map<String, EpgResponse>>(cachedJson).also {
                parsedEpgCache = it
                parsedEpgTimestamp = timestamp
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize cached EPG", e)
            null
        }
    }

    private fun cacheEpg(data: Map<String, EpgResponse>) {
        try {
            val now = System.currentTimeMillis()
            val serialized = json.encodeToString(data)
            cache.edit {
                putString(KEY_CACHED_EPG, serialized)
                    .putLong(KEY_CACHE_TIMESTAMP, now)
            }
            parsedEpgCache = data
            parsedEpgTimestamp = now
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache XMLTV EPG", e)
        }
    }

    /**
     * 6-level fallback channel matching:
     * 1. Exact epgChannelId match
     * 2. Case-insensitive epgChannelId match
     * 3. Exact display name match
     * 4. Normalized name equality (strips language prefix, quality suffix, country code, etc.)
     * 5. Normalized epgChannelId match against normalized display names
     * 6. Contains match (shorter normalized name contained in longer one, min 4 chars)
     */
    private fun matchChannel(
        item: MediaItem,
        byId: Map<String, String>,
        byIdLower: Map<String, String>,
        byName: Map<String, String>,
        byNormalized: Map<String, String>,
        normalizedNames: Array<String>,
        normalizedIds: Array<String>,
    ): String? {
        val epgChannelId = item.providerData["epgChannelId"]

        // 1. Exact epgChannelId
        if (epgChannelId != null) {
            byId[epgChannelId]?.let { return it }
        }

        // 2. Case-insensitive epgChannelId
        if (epgChannelId != null) {
            byIdLower[epgChannelId.lowercase()]?.let { return it }
        }

        // 3. Exact display name
        byName[item.name]?.let { return it }

        val normalizedItemName = normalizeName(item.name)
        if (normalizedItemName.isEmpty()) return null

        // 4. Normalized name equality
        byNormalized[normalizedItemName]?.let { return it }

        // 5. Normalized epgChannelId against normalized display names
        if (epgChannelId != null) {
            val normalizedEpgId = normalizeName(epgChannelId)
            if (normalizedEpgId.isNotEmpty()) {
                byNormalized[normalizedEpgId]?.let { return it }
            }
        }

        // 6. Contains match (min 4 chars to avoid false positives, pre-filter by length)
        if (normalizedItemName.length >= 4) {
            val itemLen = normalizedItemName.length
            // Performance Optimization: Use parallel arrays and index-based iteration
            for (i in normalizedNames.indices) {
                val norm = normalizedNames[i]
                val normLen = norm.length

                // Only check contains when needle ≤ haystack length
                if (itemLen >= normLen) {
                    if (normalizedItemName.contains(norm)) return normalizedIds[i]
                } else {
                    if (norm.contains(normalizedItemName)) return normalizedIds[i]
                }
            }
        }

        return null
    }

    private fun normalizeName(name: String): String = ChannelNameNormalizer.normalize(name)
}
