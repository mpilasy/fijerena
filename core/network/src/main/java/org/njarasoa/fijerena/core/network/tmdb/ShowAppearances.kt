package org.njarasoa.fijerena.core.network.tmdb

/**
 * One line of a person's appearances in a show (docs/plans/20261030_episode-cast-plan.md, EC-D2):
 * an episode ([episode] set, "S9E17 · Authority") for a guest or recurring role, or a whole season
 * ([episode] null, "Season 3") for a regular role. [episodeId] is the source's episode, null when
 * the source does not have it or for a season. [inSource]: whether the source has the episode or
 * the season.
 */
data class ShowAppearance(
    val season: Int,
    val episode: Int? = null,
    val name: String? = null,
    val episodeId: String? = null,
    val inSource: Boolean = true,
)

/**
 * Where a [ShowAppearance] opens the show's page: on the source's episode [episodeId], else on
 * [season]. [missing] is the appearance the source does not have, for the note "S9E17 is not in
 * your source" (EC-D3); null when the source has it.
 */
data class ShowTarget(
    val episodeId: String? = null,
    val season: Int? = null,
    val missing: ShowAppearance? = null,
)

/**
 * The lines of [credit], a person's credit in a show: every episode of a guest or recurring role in
 * air order; else, for a regular role (TMDB names no episode there), every season, the specials
 * (season 0) last. [sourceEpisodes] is the source's episode id by season and episode number:
 * each line is matched to it, and [sourceSeasons] (the seasons the source has) marks the seasons.
 */
fun showAppearances(
    credit: TmdbCredit,
    sourceEpisodes: Map<Pair<Int, Int>, String>,
    sourceSeasons: Set<Int> = sourceEpisodes.keys.mapTo(mutableSetOf()) { it.first },
): List<ShowAppearance> {
    val episodes =
        credit.media.episodes
            .mapNotNull { episode ->
                val season = episode.seasonNumber ?: return@mapNotNull null
                val number = episode.episodeNumber ?: return@mapNotNull null
                val id = sourceEpisodes[season to number]
                ShowAppearance(season, number, episode.name?.takeIf { it.isNotBlank() }, id, inSource = id != null)
            }.distinctBy { it.season to it.episode }
            .sortedWith(compareBy({ it.season }, { it.episode }))
    return episodes.ifEmpty {
        credit.media.seasons
            .mapNotNull { it.seasonNumber }
            .distinct()
            .sortedWith(compareBy({ it == SPECIALS }, { it }))
            .map { season -> ShowAppearance(season, inSource = season in sourceSeasons) }
    }
}

/**
 * Where [appearance] opens the show (EC-D2, EC-D3): the episode when the source has it; else the
 * show on that season when the source has the season; else the show as it opens. A line the
 * source lacks gives the note.
 */
fun showTarget(
    appearance: ShowAppearance,
    sourceSeasons: Set<Int>,
): ShowTarget =
    when {
        appearance.episodeId != null -> ShowTarget(episodeId = appearance.episodeId, season = appearance.season)
        appearance.inSource -> ShowTarget(season = appearance.season)
        else -> ShowTarget(season = appearance.season.takeIf { it in sourceSeasons }, missing = appearance)
    }

/** TMDB's season of a show's specials. */
const val SPECIALS = 0
