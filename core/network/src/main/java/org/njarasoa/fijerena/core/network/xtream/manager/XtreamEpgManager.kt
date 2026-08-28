package org.njarasoa.fijerena.core.network.xtream.manager
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.suspendResultOf
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpgCacheDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpgCacheEntity
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.EPG_CACHE_EXPIRY_MS
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_EPG_PREFIX
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_LEGACY_EPG_PREFS_PURGED
import org.njarasoa.fijerena.core.player.model.EpgResponse

class XtreamEpgManager(
    private val sessionManager: XtreamSessionManager,
    private val sharedPreferences: SharedPreferences,
    private val providerSettings: ProviderSettings,
    private val epgCacheDao: XtreamEpgCacheDao,
    private val providerId: Long,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // See MediaRepository's identical writeScope/commitAsync for the full rationale. Kept here
    // only for the one-time legacy-prefs purge and for the fire-and-forget cache invalidations,
    // which must not block the caller.
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Whether caching is enabled for this provider */
    private val cachingEnabled: Boolean get() = providerSettings.cachingEnabled

    init {
        purgeLegacyPrefsCache()
        evictStaleCache()
    }

    /**
     * Sweep EPG rows that are already past the expiry.
     *
     * The cache expired but never evicted: reads filter `updatedAt >= cutoff`, so stale rows went
     * unread while still occupying disk. Measured on a Shield, the table reached 1.2 GB — 83% of
     * `xtream_v2.db` — of which *none* was inside the 6-hour window, so it held no usable EPG at
     * all. See docs/plans/tv-ui-performance-plan.md.
     *
     * Batched, with a pause between batches, for the reason in [XtreamEpgCacheDao.deleteStaleBatch]:
     * this database is `auto_vacuum = FULL`, so one unbounded delete tries to reshuffle the whole
     * file in a single transaction. Runs once per process — the sweep is not provider-scoped, so a
     * second manager would only re-scan a table it just swept — and off the caller's thread.
     *
     * Failures are logged, not swallowed. An earlier attempt wrapped this in a bare `runCatching`
     * and the reason it never committed was lost.
     */
    private fun evictStaleCache() {
        if (!staleEvictionDone.compareAndSet(false, true)) return
        writeScope.launch {
            val started = System.currentTimeMillis()
            var removed = 0
            try {
                while (true) {
                    val n = epgCacheDao.deleteStaleBatch(freshnessCutoff(), STALE_SWEEP_BATCH)
                    if (n <= 0) break
                    removed += n
                    // Yield the database between commits so browsing stays responsive while a
                    // long-neglected cache is worked through.
                    delay(STALE_SWEEP_PAUSE_MS)
                }
                if (removed > 0) {
                    Log.i(TAG, "EPG cache sweep removed $removed stale rows in ${System.currentTimeMillis() - started}ms")
                }
            } catch (e: Exception) {
                Log.w(TAG, "EPG cache sweep failed after $removed rows", e)
            }
        }
    }

    /**
     * Drop the pre-SQLite `epg_*` blobs from SharedPreferences.
     *
     * A full live catalogue wrote one string + one long per channel into this file; on a 53k
     * channel provider that reached 84 MB, all of it parsed into RAM for the lifetime of every
     * process that touched the prefs, and rewritten whole on each commit. One purge shrinks the
     * file back to the handful of keys that belong there.
     */
    private fun purgeLegacyPrefsCache() {
        if (sharedPreferences.getBoolean(KEY_LEGACY_EPG_PREFS_PURGED, false)) return
        writeScope.launch {
            // KEY_EPG_TIMESTAMP_PREFIX starts with KEY_EPG_PREFIX, so one prefix covers both.
            val stale = sharedPreferences.all.keys.filter { it.startsWith(KEY_EPG_PREFIX) }
            sharedPreferences.edit(commit = true) {
                stale.forEach { remove(it) }
                putBoolean(KEY_LEGACY_EPG_PREFS_PURGED, true)
            }
        }
    }

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
     * Fetches EPG data for multiple streams, [CHUNK_SIZE] requests in flight at a time.
     *
     * Coroutines are created one chunk at a time on purpose. Mapping every id to an `async {}`
     * up front — with a Semaphore gating only the in-flight requests — meant a 53k-channel
     * catalogue allocated 53k coroutines and held every response at once, hundreds of MB
     * resident and enough GC pressure to stall video playback into an ANR.
     *
     * Uses [fetchEpgDirect] to avoid per-call overhead from [getEpgForStream]
     * (redundant dispatcher switches, Result wrapping, and unstructured background
     * coroutines for cache refreshes). The API service is resolved once upfront.
     */
    suspend fun getEpgForStreams(streamIds: List<Int>): Result<Map<Int, EpgResponse>> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val service = sessionManager.apiService ?: throw Exception("Not authenticated")
                val results = mutableMapOf<Int, EpgResponse>()
                for (chunk in streamIds.chunked(CHUNK_SIZE)) {
                    val cached = getCachedEpgBatch(chunk)
                    results.putAll(cached)

                    val misses = chunk.filter { it !in cached }
                    if (misses.isEmpty()) continue

                    val fetched =
                        coroutineScope {
                            misses
                                .map { streamId ->
                                    async {
                                        val epg = fetchEpgDirect(streamId, service, cache = false)
                                        if (epg != null) streamId to epg else null
                                    }
                                }.awaitAll()
                        }.filterNotNull()

                    if (fetched.isNotEmpty()) {
                        results.putAll(fetched)
                        // One batched insert per chunk — the old path wrote every freshly fetched
                        // entry back through a single blocking SharedPreferences commit.
                        cacheEpgBatch(fetched.toMap())
                    }
                }
                results
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
        val payload = epgCacheDao.getFreshPayload(providerId, streamId, freshnessCutoff()) ?: return null
        return decode(payload)
    }

    /** Batched [getCachedEpg] — one query per chunk instead of one per stream. */
    private fun getCachedEpgBatch(streamIds: List<Int>): Map<Int, EpgResponse> {
        if (!cachingEnabled) return emptyMap()
        val rows = epgCacheDao.getFresh(providerId, streamIds, freshnessCutoff())
        if (rows.isEmpty()) return emptyMap()
        val result = mutableMapOf<Int, EpgResponse>()
        for (row in rows) {
            val decoded = decode(row.payload) ?: continue
            result[row.streamId] = decoded
        }
        return result
    }

    private fun freshnessCutoff(): Long = System.currentTimeMillis() - EPG_CACHE_EXPIRY_MS

    private fun decode(payload: String): EpgResponse? =
        try {
            json.decodeFromString<EpgResponse>(payload)
        } catch (e: Exception) {
            null
        }

    /**
     * Cache EPG data for a stream
     */
    private fun cacheEpg(
        streamId: Int,
        epg: EpgResponse,
    ) {
        cacheEpgBatch(mapOf(streamId to epg))
    }

    private fun cacheEpgBatch(entries: Map<Int, EpgResponse>) {
        if (!cachingEnabled || entries.isEmpty()) return
        val now = System.currentTimeMillis()
        val rows =
            entries.mapNotNull { (streamId, epg) ->
                val payload = json.encodeToString(epg)
                // A handful of channels carry enormous listings — the largest measured was a single
                // 1.7 MB blob, and the 634 rows above this threshold accounted for ~236 MiB on their
                // own. They are the least worth caching too: a channel with that many programmes is
                // no cheaper to re-parse from disk than to refetch. Over the cap, skip the write and
                // let the next read go to the network.
                if (payload.length > MAX_CACHED_PAYLOAD_CHARS) {
                    null
                } else {
                    XtreamEpgCacheEntity(
                        providerId = providerId,
                        streamId = streamId,
                        payload = payload,
                        updatedAt = now,
                    )
                }
            }
        if (rows.isEmpty()) return
        epgCacheDao.upsertAll(rows)
    }

    /**
     * Clear EPG cache for a specific stream
     */
    fun clearEpgCache(streamId: Int) {
        writeScope.launch { epgCacheDao.deleteStream(providerId, streamId) }
    }

    /**
     * Clear all EPG cache
     */
    fun clearAllEpgCache() {
        writeScope.launch { epgCacheDao.deleteAll(providerId) }
    }

    private companion object {
        const val TAG = "XtreamEpgManager"

        /** Guards [evictStaleCache]; the sweep is process-wide, not per provider. */
        val staleEvictionDone = java.util.concurrent.atomic.AtomicBoolean(false)

        /** Rows per sweep commit — small enough that `auto_vacuum = FULL` page movement stays bounded. */
        const val STALE_SWEEP_BATCH = 400

        /** Breather between sweep commits so a large backlog does not monopolise the database. */
        const val STALE_SWEEP_PAUSE_MS = 50L

        /** Cache ceiling for one channel's listings; see [cacheEpgBatch]. */
        const val MAX_CACHED_PAYLOAD_CHARS = 64 * 1024

        /** Requests in flight per chunk — matches the concurrency the old Semaphore allowed. */
        const val CHUNK_SIZE = 10
    }
}
