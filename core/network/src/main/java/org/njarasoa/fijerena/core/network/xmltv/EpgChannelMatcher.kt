package org.njarasoa.fijerena.core.network.xmltv

import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import java.util.concurrent.ConcurrentHashMap

/**
 * Reverse-direction matcher: given all Xtream LIVE streams, builds lookup maps once,
 * then answers match(channelId, channelName) -> EpgBrowserMatchedStream? queries
 * using a 5-level fallback.
 */
class EpgChannelMatcher(
    streams: List<XtreamStreamEntity>,
) {
    // Level 1: exact epgChannelId -> stream
    private val byEpgId = mutableMapOf<String, XtreamStreamEntity>()

    // Level 2: lowercase epgChannelId -> stream
    private val byEpgIdLower = mutableMapOf<String, XtreamStreamEntity>()

    // Level 3: exact stream name -> stream
    private val byName = mutableMapOf<String, XtreamStreamEntity>()

    // Level 4: normalized stream name -> stream
    private val byNormalized = mutableMapOf<String, XtreamStreamEntity>()
    // Level 5: Arrays instead of lists to avoid overhead
    private val normalizedNames: Array<String>
    private val normalizedStreams: Array<XtreamStreamEntity>
    // Cached lengths to avoid jumping to String object headers in hot loops
    private val normalizedNamesLengths: IntArray

    // Memoize slow queries
    private data class MatchKey(val channelId: String, val channelName: String)
    private class MatchResult(val result: EpgBrowserMatchedStream?)
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
        normalizedNamesLengths = IntArray(namesList.size) { namesList[it].length }
    }

    fun match(
        channelId: String,
        channelName: String,
    ): EpgBrowserMatchedStream? {
        val key = MatchKey(channelId, channelName)
        return memoizedMatches.getOrPut(key) {
            MatchResult(doMatch(channelId, channelName))
        }.result
    }

    private fun doMatch(
        channelId: String,
        channelName: String,
    ): EpgBrowserMatchedStream? {
        // 1. Exact epgChannelId == XMLTV channelId
        byEpgId[channelId]?.let { return it.toMatched() }

        // 2. Case-insensitive epgChannelId
        byEpgIdLower[channelId.lowercase()]?.let { return it.toMatched() }

        // 3. Exact stream name == channel name
        byName[channelName]?.let { return it.toMatched() }

        val normalizedChannelName = ChannelNameNormalizer.normalize(channelName)
        if (normalizedChannelName.isEmpty()) return null

        // 4. Normalized name equality
        byNormalized[normalizedChannelName]?.let { return it.toMatched() }

        // 5. Contains match (min 4 chars, pre-filter by length)
        if (normalizedChannelName.length >= 4) {
            val chanLen = normalizedChannelName.length
            val names = normalizedNames
            val streams = normalizedStreams
            val lengths = normalizedNamesLengths
            // Use traditional indexed for loop which compiles to highly optimized JVM bytecode
            for (i in names.indices) {
                val norm = names[i]
                // Optimization: Pre-cached lengths array minimizes CPU cache misses
                // by avoiding repeated memory dereferences to access String headers.
                val normLen = lengths[i]

                // Only check contains when needle ≤ haystack length
                if (chanLen >= normLen) {
                    if (normalizedChannelName.contains(norm)) return streams[i].toMatched()
                } else {
                    if (norm.contains(normalizedChannelName)) return streams[i].toMatched()
                }
            }
        }

        return null
    }

    private fun XtreamStreamEntity.toMatched() =
        EpgBrowserMatchedStream(
            streamId = streamId,
            streamName = name,
            categoryId = categoryId,
        )
}
