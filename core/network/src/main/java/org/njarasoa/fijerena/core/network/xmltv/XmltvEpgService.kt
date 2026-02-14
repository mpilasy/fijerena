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

    /**
     * Get EPG data for channels from the SQLite index.
     * Returns empty map if no index is available.
     */
    suspend fun getEpgForChannels(
        channels: List<MediaItem>
    ): Map<String, EpgResponse> = withContext(Dispatchers.IO) {
        // Check if index is available
        val indexer = EpgIndexer.getInstance(context)
        if (indexer.state.value !is EpgIndexState.Indexed) {
            return@withContext emptyMap()
        }

        // Check cache first
        val cachedResult = getCachedEpg()
        if (cachedResult != null) {
            Log.d(TAG, "Returning cached XMLTV EPG for ${cachedResult.size} channels")
            return@withContext cachedResult
        }

        try {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

            val nowSeconds = System.currentTimeMillis() / 1000
            val windowStart = nowSeconds - 24 * 3600
            val windowEnd = nowSeconds + 24 * 3600

            // Get all channels from the index
            val allProgrammes = dao.searchByTitleLike("", windowStart, windowEnd)
            if (allProgrammes.isEmpty()) {
                return@withContext emptyMap()
            }

            // Build channel map from indexed data
            val xmltvChannels = allProgrammes
                .map { it.channelId to XmltvChannel(it.channelId, it.channelDisplayName, it.channelIconUrl) }
                .toMap()

            // Group programmes by channel
            val programmesByChannel = allProgrammes.groupBy { it.channelId }

            // Match media items to XMLTV channels and convert
            val result = mutableMapOf<String, EpgResponse>()
            for (item in channels) {
                val matchedId = matchChannel(item, xmltvChannels) ?: continue
                val progs = programmesByChannel[matchedId] ?: continue
                if (progs.isEmpty()) continue

                val epgPrograms = progs.map { row ->
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
                result[item.id] = EpgResponse(listings = epgPrograms)
            }

            // Cache results
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
    }

    private fun getCachedEpg(): Map<String, EpgResponse>? {
        val timestamp = cache.getLong(KEY_CACHE_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - timestamp > PARSED_CACHE_TTL_MS) return null

        val cachedJson = cache.getString(KEY_CACHED_EPG, null) ?: return null
        return try {
            json.decodeFromString<Map<String, EpgResponse>>(cachedJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize cached EPG", e)
            null
        }
    }

    private fun cacheEpg(data: Map<String, EpgResponse>) {
        try {
            val serialized = json.encodeToString(data)
            cache.edit()
                .putString(KEY_CACHED_EPG, serialized)
                .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache XMLTV EPG", e)
        }
    }

    private fun matchChannel(
        item: MediaItem,
        xmltvChannels: Map<String, XmltvChannel>
    ): String? {
        val epgChannelId = item.providerData["epgChannelId"]

        if (epgChannelId != null && xmltvChannels.containsKey(epgChannelId)) {
            return epgChannelId
        }

        if (epgChannelId != null) {
            val match = xmltvChannels.keys.firstOrNull {
                it.equals(epgChannelId, ignoreCase = true)
            }
            if (match != null) return match
        }

        val nameMatch = xmltvChannels.entries.firstOrNull {
            it.value.displayName == item.name
        }
        if (nameMatch != null) return nameMatch.key

        val normalizedItemName = normalizeName(item.name)
        if (normalizedItemName.isNotEmpty()) {
            val normalizedMatch = xmltvChannels.entries.firstOrNull {
                normalizeName(it.value.displayName) == normalizedItemName
            }
            if (normalizedMatch != null) return normalizedMatch.key
        }

        return null
    }

    private fun normalizeName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }
}
