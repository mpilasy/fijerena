package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.AppSettings

/**
 * Main orchestrator for auto-detecting iptv-org EPG guides from user's Live TV channels.
 *
 * Pipeline:
 * 1. Fetch + cache channels.json & guides.json from iptv-org API (7-day TTL)
 * 2. Match user's channel IDs/names against iptv-org channels
 * 3. Select optimal per-site guide files via greedy set-cover
 * 4. Hand URLs to EpgFileManager for download + merge
 *
 * Resolved guide URLs are persisted in SharedPreferences to survive app restarts.
 */
class IptvOrgGuideResolver private constructor(private val context: Context) {

    companion object {
        private const val TAG = "IptvOrgGuideResolver"
        private const val PREFS_NAME = "iptv_org_resolver"
        private const val KEY_RESOLVED_GUIDES = "resolved_guides"
        private const val KEY_RESOLVE_TIMESTAMP = "resolve_timestamp"
        private const val KEY_MATCH_COUNT = "match_count"
        private const val KEY_TOTAL_CHANNELS = "total_channels"
        private const val RESOLVE_TTL_MS = 24L * 60 * 60 * 1000 // Re-resolve every 24h

        @Volatile
        private var instance: IptvOrgGuideResolver? = null

        fun getInstance(context: Context): IptvOrgGuideResolver {
            return instance ?: synchronized(this) {
                instance ?: IptvOrgGuideResolver(context.applicationContext).also { instance = it }
            }
        }
    }

    sealed interface ResolverState {
        data object Idle : ResolverState
        data object Resolving : ResolverState
        data class Resolved(
            val guides: List<SelectedGuide>,
            val matchCount: Int,
            val totalChannels: Int
        ) : ResolverState
        data object NoMatch : ResolverState
        data class Failed(val reason: String) : ResolverState
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appSettings = AppSettings(context)
    private val apiCache = IptvOrgApiCache.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    private var resolveJob: Job? = null

    private val _state = MutableStateFlow<ResolverState>(ResolverState.Idle)
    val state: StateFlow<ResolverState> = _state.asStateFlow()

    /**
     * Restore cached resolver state on app startup (does not trigger resolution).
     */
    fun initialize() {
        if (appSettings.epgMode != "auto") return

        val cachedGuides = getCachedGuides()
        if (cachedGuides != null && cachedGuides.isNotEmpty()) {
            val matchCount = prefs.getInt(KEY_MATCH_COUNT, 0)
            val totalChannels = prefs.getInt(KEY_TOTAL_CHANNELS, 0)
            _state.value = ResolverState.Resolved(cachedGuides, matchCount, totalChannels)
            Log.d(TAG, "Restored cached resolution: ${cachedGuides.size} guides, $matchCount/$totalChannels matches")
        }
    }

    /**
     * Run the full auto-detection pipeline with the given user channels.
     * Skips if manual URL override is set.
     */
    fun resolve(channels: List<ChannelRef>) {
        if (appSettings.epgMode != "auto") {
            Log.d(TAG, "EPG mode is manual, skipping auto-detect")
            return
        }

        if (channels.isEmpty()) {
            Log.d(TAG, "No channels provided, nothing to resolve")
            _state.value = ResolverState.NoMatch
            return
        }

        // Check if cached resolution is still fresh
        val resolveTimestamp = prefs.getLong(KEY_RESOLVE_TIMESTAMP, 0L)
        val age = System.currentTimeMillis() - resolveTimestamp
        if (age < RESOLVE_TTL_MS && _state.value is ResolverState.Resolved) {
            Log.d(TAG, "Cached resolution still fresh (${age / 3600000}h old), skipping")
            // Still trigger download in case files need refresh
            triggerDownload()
            return
        }

        resolveJob?.cancel()
        resolveJob = scope.launch {
            doResolve(channels)
        }
    }

