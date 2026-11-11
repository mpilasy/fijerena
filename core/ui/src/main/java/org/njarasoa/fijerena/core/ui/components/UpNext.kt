package org.njarasoa.fijerena.core.ui.components

import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.playerEpisodeName
import org.njarasoa.fijerena.core.player.domain.watchedTailMs
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation

// "Up next" card (shown while "Play next episode automatically" is on, per profile). Near the end
// of an episode the card offers the next one over the playing video, with Watch now focused. Only
// a press plays it: when the episode ends with no answer, the player closes as after a cancel. The
// card's countdown is the playback time left, so it stops with the video (paused, buffering, app in
// the background).

/** The earliest the card appears before the end: 4 min, however long the episode. */
const val UP_NEXT_MAX_LEAD_MS = 4 * 60_000L

/**
 * How much playback is left when the card appears: where the episode becomes watched
 * ([watchedTailMs]), at most [UP_NEXT_MAX_LEAD_MS] — 4 min for a 47-minute episode, 2 min 38 s for a
 * 22-minute one, 90 s for a 5-minute one. 0 for an unknown duration: no card before the end.
 */
fun upNextLeadMs(durationMs: Long): Long = minOf(watchedTailMs(durationMs), UP_NEXT_MAX_LEAD_MS)

/**
 * Whether the "Up next" card shows: the setting is on, the provider allows it
 * ([providerSupportsAutoplay] — Xtream and Jellyfin), the player knows a next episode (the one
 * its Next button plays — movies and Live TV never have one), the viewer hasn't cancelled it for
 * this episode ([dismissed]), the duration is known, and at most [upNextLeadMs] of playback is
 * left.
 */
fun showUpNext(
    autoplayEnabled: Boolean,
    providerSupportsAutoplay: Boolean,
    nextEpisode: EpisodeItem?,
    positionMs: Long,
    durationMs: Long,
    dismissed: Boolean,
): Boolean =
    autoplayEnabled &&
        providerSupportsAutoplay &&
        nextEpisode != null &&
        !dismissed &&
        durationMs > 0L &&
        durationMs - positionMs <= upNextLeadMs(durationMs)

/** Whole seconds of playback left, rounded up — the card's countdown ([upNextCountdown]). */
fun upNextSecondsLeft(
    positionMs: Long,
    durationMs: Long,
): Int {
    val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
    return ((remainingMs + CinemaAnimation.countdownTickMs - 1) / CinemaAnimation.countdownTickMs).toInt()
}

/** [secondsLeft] as the card shows it, minutes and seconds: "5:38", "0:09". */
fun upNextCountdown(secondsLeft: Int): String = "%d:%02d".format(secondsLeft / 60, secondsLeft % 60)

/** "S2:E3" for the compact up-next card; "E3" when the season is unknown. */
fun upNextCode(episode: EpisodeItem): String = episode.seasonNumber?.let { "S$it:E${episode.episodeNumber}" } ?: "E${episode.episodeNumber}"

/** The episode's own title for the card's details panel ([playerEpisodeName]); null when it has none. */
fun upNextTitle(episode: EpisodeItem): String? = playerEpisodeName(episode.title).takeIf { it.isNotBlank() }

/** The episode's synopsis for the card's details panel; null when the provider gave none. */
fun upNextPlot(episode: EpisodeItem): String? =
    episode.metadata.plot
        ?.trim()
        ?.takeIf { it.isNotBlank() }
