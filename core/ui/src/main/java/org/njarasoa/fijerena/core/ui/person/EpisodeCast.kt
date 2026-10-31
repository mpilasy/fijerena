package org.njarasoa.fijerena.core.ui.person

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.suspendRunCatching
import org.njarasoa.fijerena.core.network.tmdb.CastMember
import org.njarasoa.fijerena.core.network.tmdb.EpisodeCredits
import org.njarasoa.fijerena.core.network.tmdb.TitleCredits
import org.njarasoa.fijerena.core.network.tmdb.episodeCredits
import org.njarasoa.fijerena.core.player.domain.EpisodeItem

/**
 * The cast section of [episode]'s details (docs/plans/20261030_episode-cast-plan.md, EC-D1): its
 * guest stars, from the TMDB season call the plots use, and the show's cast ([show], the Cast
 * tab's), apart. Null until the guest stars are known, so the cards do not move when they come, and null
 * when TMDB has nobody: the screen then shows the source's cast names or nothing.
 */
@Composable
fun rememberEpisodeCredits(
    mediaRepository: MediaRepository,
    showTmdbId: String?,
    episode: EpisodeItem,
    show: TitleCredits?,
): EpisodeCredits? {
    val season = episode.seasonNumber
    val guests by produceState<List<CastMember>?>(null, mediaRepository, showTmdbId, episode.id) {
        value =
            season
                ?.let { suspendRunCatching { mediaRepository.getTmdbEpisodeGuests(showTmdbId, it, episode.episodeNumber) }.getOrNull() }
                .orEmpty()
    }
    return remember(guests, show) { guests?.let { episodeCredits(it, show) } }
}
