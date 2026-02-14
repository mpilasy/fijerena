package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.network.NetworkMonitor
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and caches iptv-org API files (channels.json + guides.json) to disk
 * with a 7-day TTL.
 *
 * Uses HttpURLConnection (same pattern as EpgFileManager) for memory safety with
 * large JSON files. Parses with Json.decodeFromStream() to avoid loading full
 * strings into memory.
 */
class IptvOrgApiCache private constructor(private val context: Context) {

    companion object {
        private const val TAG = "IptvOrgApiCache"
        private const val PREFS_NAME = "iptv_org_api_cache"
        private const val KEY_CHANNELS_TIMESTAMP = "channels_timestamp"
        private const val KEY_GUIDES_TIMESTAMP = "guides_timestamp"
        private const val TTL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 120_000 // 2 minutes
        private const val BUFFER_SIZE = 65536

        private const val CHANNELS_URL = "https://iptv-org.github.io/api/channels.json"
        private const val GUIDES_URL = "https://iptv-org.github.io/api/guides.json"

        @Volatile
        private var instance: IptvOrgApiCache? = null

        fun getInstance(context: Context): IptvOrgApiCache {
            return instance ?: synchronized(this) {
                instance ?: IptvOrgApiCache(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val channelsFile = File(context.cacheDir, "iptv_org_channels.json")
    private val guidesFile = File(context.cacheDir, "iptv_org_guides.json")

    // In-memory cache to avoid re-parsing on each call within same session
    @Volatile
    private var cachedChannels: List<IptvOrgChannel>? = null
    @Volatile
    private var cachedGuides: List<IptvOrgGuide>? = null

    /**
     * Get iptv-org channels, downloading if stale or missing.
     * Returns null if download fails and no cached data exists.
     * WiFi-only — returns cached data on cellular if available.
     */
    suspend fun getChannels(): List<IptvOrgChannel>? {
        cachedChannels?.let { return it }

        if (isFresh(KEY_CHANNELS_TIMESTAMP) && channelsFile.exists()) {
            return parseChannels()?.also { cachedChannels = it }
        }

        if (NetworkMonitor.currentNetworkType == NetworkType.CELLULAR) {
            Log.d(TAG, "On cellular, using stale channels cache if available")
            return if (channelsFile.exists()) parseChannels()?.also { cachedChannels = it } else null
        }

        if (downloadToFile(CHANNELS_URL, channelsFile)) {
            prefs.edit().putLong(KEY_CHANNELS_TIMESTAMP, System.currentTimeMillis()).apply()
            return parseChannels()?.also { cachedChannels = it }
        }

        // Download failed — use stale cache if available
        return if (channelsFile.exists()) parseChannels()?.also { cachedChannels = it } else null
    }

    /**
     * Get iptv-org guides index, downloading if stale or missing.
     * Returns null if download fails and no cached data exists.
     */
    suspend fun getGuides(): List<IptvOrgGuide>? {
        cachedGuides?.let { return it }

        if (isFresh(KEY_GUIDES_TIMESTAMP) && guidesFile.exists()) {
            return parseGuides()?.also { cachedGuides = it }
        }

        if (NetworkMonitor.currentNetworkType == NetworkType.CELLULAR) {
            Log.d(TAG, "On cellular, using stale guides cache if available")
            return if (guidesFile.exists()) parseGuides()?.also { cachedGuides = it } else null
        }

        if (downloadToFile(GUIDES_URL, guidesFile)) {
            prefs.edit().putLong(KEY_GUIDES_TIMESTAMP, System.currentTimeMillis()).apply()
            return parseGuides()?.also { cachedGuides = it }
        }

        return if (guidesFile.exists()) parseGuides()?.also { cachedGuides = it } else null
    }

    /**
     * Invalidate in-memory caches (e.g. on provider change).
     */
    fun invalidateMemoryCache() {
        cachedChannels = null
        cachedGuides = null
    }

    private fun isFresh(timestampKey: String): Boolean {
        val timestamp = prefs.getLong(timestampKey, 0L)
        return (System.currentTimeMillis() - timestamp) < TTL_MS
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun parseChannels(): List<IptvOrgChannel>? {
        return try {
            channelsFile.inputStream().buffered().use { stream ->
                json.decodeFromStream<List<IptvOrgChannel>>(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse channels.json", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM parsing channels.json", e)
            System.gc()
            null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun parseGuides(): List<IptvOrgGuide>? {
        return try {
            guidesFile.inputStream().buffered().use { stream ->
                json.decodeFromStream<List<IptvOrgGuide>>(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse guides.json", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM parsing guides.json", e)
            System.gc()
            null
        }
    }

    private fun downloadToFile(url: String, target: File): Boolean {
        var connection: HttpURLConnection? = null
        val tmpFile = File(target.parent, "${target.name}.tmp")
        try {
            Log.d(TAG, "Downloading: $url")
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept-Encoding", "identity")
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                Log.w(TAG, "HTTP $statusCode for $url")
                return false
            }

            tmpFile.delete()
            connection.inputStream.buffered(BUFFER_SIZE).use { input ->
                tmpFile.outputStream().buffered(BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            // Atomic rename
            target.delete()
            tmpFile.renameTo(target)
            Log.d(TAG, "Downloaded ${target.name}: ${target.length() / 1024}KB")
            return true

        } catch (e: Exception) {
            tmpFile.delete()
            Log.e(TAG, "Download failed: $url", e)
            return false
        } catch (e: OutOfMemoryError) {
            tmpFile.delete()
            Log.e(TAG, "OOM during download: $url", e)
            System.gc()
            return false
        } finally {
            connection?.disconnect()
        }
    }
}
