package org.njarasoa.fijerena.core.network.xtream.manager
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_PANEL_GUIDE_LOCAL_AS_UTC
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import org.njarasoa.fijerena.core.player.model.EpgResponse

class XtreamEpgManager(
    private val sessionManager: XtreamSessionManager,
    private val sharedPreferences: SharedPreferences,
    private val providerSettings: ProviderSettings,
    private val epgCacheDao: XtreamEpgCacheDao,
    private val providerId: Long,
    // The source's own xmltv.php in the guide index, to tell whether this panel's guide labels its
    // local time as UTC (PanelGuideTimes). Null: never checked, never corrected.
    private val ownXmltv: OwnXmltvListings? = null,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // As MediaRepository's writeScope/commitAsync. Only for the one-time legacy-prefs purge and the
    // cache invalidations, which must not block the caller.
    private val writeScope = AppScopes.create("XtreamEpgManager.write", Dispatchers.IO.limitedParallelism(1))

    // Whether this panel's guide stamps its wall time as UTC; kept across restarts, re-measured on
    // every fresh answer ([remeasure]). Applied where listings leave this class, never to the cache.
    @Volatile
    private var labelsLocalAsUtc = sharedPreferences.getBoolean(KEY_PANEL_GUIDE_LOCAL_AS_UTC, false)

    // Set once a comparison reached a verdict in this process. Until then cached answers are
    // compared too, so an update or a restart doesn't wait for the 6-hour cache to expire.
    @Volatile
    private var measuredThisRun = false

    init {
        purgeLegacyPrefsCache()
        evictStaleCache()
    }

    /**
     * Sweeps the EPG rows past the expiry. Reads filter `updatedAt >= cutoff`, so expired rows were
     * never read but stayed on disk: on a Shield the table reached 1.2 GB, 83% of `xtream_v2.db`
     * (the tv ui performance plan).
     *
     * Batched with a pause, for the reason in [XtreamEpgCacheDao.deleteStaleBatch] (`auto_vacuum =
     * FULL`). Once per process, as the sweep is not per provider, and off the caller's thread.
     * Failures are logged.
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "EPG cache sweep failed after $removed rows", e)
            }
        }
    }

    /**
     * Drops the old `epg_*` blobs from SharedPreferences: on a 53k-channel provider they reached
     * 84 MB, parsed into RAM by each process that read the prefs and rewritten whole on each commit.
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

    suspend fun getEpgForStream(streamId: Int): Result<EpgResponse> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val service = sessionManager.apiService ?: throw Exception("Not authenticated")

                val cached = getCachedEpg(streamId)
                if (cached != null) {
                    // Refresh in background
                    writeScope.launch {
                        try {
                            val fresh = service.getEpgForStream(streamId)
                            cacheEpg(streamId, fresh)
                            remeasure(listOf(fresh))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Ignore network errors when refreshing
                        }
                    }
                    return@suspendResultOf shown(cached)
                }

                val epg = service.getEpgForStream(streamId)
                cacheEpg(streamId, epg)
                remeasure(listOf(epg))
                shown(epg)
            }
        }

    /**
     * Fetches EPG data for many streams, [CHUNK_SIZE] requests in flight at a time. Coroutines are
     * made one chunk at a time on purpose: one `async {}` per id up front made 53k coroutines for a
     * 53k-channel catalogue, with every response held at once, enough GC pressure to stall video
     * into an ANR.
     *
     * Uses [fetchEpgDirect], without [getEpgForStream]'s overhead per call. The API service is
     * resolved once.
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
                        cacheEpgBatch(fetched.toMap())
                        remeasure(fetched.map { it.second })
                    }
                }
                if (!measuredThisRun) remeasure(results.values)
                results.mapValues { (_, epg) -> shown(epg) }
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Continue on failure - EPG may not be available for all channels
            null
        }
    }

    /**
     * [epg] as the guide should show it: read as the panel's wall time when this panel labels it as
     * UTC, and with overlapping programmes ended at the next start ([withOverlapsClipped]).
     */
    private fun shown(epg: EpgResponse): EpgResponse =
        (if (labelsLocalAsUtc) PanelGuideTimes.corrected(epg, sessionManager.panelClock.zone) else epg).withOverlapsClipped()

    /**
     * Compare [fresh] answers with the source's own `xmltv.php` and keep the verdict when there is
     * one ([PanelGuideTimes.labelsLocalAsUtc]); off the caller's thread. See the panel guide local
     * time plan.
     */
    private fun remeasure(fresh: Collection<EpgResponse>) {
        val own = ownXmltv ?: return
        val programs = fresh.flatMap { it.listings }.filter { !it.channelId.isNullOrBlank() && it.startTime > 0 }
        if (programs.size < PanelGuideTimes.MIN_MATCHES) return
        val zone = sessionManager.panelClock.zone
        writeScope.launch {
            try {
                val panel = programs.map { PanelGuideTimes.listing(it.channelId!!, it.title, it.startTime) }
                val index =
                    own.listings(
                        programs.mapTo(HashSet()) { it.channelId!! },
                        panel.minOf { it.startSec } - REMEASURE_MARGIN_SEC,
                        panel.maxOf { it.startSec } + REMEASURE_MARGIN_SEC,
                    ) ?: return@launch
                val verdict = PanelGuideTimes.labelsLocalAsUtc(panel, index, zone) ?: return@launch
                measuredThisRun = true
                if (verdict != labelsLocalAsUtc) {
                    labelsLocalAsUtc = verdict
                    sharedPreferences.edit { putBoolean(KEY_PANEL_GUIDE_LOCAL_AS_UTC, verdict) }
                    Log.i(TAG, "Provider $providerId: panel guide labels local time ($zone) as UTC = $verdict")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Comparing the panel guide with its xmltv.php failed", e)
            }
        }
    }

    private fun getCachedEpg(streamId: Int): EpgResponse? {
        val payload = epgCacheDao.getFreshPayload(providerId, streamId, freshnessCutoff()) ?: return null
        return decode(payload)
    }

    /** Batched [getCachedEpg] — one query per chunk instead of one per stream. */
    private fun getCachedEpgBatch(streamIds: List<Int>): Map<Int, EpgResponse> {
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
            // cancellation-ok: non-suspend
            null
        }

    private fun cacheEpg(
        streamId: Int,
        epg: EpgResponse,
    ) {
        cacheEpgBatch(mapOf(streamId to epg))
    }

    private fun cacheEpgBatch(entries: Map<Int, EpgResponse>) {
        if (entries.isEmpty()) return
        val now = System.currentTimeMillis()
        val rows =
            entries.mapNotNull { (streamId, epg) ->
                val payload = json.encodeToString(epg)
                // A few channels have huge listings (one 1.7 MB blob; the 634 rows over this cap held
                // ~236 MiB). They are no cheaper to parse from disk than to refetch: over the cap,
                // no write, and the next read goes to the network.
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

    fun clearEpgCache(streamId: Int) {
        writeScope.launch { epgCacheDao.deleteStream(providerId, streamId) }
    }

    fun clearAllEpgCache() {
        writeScope.launch { epgCacheDao.deleteAll(providerId) }
    }

    private companion object {
        const val TAG = "XtreamEpgManager"

        /** Guards [evictStaleCache]; the sweep is process-wide, not per provider. */
        val staleEvictionDone =
            java.util.concurrent.atomic
                .AtomicBoolean(false)

        /** Rows per sweep commit — small enough that `auto_vacuum = FULL` page movement stays bounded. */
        const val STALE_SWEEP_BATCH = 400

        /** Breather between sweep commits so a large backlog does not monopolise the database. */
        const val STALE_SWEEP_PAUSE_MS = 50L

        /** Cache ceiling for one channel's listings; see [cacheEpgBatch]. */
        const val MAX_CACHED_PAYLOAD_CHARS = 64 * 1024

        /** Requests in flight per chunk. */
        const val CHUNK_SIZE = 10

        /** Index programmes fetched beyond the panel listings' starts: wider than any panel offset. */
        const val REMEASURE_MARGIN_SEC = 24 * 60 * 60L
    }
}
