package org.njarasoa.fijerena.core.player.domain

data class SeriesDetail(
    val id: String,
    val name: String,
    val metadata: MediaMetadata = MediaMetadata(),
    val coverUrl: String? = null,
    /** The provider's own backdrop art, if it has one (Xtream's `backdrop_path`, Jellyfin's
     * `BackdropImageTags`) — the fallback the TV detail hero uses when TMDB has none. Null for
     * providers with no such concept (SMB, Local, Remote M3U). */
    val backdropUrl: String? = null,
    val seasons: List<SeasonInfo> = emptyList(),
    val episodes: Map<String, List<EpisodeItem>> = emptyMap(),
    /** Read from the device because the source couldn't be reached: the page says so and offers Retry. */
    val isSavedCopy: Boolean = false,
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
 * entry with no actual episodes) dropped. Some Xtream panels send a `seasons`
 * list that's missing an entry for a season the episode map still has
 * episodes under (seen in the wild: same episode data, one alternate stream's
 * `seasons` array just omits the last season) — those are backfilled from the
 * episode map too, so a season with real episodes is never dropped from the
 * accordion, only under-counted.
 */
fun SeriesDetail.sortedSeasons(seasonName: (Int) -> String): List<SeasonInfo> {
    val apiSeasons =
        seasons
            .filter { season -> episodes[season.seasonNumber.toString()]?.isNotEmpty() == true }
            .associateBy { it.seasonNumber }

    val derivedSeasons =
        episodes.entries
            .filter { (_, eps) -> eps.isNotEmpty() }
            .mapNotNull { (key, eps) -> key.toIntOrNull()?.let { it to eps.size } }
            .filter { (num, _) -> num !in apiSeasons }
            .map { (num, count) ->
                SeasonInfo(
                    seasonNumber = num,
                    name = seasonName(num),
                    episodeCount = count,
                )
            }

    return (apiSeasons.values + derivedSeasons).sortedBy { it.seasonNumber }
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
 * This show's episode with the season and episode number in [name]'s `SxxEyy` — how an episode
 * played from another copy of the show (an alternate stream: another language or quality, its own
 * episode ids) is found in this one. Null when [name] carries no `SxxEyy` or this show has no such
 * episode.
 */
fun SeriesDetail.episodeIdMatchingName(name: String): String? {
    val match = SEASON_EPISODE.find(name) ?: return null
    val season = match.groupValues[1].toInt()
    val number = match.groupValues[2].toInt()
    return episodes.entries
        .firstNotNullOfOrNull { (seasonKey, episodes) ->
            episodes.firstOrNull { (it.seasonNumber ?: seasonKey.toIntOrNull()) == season && it.episodeNumber == number }
        }?.id
}

private val SEASON_EPISODE = Regex("""\bS(\d{1,3})\s*E(\d{1,4})\b""", RegexOption.IGNORE_CASE)

/**
 * Episode to land on when the series screen opens: the most recently played one, or the first
 * unwatched episode after it when that one was already finished (nobody comes back to a series
 * to rewatch what they just completed) — see [nextUnwatchedEpisodeId]. Nothing played but some
 * episodes marked watched: the first unwatched episode from the start. Null when nothing here has
 * been played or marked yet.
 *
 * [lastPlayedEpisodeId] is the id with the newest playback timestamp among this series'
 * episodes (a manual mark is not a play); [isCompleted] reports whether an episode was watched
 * past the completion mark or marked watched.
 */
fun SeriesDetail.resumeAnchorEpisodeId(
    sortedSeasons: List<SeasonInfo>,
    lastPlayedEpisodeId: String?,
    isCompleted: (episodeId: String) -> Boolean,
): String? {
    if (lastPlayedEpisodeId != null && !isCompleted(lastPlayedEpisodeId)) return lastPlayedEpisodeId
    val flat = flattenedEpisodes(sortedSeasons).map { it.id }
    return when {
        lastPlayedEpisodeId != null -> nextUnwatchedEpisodeId(flat, lastPlayedEpisodeId, isCompleted) ?: lastPlayedEpisodeId
        flat.any(isCompleted) -> nextUnwatchedEpisodeId(flat, finishedEpisodeId = null, isCompleted)
        else -> null
    }
}

/**
 * The episode to watch after finishing [finishedEpisodeId], from [orderedEpisodeIds] in show order
 * (the watched followups plan → Phase 1): the first one after it that isn't
 * completed; failing that, the first one from the start of the show that isn't; and when every
 * episode is completed, the first episode. [finishedEpisodeId] itself counts as completed; null
 * starts from the beginning. Null when [finishedEpisodeId] isn't in the list, or the list is empty.
 * Shared by the series screens and Home's "Continue watching" card so both offer the same episode.
 */
fun nextUnwatchedEpisodeId(
    orderedEpisodeIds: List<String>,
    finishedEpisodeId: String?,
    isCompleted: (episodeId: String) -> Boolean,
): String? {
    val start =
        if (finishedEpisodeId == null) {
            0
        } else {
            orderedEpisodeIds.indexOf(finishedEpisodeId).takeIf { it >= 0 }?.plus(1) ?: return null
        }
    val unwatched = { id: String -> id != finishedEpisodeId && !isCompleted(id) }
    return orderedEpisodeIds.subList(start, orderedEpisodeIds.size).firstOrNull(unwatched)
        ?: orderedEpisodeIds.firstOrNull(unwatched)
        ?: orderedEpisodeIds.firstOrNull()
}

/**
 * Returns formatted start and end years (e.g. "2008–2013", "2024–present", or "2021") for a series
 * based on the series release year and its episodes' air dates.
 */
fun SeriesDetail.seriesYearRange(presentLabel: String = "present"): String? {
    val startYear = metadata.year ?: metadata.releaseDate?.take(4)?.toIntOrNull()
    val episodeYears =
        episodes.values
            .flatten()
            .mapNotNull {
                it.metadata.year ?: it.metadata.airDate
                    ?.take(4)
                    ?.toIntOrNull()
            }

    val minYear = startYear ?: episodeYears.minOrNull()
    val maxEpisodeYear = episodeYears.maxOrNull()

    return when {
        minYear == null -> {
            null
        }

        maxEpisodeYear == null || minYear == maxEpisodeYear -> {
            "$minYear"
        }

        else -> {
            val currentYear =
                java.util.Calendar
                    .getInstance()
                    .get(java.util.Calendar.YEAR)
            if (maxEpisodeYear >= currentYear) {
                "$minYear–$presentLabel"
            } else {
                "$minYear–$maxEpisodeYear"
            }
        }
    }
}
