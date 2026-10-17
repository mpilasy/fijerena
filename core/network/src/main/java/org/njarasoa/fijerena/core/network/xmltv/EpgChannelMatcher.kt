package org.njarasoa.fijerena.core.network.xmltv

import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import java.util.concurrent.ConcurrentHashMap

/**
 * Reverse-direction matcher: given all Xtream LIVE streams of one source ([providerId]), builds
 * lookup maps once, then answers match(channelId, channelName) -> EpgBrowserMatchedStream? queries
 * using a 5-level fallback.
 */
class EpgChannelMatcher(
    streams: List<XtreamStreamEntity>,
    private val providerId: Long = 0L,
) {
    companion object {
        // One matcher per source: Search the guide matches each airing on its own source's
        // channels, so the profile's sources are all in use at once (multi-source plan, Phase 7).
        private val cache = HashMap<Long, EpgChannelMatcher>()

        @Synchronized
        fun getOrCreate(
            providerId: Long,
            fetchStreams: () -> List<XtreamStreamEntity>,
        ): EpgChannelMatcher = cache.getOrPut(providerId) { EpgChannelMatcher(fetchStreams(), providerId) }

        /**
         * Explicitly clear the cache (e.g., when a provider's streams are refreshed).
         */
        @Synchronized
        fun clearCache() {
            cache.clear()
        }

        /**
         * Proactively populates the cache for a specific provider.
         * Useful after a background sync to avoid I/O on the next user search.
         */
        @Synchronized
        fun warmCache(
            providerId: Long,
            streams: List<XtreamStreamEntity>,
        ) {
            cache[providerId] = EpgChannelMatcher(streams, providerId)
        }

        // Bounds native/JVM heap for very large or noisy XMLTV guides (many distinct channelId/
        // channelName combinations). A full clear rather than real LRU eviction: hitting this cap
        // is rare, entries are cheap to recompute, and it stays safe under concurrent access
        // without hand-rolling a thread-safe LRU.
        private const val MAX_MEMOIZED_MATCHES = 5_000
    }

    private val byEpgId = mutableMapOf<String, XtreamStreamEntity>()

    private val byEpgIdLower = mutableMapOf<String, XtreamStreamEntity>()

    private val byName = mutableMapOf<String, XtreamStreamEntity>()

    private val byNormalized = mutableMapOf<String, XtreamStreamEntity>()

    // Level 5: Arrays instead of lists to avoid overhead
    private val normalizedNames: Array<String>
    private val normalizedStreams: Array<XtreamStreamEntity>

    // Memoize slow queries
    private data class MatchKey(
        val channelId: String,
        val channelName: String,
    )

    private class MatchResult(
        val result: EpgBrowserMatchedStream?,
    )

    private val memoizedMatches = ConcurrentHashMap<MatchKey, MatchResult>()

    init {
        val namesList = ArrayList<String>(streams.size)
        val streamsList = ArrayList<XtreamStreamEntity>(streams.size)

        for (stream in streams) {
            val epgId = stream.epgChannelId
            if (!epgId.isNullOrBlank()) {
                byEpgId.putIfAbsent(epgId, stream)
                byEpgIdLower.putIfAbsent(epgId.lowercase(), stream)
            }
            // A separator row ("#### ΑΘΛΗΤΙΚΑ/SPORTS ####") is no channel: by name its normalized
            // "sports" was contained in "skysportsracing". It still matches by guide id above.
            if (stream.name.trimStart().startsWith("#")) continue
            byName.putIfAbsent(stream.name, stream)
            val norm = ChannelNameNormalizer.normalize(stream.name)
            if (norm.isNotEmpty()) {
                byNormalized.putIfAbsent(norm, stream)
                if (norm.length >= 4) {
                    namesList.add(norm)
                    streamsList.add(stream)
                }
            }
        }

        normalizedNames = namesList.toTypedArray()
        normalizedStreams = streamsList.toTypedArray()
    }

    fun match(
        channelId: String,
        channelName: String,
    ): EpgBrowserMatchedStream? {
        val key = MatchKey(channelId, channelName)
        if (memoizedMatches.size >= MAX_MEMOIZED_MATCHES && !memoizedMatches.containsKey(key)) {
            memoizedMatches.clear()
        }
        return memoizedMatches
            .getOrPut(key) {
                MatchResult(doMatch(channelId, channelName))
            }.result
    }

    private fun doMatch(
        channelId: String,
        channelName: String,
    ): EpgBrowserMatchedStream? {
        val normalizedChannelName = ChannelNameNormalizer.normalize(channelName)

        // 5. Contains match (min 4 chars, pre-filter by length)
        val containsMatch =
            if (normalizedChannelName.length >= 4) {
                val chanLen = normalizedChannelName.length
                var found: XtreamStreamEntity? = null
                // Use traditional indexed for loop which compiles to highly optimized JVM bytecode
                for (i in normalizedNames.indices) {
                    val norm = normalizedNames[i]
                    val normLen = norm.length
                    // Only check contains when needle ≤ haystack length
                    val isMatch =
                        if (chanLen >= normLen) normalizedChannelName.contains(norm) else norm.contains(normalizedChannelName)
                    if (isMatch) {
                        found = normalizedStreams[i]
                        break
                    }
                }
                found
            } else {
                null
            }

        val matchedStream =
            // 1. Exact epgChannelId == XMLTV channelId
            byEpgId[channelId]
                // 2. Case-insensitive epgChannelId
                ?: byEpgIdLower[channelId.lowercase()]
                // 3. Exact stream name == channel name
                ?: byName[channelName]
                // 4. Normalized name equality
                ?: (if (normalizedChannelName.isNotEmpty()) byNormalized[normalizedChannelName] else null)
                ?: containsMatch

        return matchedStream?.toMatched()
    }

    private fun XtreamStreamEntity.toMatched() =
        EpgBrowserMatchedStream(
            streamId = streamId,
            streamName = name,
            categoryId = categoryId,
            excluded = excluded,
            providerId = providerId,
        )
}
