package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse
import java.io.File

class XmltvEpgService(
    context: Context,
    private val providerId: Long
) {
    companion object {
        private const val TAG = "XmltvEpgService"
        private const val PARSED_CACHE_TTL_MS = 12L * 60 * 60 * 1000 // 12 hours
        private const val KEY_CACHED_EPG = "xmltv_epg_data"
        private const val KEY_CACHE_TIMESTAMP = "xmltv_cache_timestamp"
        private const val KEY_CACHED_URL = "xmltv_cached_url"
    }

    private val cache: SharedPreferences = context.getSharedPreferences(
        "xmltv_cache_$providerId",
        Context.MODE_PRIVATE
    )

    private val epgFileManager = EpgFileManager.getInstance(context)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getEpgForChannels(
        channels: List<MediaItem>,
        xmltvUrl: String
    ): Map<String, EpgResponse> = withContext(Dispatchers.IO) {
        // Layer 1: Check parsed results cache (instant)
        val cachedResult = getCachedEpg(xmltvUrl)
        if (cachedResult != null) {
            Log.d(TAG, "Returning cached XMLTV EPG for ${cachedResult.size} channels")
            return@withContext cachedResult
        }

        // Layer 2: Get file from EpgFileManager (downloaded in background)
        val localFile = epgFileManager.getEpgFile()
        if (localFile == null) {
            Log.w(TAG, "No EPG file available from EpgFileManager")
            return@withContext emptyMap()
        }

        // Layer 3: Parse from local file with channel + time filters
        Log.d(TAG, "Parsing XMLTV from local file: ${localFile.length() / (1024 * 1024)}MB")
        val xmltvData = parseFromFile(localFile, channels)
        if (xmltvData == null) {
            Log.w(TAG, "Failed to parse XMLTV data from file")
            return@withContext emptyMap()
        }

        Log.d(TAG, "Parsed ${xmltvData.channels.size} channels, ${xmltvData.programmes.values.sumOf { it.size }} programmes")

        // Match channels and convert
        val result = matchAndConvert(channels, xmltvData)

        // Cache the parsed results
        cacheEpg(xmltvUrl, result)

        Log.d(TAG, "Matched XMLTV EPG for ${result.size} of ${channels.size} channels")
        result
    }

    fun clearCache() {
        cache.edit().clear().apply()
    }

    private fun getCachedEpg(xmltvUrl: String): Map<String, EpgResponse>? {
        val cachedUrl = cache.getString(KEY_CACHED_URL, null)
        if (cachedUrl != xmltvUrl) return null

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

    private fun cacheEpg(xmltvUrl: String, data: Map<String, EpgResponse>) {
        try {
            val serialized = json.encodeToString(data)
            cache.edit()
                .putString(KEY_CACHED_EPG, serialized)
                .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                .putString(KEY_CACHED_URL, xmltvUrl)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache XMLTV EPG", e)
        }
    }

    /** Parse XMLTV from a local file with channel + time filters applied during parse. */
    private fun parseFromFile(file: File, mediaItems: List<MediaItem>): XmltvData? {
        return try {
            val channelFilter: (Map<String, XmltvChannel>) -> Set<String> = { xmltvChannels ->
                mediaItems.mapNotNull { item -> matchChannel(item, xmltvChannels) }.toSet()
            }
            val nowSeconds = System.currentTimeMillis() / 1000
            val timeWindow = Pair(nowSeconds - 24 * 3600, nowSeconds + 24 * 3600)

            file.inputStream().buffered().use { stream ->
                XmltvParser.parse(stream, channelFilter, timeWindow)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse XMLTV file: ${e.message}", e)
            null
        }
    }

    private fun matchAndConvert(
        channels: List<MediaItem>,
        xmltvData: XmltvData
    ): Map<String, EpgResponse> {
        val result = mutableMapOf<String, EpgResponse>()
        val xmltvChannels = xmltvData.channels

        for (item in channels) {
            val matchedXmltvId = matchChannel(item, xmltvChannels) ?: continue
            val programmes = xmltvData.programmes[matchedXmltvId] ?: continue
            if (programmes.isEmpty()) continue

            val epgPrograms = programmes.map { prog ->
                EpgProgram(
                    id = "${prog.channelId}_${prog.startEpoch}",
                    epgId = prog.channelId,
                    title = prog.title,
                    start = prog.startEpoch.toString(),
                    end = prog.endEpoch.toString(),
                    description = prog.description,
                    channelId = prog.channelId
                )
            }

            result[item.id] = EpgResponse(listings = epgPrograms)
        }

        return result
    }

    private fun matchChannel(
        item: MediaItem,
        xmltvChannels: Map<String, XmltvChannel>
    ): String? {
        val epgChannelId = item.providerData["epgChannelId"]

        // 1. Exact match on epgChannelId
        if (epgChannelId != null && xmltvChannels.containsKey(epgChannelId)) {
            return epgChannelId
        }

        // 2. Case-insensitive match on epgChannelId
        if (epgChannelId != null) {
            val match = xmltvChannels.keys.firstOrNull {
                it.equals(epgChannelId, ignoreCase = true)
            }
            if (match != null) return match
        }

        // 3. Exact display name match
        val nameMatch = xmltvChannels.entries.firstOrNull {
            it.value.displayName == item.name
        }
        if (nameMatch != null) return nameMatch.key

        // 4. Normalized name match (strip non-alphanumeric, collapse whitespace, lowercase)
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
