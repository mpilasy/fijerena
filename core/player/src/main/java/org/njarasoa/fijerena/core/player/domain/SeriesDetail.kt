package org.njarasoa.fijerena.core.player.domain

data class SeriesDetail(
    val id: String,
    val name: String,
    val metadata: MediaMetadata = MediaMetadata(),
    val coverUrl: String? = null,
    val seasons: List<SeasonInfo> = emptyList(),
    val episodes: Map<String, List<EpisodeItem>> = emptyMap(),
)

data class SeasonInfo(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int? = null,
    val coverUrl: String? = null,
)

data class EpisodeItem(
    val id: String,
    val episodeNumber: Int,
    val title: String,
    val seasonNumber: Int? = null,
    val extension: String? = null,
    val metadata: MediaMetadata = MediaMetadata(),
    val thumbnailUrl: String? = null,
)

/**
 * Seasons to display, sorted and with empty ones (e.g. a "Season 0" specials
 * entry with no actual episodes) dropped. Falls back to deriving seasons from
 * the episode map's keys when the API didn't provide a season list.
 */
fun SeriesDetail.sortedSeasons(seasonName: (Int) -> String): List<SeasonInfo> {
    val apiSeasons =
        seasons
            .filter { season -> episodes[season.seasonNumber.toString()]?.isNotEmpty() == true }
            .sortedBy { it.seasonNumber }
    if (apiSeasons.isNotEmpty()) return apiSeasons

    return episodes.entries
        .filter { (_, eps) -> eps.isNotEmpty() }
        .mapNotNull { (key, eps) -> key.toIntOrNull()?.let { it to eps.size } }
        .sortedBy { (num, _) -> num }
        .map { (num, count) ->
            SeasonInfo(
                seasonNumber = num,
                name = seasonName(num),
                episodeCount = count,
            )
        }
}

/** All episodes across [sortedSeasons], each season's episodes sorted by episode number. */
fun SeriesDetail.flattenedEpisodes(sortedSeasons: List<SeasonInfo>): List<EpisodeItem> {
    val result = ArrayList<EpisodeItem>(episodes.values.sumOf { it.size })
    for (season in sortedSeasons) {
        episodes[season.seasonNumber.toString()]?.sortedBy { it.episodeNumber }?.let { result.addAll(it) }
    }
    return result
}

/** The season number containing [episodeId], or null if not found. */
fun SeriesDetail.seasonNumberContaining(episodeId: String): Int? =
    episodes.entries
        .firstOrNull { (_, eps) -> eps.any { it.id == episodeId } }
        ?.key
        ?.toIntOrNull()

/**
 * Which season should be expanded by default in an accordion-style season list:
 * the resume season if known, else the first season when there's more than one,
 * else none.
 */
fun defaultExpandedSeason(
    resumeSeasonNumber: Int?,
    sortedSeasons: List<SeasonInfo>,
): Set<Int> =
    when {
        resumeSeasonNumber != null -> setOf(resumeSeasonNumber)
        sortedSeasons.size > 1 -> setOf(sortedSeasons.first().seasonNumber)
        else -> emptySet()
    }

/**
 * First season (in [sortedSeasons] order) containing an episode for which
 * [isCompleted] returns false — used to auto-expand the season with the next
 * unwatched/in-progress episode.
 */
fun firstSeasonWithUnwatchedEpisode(
    sortedSeasons: List<SeasonInfo>,
    episodesBySeason: Map<String, List<EpisodeItem>>,
    isCompleted: (episodeId: String) -> Boolean,
): Int? {
    for (season in sortedSeasons) {
        val episodes = episodesBySeason[season.seasonNumber.toString()] ?: continue
        for (episode in episodes) {
            if (!isCompleted(episode.id)) return season.seasonNumber
        }
    }
    return null
}

/**
 * Flat-list index of [targetEpisodeId] within an accordion season list (header
 * rows counted when [hasMultipleSeasons]), or null if it isn't in an expanded
 * season.
 */
fun episodeScrollIndex(
    sortedSeasons: List<SeasonInfo>,
    episodesBySeason: Map<String, List<EpisodeItem>>,
    hasMultipleSeasons: Boolean,
    targetEpisodeId: String,
    isExpanded: (seasonNumber: Int) -> Boolean,
): Int? {
    var index = 0
    for (season in sortedSeasons) {
        val seasonEpisodes = episodesBySeason[season.seasonNumber.toString()] ?: emptyList()
        val expanded = !hasMultipleSeasons || isExpanded(season.seasonNumber)
        if (hasMultipleSeasons) index++
        if (expanded) {
            val episodeIndex = seasonEpisodes.indexOfFirst { it.id == targetEpisodeId }
            if (episodeIndex >= 0) return index + episodeIndex
            index += seasonEpisodes.size
        }
    }
    return null
}
