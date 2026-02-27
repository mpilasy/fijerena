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

        private val LANGUAGE_PREFIX_REGEX = Regex("^[A-Za-z]{2,3}:\\s*")
        private val QUALITY_SUFFIX_REGEX = Regex("\\b(fhd|uhd|hd|sd|4k|720p|1080p|1080i|hevc|h\\.?265|h\\.?264|avc|vp9|av1|mpeg[24]|hdr10?)\\b", RegexOption.IGNORE_CASE)
        private val COUNTRY_CODE_REGEX = Regex("\\s*[\\[(][A-Za-z]{2,3}[])]")
        private val UNICODE_SUPERSCRIPT_REGEX = Regex("[\u1D00-\u1DBF\u2070-\u209F\u2460-\u24FF]+")
        private val NON_ALNUM_REGEX = Regex("[^a-z0-9]")
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
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

            // Query all XMLTV channels directly (lightweight — just IDs and names)
            val allXmltvChannels = dao.getAllChannels()
            if (allXmltvChannels.isEmpty()) {
                return@withContext emptyMap()
            }

            // Build lookup structures for matching
            val byId = mutableMapOf<String, String>()           // xmltvId -> xmltvId
            val byIdLower = mutableMapOf<String, String>()      // xmltvId.lowercase -> xmltvId
            val byName = mutableMapOf<String, String>()          // displayName -> xmltvId
            val byNormalized = mutableMapOf<String, String>()    // normalized displayName -> xmltvId
            val normalizedEntries = mutableListOf<Pair<String, String>>() // (normalized displayName, xmltvId)

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

            // Match each media item to an XMLTV channel
            val matchedIds = mutableMapOf<String, String>() // mediaItem.id -> xmltvChannelId
            for (item in channels) {
                val matched = matchChannel(item, byId, byIdLower, byName, byNormalized, normalizedEntries)
                if (matched != null) {
                    matchedIds[item.id] = matched
                }
            }

            if (matchedIds.isEmpty()) {
                Log.d(TAG, "No XMLTV channel matches for ${channels.size} channels")
                return@withContext emptyMap()
            }

            // Fetch programmes only for matched channels
            val nowSeconds = System.currentTimeMillis() / 1000
            val windowStart = nowSeconds - 24 * 3600
            val windowEnd = nowSeconds + 24 * 3600

            val uniqueXmltvIds = matchedIds.values.toSet().toList()
            val allProgrammes = uniqueXmltvIds.chunked(500).flatMap { chunk ->
                dao.getProgrammesForChannels(chunk, windowStart, windowEnd)
            }
            val programmesByChannel = allProgrammes.groupBy { it.channelId }

            // Build result
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

    fun clearCache() {
        cache.edit().clear().apply()
        parsedEpgCache = null
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

    private fun normalizeName(name: String): String {
        return name
            .replace(LANGUAGE_PREFIX_REGEX, "")
            .replace(QUALITY_SUFFIX_REGEX, "")
            .replace(COUNTRY_CODE_REGEX, "")
            .replace(UNICODE_SUPERSCRIPT_REGEX, "")
            .lowercase()
            .replace(NON_ALNUM_REGEX, "")
    }

}
