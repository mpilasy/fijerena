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
