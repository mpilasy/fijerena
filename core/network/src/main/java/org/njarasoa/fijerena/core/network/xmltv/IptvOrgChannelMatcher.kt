package org.njarasoa.fijerena.core.network.xmltv

import android.util.Log

/**
 * Pure logic for matching user channels against the iptv-org channel database.
 *
 * Two-phase matching:
 * 1. Direct epgChannelId match against iptv-org channel.id (exact, then case-insensitive)
 * 2. Normalized name matching against channel.name + channel.alt_names
 */
object IptvOrgChannelMatcher {

    private const val TAG = "IptvOrgChannelMatcher"

    data class MatchResult(
        val matchedChannelIds: Set<String>,
        val matchCount: Int,
        val totalChannels: Int
    )

    fun match(
        userChannels: List<ChannelRef>,
        iptvOrgChannels: List<IptvOrgChannel>
    ): MatchResult {
        if (userChannels.isEmpty() || iptvOrgChannels.isEmpty()) {
            return MatchResult(emptySet(), 0, userChannels.size)
        }

        val matched = mutableSetOf<String>()

        // Build lookup maps for iptv-org channels
        val byIdExact = iptvOrgChannels.associateBy { it.id }
        val byIdLower = iptvOrgChannels.associateBy { it.id.lowercase() }

        // Build normalized name → channel ID multimap (name + alt_names)
        val byNormalizedName = mutableMapOf<String, MutableList<String>>()
        for (ch in iptvOrgChannels) {
            val names = mutableListOf(ch.name) + ch.altNames
            for (name in names) {
                val normalized = normalizeName(name)
                if (normalized.isNotEmpty()) {
                    byNormalizedName.getOrPut(normalized) { mutableListOf() }.add(ch.id)
                }
            }
        }

        // Phase 1: Match by epgChannelId
        for (ref in userChannels) {
            val epgId = ref.epgChannelId
            if (epgId.isNullOrBlank()) continue

            // Exact match
            val exactMatch = byIdExact[epgId]
            if (exactMatch != null) {
                matched.add(exactMatch.id)
                continue
            }

            // Case-insensitive match
            val lowerMatch = byIdLower[epgId.lowercase()]
            if (lowerMatch != null) {
                matched.add(lowerMatch.id)
            }
        }

        val phase1Count = matched.size
        Log.d(TAG, "Phase 1 (ID match): $phase1Count channels matched")

        // Phase 2: Normalized name matching for unmatched channels
        val unmatchedRefs = userChannels.filter { ref ->
            val epgId = ref.epgChannelId
            if (epgId.isNullOrBlank()) return@filter true
            // Check if this ref's ID was already matched
            !matched.contains(byIdExact[epgId]?.id) &&
                !matched.contains(byIdLower[epgId.lowercase()]?.id)
        }

        for (ref in unmatchedRefs) {
            val normalized = normalizeName(ref.name)
            if (normalized.isEmpty()) continue

            val candidates = byNormalizedName[normalized]
            if (candidates != null) {
                matched.addAll(candidates)
            }
        }

        Log.d(TAG, "Phase 2 (name match): ${matched.size - phase1Count} additional channels matched")
        Log.d(TAG, "Total: ${matched.size}/${userChannels.size} channels matched")

        return MatchResult(
            matchedChannelIds = matched,
            matchCount = matched.size,
            totalChannels = userChannels.size
        )
    }

    /**
     * Normalize a channel name for fuzzy matching:
     * - Lowercase
     * - Strip non-alphanumeric (keep spaces)
     * - Collapse whitespace
     * - Trim
     */
    private fun normalizeName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