    /**
     * Force re-resolution (e.g. when user taps "Re-detect" in settings).
     */
    fun forceResolve(channels: List<ChannelRef>) {
        prefs.edit().remove(KEY_RESOLVE_TIMESTAMP).apply()
        apiCache.invalidateMemoryCache()
        resolveJob?.cancel()
        resolveJob = scope.launch {
            doResolve(channels)
        }
    }

    private suspend fun doResolve(channels: List<ChannelRef>) {
        _state.value = ResolverState.Resolving
        Log.d(TAG, "Starting auto-detection for ${channels.size} channels")

        // Step 1: Fetch iptv-org API data
        val iptvOrgChannels = apiCache.getChannels()
        if (iptvOrgChannels == null) {
            _state.value = ResolverState.Failed("Failed to fetch iptv-org channel database")
            return
        }

        val iptvOrgGuides = apiCache.getGuides()
        if (iptvOrgGuides == null) {
            _state.value = ResolverState.Failed("Failed to fetch iptv-org guides index")
            return
        }

        Log.d(TAG, "iptv-org: ${iptvOrgChannels.size} channels, ${iptvOrgGuides.size} guide entries")

        // Step 2: Match user channels
        val matchResult = IptvOrgChannelMatcher.match(channels, iptvOrgChannels)

        if (matchResult.matchedChannelIds.isEmpty()) {
            _state.value = ResolverState.NoMatch
            clearCachedGuides()
            Log.d(TAG, "No channels matched iptv-org database")
            return
        }

        // Step 3: Select optimal guide files
        val selectedGuides = IptvOrgGuideSelector.select(
            matchedChannelIds = matchResult.matchedChannelIds,
            guides = iptvOrgGuides,
            preferredLang = appSettings.epgPreferredLang
        )

        if (selectedGuides.isEmpty()) {
            _state.value = ResolverState.NoMatch
            clearCachedGuides()
            Log.d(TAG, "No guide files available for matched channels")
            return
        }

        // Step 4: Persist and update state
        saveCachedGuides(selectedGuides)
        prefs.edit()
            .putLong(KEY_RESOLVE_TIMESTAMP, System.currentTimeMillis())
            .putInt(KEY_MATCH_COUNT, matchResult.matchCount)
            .putInt(KEY_TOTAL_CHANNELS, matchResult.totalChannels)
            .apply()

        _state.value = ResolverState.Resolved(
            guides = selectedGuides,
            matchCount = matchResult.matchCount,
            totalChannels = matchResult.totalChannels
        )

        Log.d(TAG, "Resolution complete: ${selectedGuides.size} guides, " +
            "${matchResult.matchCount}/${matchResult.totalChannels} channels covered")

        // Step 5: Trigger download
        triggerDownload()
    }

    private fun triggerDownload() {
        val guides = getCachedGuides() ?: return
        if (guides.isEmpty()) return

        val epgFileManager = EpgFileManager.getInstance(context)
        epgFileManager.downloadAndMergeGuides(guides)
    }

    @Serializable
    private data class CachedGuide(
        val url: String,
        val site: String,
        val lang: String,
        val channelIds: List<String>
    )

    private fun saveCachedGuides(guides: List<SelectedGuide>) {
        val cached = guides.map { CachedGuide(it.url, it.site, it.lang, it.channelIds.toList()) }
        prefs.edit().putString(KEY_RESOLVED_GUIDES, json.encodeToString(cached)).apply()
    }

    fun getCachedGuides(): List<SelectedGuide>? {
        val jsonStr = prefs.getString(KEY_RESOLVED_GUIDES, null) ?: return null
        return try {
            json.decodeFromString<List<CachedGuide>>(jsonStr).map {
                SelectedGuide(it.url, it.site, it.lang, it.channelIds.toSet())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse cached guides", e)
            null
        }
    }

    private fun clearCachedGuides() {
        prefs.edit()
            .remove(KEY_RESOLVED_GUIDES)
            .remove(KEY_RESOLVE_TIMESTAMP)
            .remove(KEY_MATCH_COUNT)
            .remove(KEY_TOTAL_CHANNELS)
            .apply()
    }
}
