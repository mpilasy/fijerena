package org.njarasoa.fijerena.core.network

import org.njarasoa.fijerena.core.network.xtream.db.NewEpisodeEntity
import org.njarasoa.fijerena.core.player.domain.MediaItem

/**
 * What one source knows about the new episodes of the profile in use (see the new episode alerts
 * plan and `NewEpisodes.forProfile` in core:ui): the rows of the last 30 days, the shows with a row
 * that the profile may see, and which episodes the profile has started or finished.
 */
data class NewEpisodeFacts(
    val rows: List<NewEpisodeEntity>,
    val shows: List<Show>,
    /** Episode ids that the profile started or finished (or finished on a TMDB sibling). */
    val seenEpisodeIds: Set<String>,
) {
    /**
     * One show with rows. [followedSince] is when the profile followed it (favourite, or first
     * play kept), null when it does not follow it; [newSeasons] the seasons whose stored episodes
     * are all new.
     */
    data class Show(
        val item: MediaItem,
        val followedSince: Long?,
        val newSeasons: Set<Int?>,
    )
}
