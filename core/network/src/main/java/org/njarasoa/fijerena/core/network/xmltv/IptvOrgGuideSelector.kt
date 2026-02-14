package org.njarasoa.fijerena.core.network.xmltv

import android.util.Log

/**
 * Given matched iptv-org channel IDs and the guides index, selects the optimal
 * set of per-site guide files to download using a greedy set-cover algorithm.
 */
object IptvOrgGuideSelector {

    private const val TAG = "IptvOrgGuideSelector"
    private const val MAX_GUIDES = 15

    /**
     * Select the minimum set of guide files that covers the most matched channels.
     *
     * @param matchedChannelIds Set of iptv-org channel IDs that matched user channels
     * @param guides Full list of guide entries from guides.json
     * @param preferredLang Preferred language code (e.g., "en"). Guides in this language
     *                      get priority when coverage is equal.
     * @return List of SelectedGuide to download, capped at [MAX_GUIDES]
     */
    fun select(
        matchedChannelIds: Set<String>,
        guides: List<IptvOrgGuide>,
        preferredLang: String = "en"
    ): List<SelectedGuide> {
        if (matchedChannelIds.isEmpty() || guides.isEmpty()) return emptyList()

        // Filter guides to only those covering matched channels
        val relevantGuides = guides.filter { it.channel in matchedChannelIds }
        if (relevantGuides.isEmpty()) {
            Log.d(TAG, "No guide entries cover any matched channels")
            return emptyList()
        }

        // Group by site+lang (each unique guide file)
        // Key: "site|lang" → set of channel IDs covered
        val guideFiles = mutableMapOf<String, MutableSet<String>>()
        for (guide in relevantGuides) {
            val key = "${guide.site}|${guide.lang}"
            guideFiles.getOrPut(key) { mutableSetOf() }.add(guide.channel)
        }

        Log.d(TAG, "${guideFiles.size} unique guide files cover ${matchedChannelIds.size} channels")

        // Greedy set-cover: pick the guide covering the most uncovered channels
        val uncovered = matchedChannelIds.toMutableSet()
        val selected = mutableListOf<SelectedGuide>()

        while (uncovered.isNotEmpty() && selected.size < MAX_GUIDES) {
            // Find guide file with best coverage of remaining uncovered channels
            val best = guideFiles.maxByOrNull { (key, channelIds) ->
                val coverage = channelIds.count { it in uncovered }
                val lang = key.substringAfter("|")
                // Tie-break: prefer preferred language (add 0.5 for preferred lang)
                coverage.toDouble() + if (lang == preferredLang) 0.5 else 0.0
            } ?: break

            val (key, channelIds) = best
            val coverageCount = channelIds.count { it in uncovered }

            if (coverageCount == 0) break // No more progress possible

            val site = key.substringBefore("|")
            val lang = key.substringAfter("|")
            val url = IptvOrgEpgSource.buildGuideUrl(lang, site)

            selected.add(SelectedGuide(
                url = url,
                site = site,
                lang = lang,
                channelIds = channelIds.toSet()
            ))

            uncovered.removeAll(channelIds)
            guideFiles.remove(key)

            Log.d(TAG, "Selected: $site ($lang) covers $coverageCount channels, ${uncovered.size} remaining")
        }

        Log.d(TAG, "Selected ${selected.size} guide files, " +
            "${matchedChannelIds.size - uncovered.size}/${matchedChannelIds.size} channels covered")

        return selected
    }
}
