package org.njarasoa.fijerena.core.network.xmltv

import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity

/**
 * Reverse-direction matcher: given all Xtream LIVE streams, builds lookup maps once,
 * then answers match(channelId, channelName) -> EpgBrowserMatchedStream? queries
 * using a 5-level fallback.
 */
class EpgChannelMatcher(streams: List<XtreamStreamEntity>) {

    // Level 1: exact epgChannelId -> stream
    private val byEpgId = mutableMapOf<String, XtreamStreamEntity>()
    // Level 2: lowercase epgChannelId -> stream
    private val byEpgIdLower = mutableMapOf<String, XtreamStreamEntity>()
    // Level 3: exact stream name -> stream
    private val byName = mutableMapOf<String, XtreamStreamEntity>()
    // Level 4: normalized stream name -> stream
    private val byNormalized = mutableMapOf<String, XtreamStreamEntity>()
    // Level 5: (normalized name, stream) pairs for contains matching
    private val normalizedEntries = mutableListOf<Pair<String, XtreamStreamEntity>>()

    init {
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
                normalizedEntries.add(norm to stream)
            }
        }
    }

    fun match(channelId: String, channelName: String): EpgBrowserMatchedStream? {
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
            for ((norm, stream) in normalizedEntries) {
                if (norm.length < 4) continue
                // Only check contains when needle ≤ haystack length
                if (chanLen >= norm.length && normalizedChannelName.contains(norm)) return stream.toMatched()
                if (norm.length >= chanLen && norm.contains(normalizedChannelName)) return stream.toMatched()
            }
        }

        return null
    }

    private fun XtreamStreamEntity.toMatched() = EpgBrowserMatchedStream(
        streamId = streamId,
        streamName = name,
        categoryId = categoryId
    )
}
