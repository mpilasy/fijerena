package org.njarasoa.fijerena.core.network.xtream.manager
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.suspendResultOf
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.EPG_CACHE_EXPIRY_MS
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_EPG_PREFIX
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_EPG_TIMESTAMP_PREFIX
import org.njarasoa.fijerena.core.player.model.EpgResponse

class XtreamEpgManager(
    private val sessionManager: XtreamSessionManager,
    private val sharedPreferences: SharedPreferences,
    private val providerSettings: ProviderSettings,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // See MediaRepository's identical writeScope/commitAsync for the full rationale. This one
    // matters more than most: getEpgForStreams() fans out up to 10 concurrent cache writes per
    // category load, which can otherwise pile up a real QueuedWork backlog right as Media3's
    // startForegroundService() dispatches are firing for the same channel-load.
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private fun commitAsync(action: SharedPreferences.Editor.() -> Unit) {
        writeScope.launch { sharedPreferences.edit(commit = true, action = action) }
    }

    /** Whether caching is enabled for this provider */
    private val cachingEnabled: Boolean get() = providerSettings.cachingEnabled

    /**
     * Fetches EPG data for a specific stream with caching
     */
    suspend fun getEpgForStream(streamId: Int): Result<EpgResponse> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val service = sessionManager.apiService ?: throw Exception("Not authenticated")

                // Try cache first
                val cached = getCachedEpg(streamId)
                if (cached != null) {
                    // Refresh in background
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val fresh = service.getEpgForStream(streamId)
                            cacheEpg(streamId, fresh)
                        } catch (e: Exception) {
                            // Ignore network errors when refreshing
                        }
                    }
                    return@suspendResultOf cached
                }

                val epg = service.getEpgForStream(streamId)
                cacheEpg(streamId, epg)
                epg
            }
        }

    /**
     * Fetches EPG data for multiple streams in parallel with concurrency limiting.
     *
     * Uses [fetchEpgDirect] to avoid per-call overhead from [getEpgForStream]
     * (redundant dispatcher switches, Result wrapping, and unstructured background
     * coroutines for cache refreshes). The API service is resolved once upfront.
     */
    suspend fun getEpgForStreams(streamIds: List<Int>): Result<Map<Int, EpgResponse>> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val service = sessionManager.apiService ?: throw Exception("Not authenticated")
                coroutineScope {
                    val semaphore = Semaphore(10)
                    val deferreds =
                        streamIds.map { streamId ->
                            async {
                                semaphore.withPermit {
                                    val epg = fetchEpgDirect(streamId, service, cache = false)
                                    if (epg != null) streamId to epg else null
                                }
                            }
                        }

                    val results = mutableMapOf<Int, EpgResponse>()
                    // Freshly network-fetched entries only (cache hits didn't touch the network and
                    // are already on disk) — written as a single commit() below instead of one
                    // commit()+fsync per stream, which used to serialize up to 50 blocking fsyncs
                    // to the same SharedPreferences file on every category/preview load.
                    val toCache = mutableMapOf<Int, EpgResponse>()
                    deferreds.awaitAll().forEach { pair ->
                        if (pair != null) {
                            val (streamId, epg) = pair
                            results[streamId] = epg
                            if (getCachedEpg(streamId) == null) {
                                toCache[streamId] = epg
                            }
                        }
                    }
                    if (toCache.isNotEmpty()) {
                        cacheEpgBatch(toCache)
                    }
                    results
                }
            }
        }

    /**
     * Internal EPG fetch that bypasses [getEpgForStream] overhead for batch use.
     *
     * - No `withContext(Dispatchers.IO)` (caller already on IO)
     * - No `suspendResultOf` wrapping (caller handles errors)
     * - No background refresh coroutines for cache hits (avoids N fire-and-forget coroutines)
     * - Returns null on failure instead of Result.Error
     */
    private suspend fun fetchEpgDirect(
        streamId: Int,
        service: org.njarasoa.fijerena.core.player.api.XtreamApiService,
        cache: Boolean = true,
    ): EpgResponse? {
        return try {
            val cached = getCachedEpg(streamId)
            if (cached != null) return cached
            val epg = service.getEpgForStream(streamId)
            if (cache) cacheEpg(streamId, epg)
            epg
        } catch (_: Exception) {
            // Continue on failure - EPG may not be available for all channels
            null
        }
    }

    /**
     * Get cached EPG data for a stream
     */
    private fun getCachedEpg(streamId: Int): EpgResponse? {
        if (!cachingEnabled) return null
        val timestamp = sharedPreferences.getLong(KEY_EPG_TIMESTAMP_PREFIX + streamId, 0L)
        if (System.currentTimeMillis() - timestamp > EPG_CACHE_EXPIRY_MS) {
            return null
        }
        val cached = sharedPreferences.getString(KEY_EPG_PREFIX + streamId, null) ?: return null
        return try {
            json.decodeFromString<EpgResponse>(cached)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cache EPG data for a stream
     */
    private fun cacheEpg(
        streamId: Int,
        epg: EpgResponse,
    ) {
        if (!cachingEnabled) return
        commitAsync {
            putString(KEY_EPG_PREFIX + streamId, json.encodeToString(epg))
                .putLong(KEY_EPG_TIMESTAMP_PREFIX + streamId, System.currentTimeMillis())
        }
    }

    /** Same as [cacheEpg] but writes every entry in a single commit() — see [getEpgForStreams]. */
    private fun cacheEpgBatch(entries: Map<Int, EpgResponse>) {
        if (!cachingEnabled) return
        val now = System.currentTimeMillis()
        commitAsync {
            for ((streamId, epg) in entries) {
                putString(KEY_EPG_PREFIX + streamId, json.encodeToString(epg))
                putLong(KEY_EPG_TIMESTAMP_PREFIX + streamId, now)
            }
        }
    }

    /**
     * Clear EPG cache for a specific stream
     */
    fun clearEpgCache(streamId: Int) {
        commitAsync {
            remove(KEY_EPG_PREFIX + streamId)
                .remove(KEY_EPG_TIMESTAMP_PREFIX + streamId)
        }
    }

    /**
     * Clear all EPG cache
     */
    fun clearAllEpgCache() {
        commitAsync {
            sharedPreferences.all.keys
                .filter { it.startsWith(KEY_EPG_PREFIX) || it.startsWith(KEY_EPG_TIMESTAMP_PREFIX) }
                .forEach { remove(it) }
        }
    }
}
