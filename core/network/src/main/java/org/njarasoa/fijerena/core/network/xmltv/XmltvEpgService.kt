package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSearchResultRow
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgWindowRow
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class XmltvEpgService(
    private val context: Context,
    private val providerId: Long,
) {
    companion object {
        private const val TAG = "XmltvEpgService"
        private const val PARSED_CACHE_TTL_MS = 12L * 60 * 60 * 1000

        // Channels whose parsed listings stay in memory. The player asks for one channel at a
        // time, so this covers a long zapping session; the index answers anything older quickly.
        private const val PARSED_CACHE_MAX_CHANNELS = 32
        private const val MISS_CACHE_MS = 60_000L

        // One per provider for the process, not per instance: EpgFileManager clears it after a
        // sync through an instance of its own, not the one MediaRepository holds.
        private val parsedCaches = ConcurrentHashMap<Long, LinkedHashMap<String, CachedListings>>()

        // Access-ordered, so the channel used least recently goes first.
        private fun newParsedCache() =
            object : LinkedHashMap<String, CachedListings>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedListings>?): Boolean =
                    size > PARSED_CACHE_MAX_CHANNELS
            }
    }

    // The parsed listings used to live in SharedPreferences as one JSON blob of every channel
    // asked for since the last index build: it grew with each channel change, was rewritten whole
    // each time, and apply() made the main thread wait for it in onPause/onStop. Memory only now;
    // the old file is deleted once, off the main thread.
    private val legacyPrefsDeleted = AtomicBoolean(false)

    private class CachedListings(
        val epg: EpgResponse,
        val indexGeneration: Long,
        val cachedAtMs: Long,
    )

    // Guarded by itself: several IO threads read and write it at once.
    private val parsedEpgCache = parsedCaches.computeIfAbsent(providerId) { newParsedCache() }

    // Cached channel match maps — avoids full DB scan + 5 map builds on every getNowPlayingForItems call
    private var cachedChannelMaps: ChannelMatchMaps? = null

    // A miss is cached too, briefly: whenever the maps come out empty — no enabled source for this
    // provider, or no indexed channel belonging to one — every call would redo the query, and
    // getNowPlayingForItems runs on every channel-list render and channel switch. Bounded rather
    // than permanent: the index can finish
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
        // Programme lookups keyed on xmltv id alone would also match another provider's source
        // holding the same id, so every query below is restricted to these sources too.
        val sourceIds: List<Long>,
    )

    private suspend fun buildChannelMatchMaps(): ChannelMatchMaps? {
        cachedChannelMaps?.let { return it }
        if (System.currentTimeMillis() < missCachedUntilMs) return null

        // EPG is provider-scoped: without a real provider there is nothing to match against.
        if (providerId <= 0L) return cacheMiss()

        val settingsDb = SettingsDatabase.getInstance(context)
        val sourceDao = settingsDb.epgSourceDao()
        val validSources = sourceDao.getEnabledSourcesForProvider(providerId)
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
            // A guide channel with no id can't be matched by id: under "" it caught every stream
            // whose own guide id is empty (most of an Xtream panel's), before their names were
            // tried — CY: ANT1 got that empty channel's (no) programmes instead of GR: ANT1's.
            if (ch.xmltvId.isNotBlank()) {
                byId[ch.xmltvId] = ch.xmltvId
                byIdLower[ch.xmltvId.lowercase()] = ch.xmltvId
            }
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
            normalizedIds = normalizedIdsList.toTypedArray(),
            sourceIds = sourceIds,
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
            val matched =
                matchChannel(
                    item,
                    maps.byId,
                    maps.byIdLower,
                    maps.byName,
                    maps.byNormalized,
                    maps.normalizedNames,
                    maps.normalizedIds,
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
            val indexState =
                EpgIndexer.getInstance(context).state.value as? EpgIndexState.Indexed
                    ?: return@withContext emptyMap()

            deleteLegacyPrefs()

            getCachedEpg(indexState.indexedAtMs, channels.map { it.id })?.let { return@withContext it }

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

                // Explicit loops rather than chunked().flatMap() and .groupBy(): they avoid
                // intermediate list and Map.Entry allocations.
                // A channel id can be indexed from several EPG sources, which yields the same
                // programme once per source. Keep one row per start time: listing ids are
                // built from channel id + start epoch and must stay unique.
                val programmesByChannel = mutableMapOf<String, LinkedHashMap<Long, EpgSearchResultRow>>()
                for (chunk in uniqueXmltvIds.chunked(500)) {
                    val rows = dao.getProgrammesForChannels(chunk, maps.sourceIds, windowStart, windowEnd)
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

                cacheEpg(result, indexState.indexedAtMs)
                result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query XMLTV EPG from index: ${e.message}", e)
                emptyMap()
            }
        }

    /**
     * The index's answer for one page of the TV Guide (GD4): [channels]' programmes overlapping
     * [windowStartSec]..[windowEndSec), keyed by item id, each list sorted by start. One query per
     * page, no parsed cache (the guide caches pages itself, per day). Null when the index cannot
     * answer for these channels — not built, nothing matched, or the matched channels have no
     * programme at all — which is what sends the guide to the source's own EPG. An answer with
     * empty [IndexWindow.epg] means the index knows these channels but has nothing in the window;
     * [IndexWindow.latestEndSec] then says where their listings stop.
     */
    suspend fun getEpgForChannelsInWindow(
        channels: List<MediaItem>,
        windowStartSec: Long,
        windowEndSec: Long,
    ): IndexWindow? =
        withContext(Dispatchers.IO) {
            if (EpgIndexer.getInstance(context).state.value !is EpgIndexState.Indexed) return@withContext null
            val maps = buildChannelMatchMaps() ?: return@withContext null
            val matchedIds = matchItems(channels, maps)
            if (matchedIds.isEmpty()) return@withContext null

            val dao = EpgIndexDatabase.getInstance(context).epgIndexDao()
            val uniqueXmltvIds = matchedIds.values.distinct()
            // One row per channel and start time: a channel indexed from several sources yields the
            // same programme once per source, and listing ids (channel + start) must stay unique.
            val byChannel = HashMap<String, LinkedHashMap<Long, EpgWindowRow>>()
            for (chunk in uniqueXmltvIds.chunked(500)) {
                for (row in dao.getProgrammesInWindow(chunk, maps.sourceIds, windowStartSec, windowEndSec)) {
                    byChannel.getOrPut(row.channelId) { LinkedHashMap() }.putIfAbsent(row.startEpoch, row)
                }
            }

            val epg = HashMap<String, EpgResponse>()
            var latestEnd: Long? = null
            for ((itemId, xmltvId) in matchedIds) {
                val rows = byChannel[xmltvId]?.values ?: continue
                epg[itemId] =
                    EpgResponse(
                        listings =
                            rows.map { row ->
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
                val last = rows.maxOf { it.endEpoch }
                if (latestEnd == null || last > latestEnd) latestEnd = last
            }
            if (epg.isEmpty()) {
                // Nothing in the window: does the index hold these channels at all? Only then is it
                // the guide's answer ("stops before this day"); otherwise the source's EPG is asked.
                for (chunk in uniqueXmltvIds.chunked(500)) {
                    val last = dao.getLatestEndForChannels(chunk, maps.sourceIds) ?: continue
                    if (latestEnd == null || last > latestEnd) latestEnd = last
                }
                if (latestEnd == null) return@withContext null
            }
            IndexWindow(epg, latestEnd)
        }

    /** [getEpgForChannelsInWindow]'s answer. [latestEndSec] is the last listing end among these channels. */
    data class IndexWindow(
        val epg: Map<String, EpgResponse>,
        val latestEndSec: Long?,
    )

    /**
     * The guide channel (xmltv id) each item's listings come from, by item id — the same matching
     * the guide grid uses, so a search limited to a guide's channels finds what the grid shows.
     */
    suspend fun matchGuideChannels(items: List<MediaItem>): Map<String, String> =
        withContext(Dispatchers.IO) {
            val maps = buildChannelMatchMaps() ?: return@withContext emptyMap()
            matchItems(items, maps)
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

                // Explicit loops rather than chunked().flatMap() and .associateBy(): they avoid
                // intermediate list and Map.Entry allocations.
                val nowPlayingByChannel = mutableMapOf<String, EpgSearchResultRow>()
                for (chunk in uniqueXmltvIds.chunked(500)) {
                    val rows = dao.getNowPlayingForChannels(chunk, maps.sourceIds, nowEpoch)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query now-playing from index: ${e.message}", e)
                emptyMap()
            }
        }

    fun clearCache() {
        synchronized(parsedEpgCache) { parsedEpgCache.clear() }
        cachedChannelMaps = null
        missCachedUntilMs = 0L
    }

    /**
     * The parsed listings of every channel in [itemIds], if each came from the index build
     * [indexGeneration] and is within the TTL; null when any one is missing or stale.
     */
    internal fun getCachedEpg(
        indexGeneration: Long,
        itemIds: Collection<String>,
    ): Map<String, EpgResponse>? {
        if (itemIds.isEmpty()) return null
        val now = System.currentTimeMillis()
        synchronized(parsedEpgCache) {
            val result = HashMap<String, EpgResponse>(itemIds.size)
            for (id in itemIds) {
                val entry = parsedEpgCache[id] ?: return null
                if (entry.indexGeneration != indexGeneration || now - entry.cachedAtMs > PARSED_CACHE_TTL_MS) {
                    parsedEpgCache.remove(id)
                    return null
                }
                result[id] = entry.epg
            }
            return result
        }
    }

    internal fun cacheEpg(
        data: Map<String, EpgResponse>,
        indexGeneration: Long,
    ) {
        val now = System.currentTimeMillis()
        synchronized(parsedEpgCache) {
            for ((id, epg) in data) parsedEpgCache[id] = CachedListings(epg, indexGeneration, now)
        }
    }

    private fun deleteLegacyPrefs() {
        if (legacyPrefsDeleted.compareAndSet(false, true)) {
            runCatching { context.deleteSharedPreferences("xmltv_cache_$providerId") }
                .onFailure { Log.w(TAG, "Couldn't delete the old XMLTV cache file", it) }
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
        val epgChannelId = item.providerData["epgChannelId"]?.takeIf { it.isNotBlank() }

        if (epgChannelId != null) {
            byId[epgChannelId]?.let { return it }
        }

        if (epgChannelId != null) {
            byIdLower[epgChannelId.lowercase()]?.let { return it }
        }

        byName[item.name]?.let { return it }

        val normalizedItemName = normalizeName(item.name)
        if (normalizedItemName.isEmpty()) return null

        byNormalized[normalizedItemName]?.let { return it }

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
