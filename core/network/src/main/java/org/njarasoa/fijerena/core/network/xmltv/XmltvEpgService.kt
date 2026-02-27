package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse

class XmltvEpgService(
    private val context: Context,
    private val providerId: Long
) {
    companion object {
        private const val TAG = "XmltvEpgService"
        private const val PARSED_CACHE_TTL_MS = 12L * 60 * 60 * 1000
        private const val KEY_CACHED_EPG = "xmltv_epg_data"
        private const val KEY_CACHE_TIMESTAMP = "xmltv_cache_timestamp"
    }

    private val cache: SharedPreferences = context.getSharedPreferences(
        "xmltv_cache_$providerId",
        Context.MODE_PRIVATE
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // In-memory cache — avoid re-deserializing full EPG JSON from SharedPreferences on every call
    private var parsedEpgCache: Map<String, EpgResponse>? = null
    private var parsedEpgTimestamp: Long = 0L

    // Cached channel match maps — avoids full DB scan + 5 map builds on every getNowPlayingForItems call
    private var cachedChannelMaps: ChannelMatchMaps? = null

    private data class ChannelMatchMaps(
        val byId: Map<String, String>,
        val byIdLower: Map<String, String>,
        val byName: Map<String, String>,
        val byNormalized: Map<String, String>,
        val normalizedEntries: List<Pair<String, String>>
    )

    private suspend fun buildChannelMatchMaps(): ChannelMatchMaps? {
        // Return cached maps if available — avoids full DB scan on every call
        cachedChannelMaps?.let { return it }

        val db = EpgIndexDatabase.getInstance(context)
        val allXmltvChannels = db.epgIndexDao().getAllChannels()
        if (allXmltvChannels.isEmpty()) return null

        val byId = mutableMapOf<String, String>()
        val byIdLower = mutableMapOf<String, String>()
        val byName = mutableMapOf<String, String>()
        val byNormalized = mutableMapOf<String, String>()
        val normalizedEntries = mutableListOf<Pair<String, String>>()

        for (ch in allXmltvChannels) {
            byId[ch.xmltvId] = ch.xmltvId
            byIdLower[ch.xmltvId.lowercase()] = ch.xmltvId
            byName[ch.displayName] = ch.xmltvId
            val norm = normalizeName(ch.displayName)
            if (norm.isNotEmpty()) {
                byNormalized[norm] = ch.xmltvId
                normalizedEntries.add(norm to ch.xmltvId)
            }
        }

        return ChannelMatchMaps(byId, byIdLower, byName, byNormalized, normalizedEntries)
            .also { cachedChannelMaps = it }
    }

    private fun matchItems(
        items: List<MediaItem>,
        maps: ChannelMatchMaps
    ): Map<String, String> {
        val matchedIds = mutableMapOf<String, String>()
        for (item in items) {
            val matched = matchChannel(item, maps.byId, maps.byIdLower, maps.byName, maps.byNormalized, maps.normalizedEntries)
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
    suspend fun getEpgForChannels(
        channels: List<MediaItem>
    ): Map<String, EpgResponse> = withContext(Dispatchers.IO) {
        val indexer = EpgIndexer.getInstance(context)
        if (indexer.state.value !is EpgIndexState.Indexed) {
            return@withContext emptyMap()
        }

        val cachedResult = getCachedEpg()
        if (cachedResult != null) {
            Log.d(TAG, "Returning cached XMLTV EPG for ${cachedResult.size} channels")
            return@withContext cachedResult
        }

        try {
            val maps = buildChannelMatchMaps() ?: return@withContext emptyMap()
            val matchedIds = matchItems(channels, maps)

            if (matchedIds.isEmpty()) {
                Log.d(TAG, "No XMLTV channel matches for ${channels.size} channels")
                return@withContext emptyMap()
            }

            val dao = EpgIndexDatabase.getInstance(context).epgIndexDao()
            val nowSeconds = System.currentTimeMillis() / 1000
            val windowStart = nowSeconds - 24 * 3600
            val windowEnd = nowSeconds + 24 * 3600

            val uniqueXmltvIds = matchedIds.values.distinct()
            val allProgrammes = uniqueXmltvIds.chunked(500).flatMap { chunk ->
                dao.getProgrammesForChannels(chunk, windowStart, windowEnd)
            }
            val programmesByChannel = allProgrammes.groupBy { it.channelId }

            val result = mutableMapOf<String, EpgResponse>()
            for ((itemId, xmltvId) in matchedIds) {
                val progs = programmesByChannel[xmltvId] ?: continue
                if (progs.isEmpty()) continue
                result[itemId] = EpgResponse(
                    listings = progs.map { row ->
                        EpgProgram(
                            id = "${row.channelId}_${row.startEpoch}",
                            epgId = row.channelId,
                            title = row.title,
                            start = row.startEpoch.toString(),
                            end = row.endEpoch.toString(),
                            description = row.description,
                            channelId = row.channelId
                        )
                    }
                )
            }

            cacheEpg(result)
            Log.d(TAG, "Matched XMLTV EPG for ${result.size} of ${channels.size} channels")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query XMLTV EPG from index: ${e.message}", e)
            emptyMap()
        }
    }

    /**
     * Lightweight now-playing query: returns only the currently airing programme per item.
     * Uses the same 6-level channel matching but queries only current programmes.
     */
    suspend fun getNowPlayingForItems(
        items: List<MediaItem>
    ): Map<String, EpgProgram> = withContext(Dispatchers.IO) {
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
            val nowPlayingRows = uniqueXmltvIds.chunked(500).flatMap { chunk ->
                dao.getNowPlayingForChannels(chunk, nowEpoch)
            }
            val nowPlayingByChannel = nowPlayingRows.associateBy { it.channelId }

            val result = mutableMapOf<String, EpgProgram>()
            for ((itemId, xmltvId) in matchedIds) {
                val row = nowPlayingByChannel[xmltvId] ?: continue
                result[itemId] = EpgProgram(
                    id = "${row.channelId}_${row.startEpoch}",
                    epgId = row.channelId,
                    title = row.title,
                    start = row.startEpoch.toString(),
                    end = row.endEpoch.toString(),
                    description = row.description,
                    channelId = row.channelId
                )
            }

            Log.d(TAG, "Now-playing from index: ${result.size} of ${items.size} items")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query now-playing from index: ${e.message}", e)
            emptyMap()
        }
    }

    fun clearCache() {
        cache.edit().clear().apply()
        parsedEpgCache = null
        cachedChannelMaps = null
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
            cache.edit()
                .putString(KEY_CACHED_EPG, serialized)
                .putLong(KEY_CACHE_TIMESTAMP, now)
                .apply()
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
        normalizedEntries: List<Pair<String, String>>
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

        // 6. Contains match (min 4 chars to avoid false positives)
        if (normalizedItemName.length >= 4) {
            for ((norm, xmltvId) in normalizedEntries) {
                if (norm.length < 4) continue
                if (normalizedItemName.contains(norm) || norm.contains(normalizedItemName)) {
                    return xmltvId
                }
            }
        }

        return null
    }

    private fun normalizeName(name: String): String = ChannelNameNormalizer.normalize(name)

}
