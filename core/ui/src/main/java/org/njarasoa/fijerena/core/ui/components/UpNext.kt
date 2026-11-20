package org.njarasoa.fijerena.core.ui.components

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.playerEpisodeName
import org.njarasoa.fijerena.core.player.domain.watchedTailMs
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation

// "Up next" card (shown while "Play next episode automatically" is on, per profile). Near the end
// of an episode the card offers the next one over the playing video, with Watch now focused. When
// the episode ends with no answer, the next one plays on its own, at most
// [UP_NEXT_MAX_UNANSWERED] times in a row: then the player closes as after a cancel, so a show does
// not play all night to nobody. Any key or touch in the player starts the count again. The card's
// countdown is the playback time left, so it stops with the video (paused, buffering, app in the
// background).

/** The earliest the card appears before the end: 4 min, however long the episode. */
const val UP_NEXT_MAX_LEAD_MS = 4 * 60_000L

/** Episodes in a row that start on their own with no key or touch from the viewer, before the player stops. */
const val UP_NEXT_MAX_UNANSWERED = 4

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

/**
 * The episode to play when one ends on its own, or null to close the player (back to the episode
 * list): the setting is on, the provider allows it, there is a next episode, the card wasn't
 * cancelled, and fewer than [UP_NEXT_MAX_UNANSWERED] episodes in a row started on their own
 * ([unansweredInARow]).
 */
fun upNextOnEnd(
    autoplayEnabled: Boolean,
    providerSupportsAutoplay: Boolean,
    nextEpisode: EpisodeItem?,
    dismissed: Boolean,
    unansweredInARow: Int,
): EpisodeItem? =
    if (autoplayEnabled && providerSupportsAutoplay && !dismissed && unansweredInARow < UP_NEXT_MAX_UNANSWERED) nextEpisode else null

/** Suspends until the screen is at least STARTED: the next episode never starts unseen. */
suspend fun Lifecycle.awaitStarted() {
    currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
}

/** "S2:E3" for the compact up-next card; "E3" when the season is unknown. */
fun upNextCode(episode: EpisodeItem): String = episode.seasonNumber?.let { "S$it:E${episode.episodeNumber}" } ?: "E${episode.episodeNumber}"

/** The episode's own title for the card's details panel ([playerEpisodeName]); null when it has none. */
fun upNextTitle(episode: EpisodeItem): String? = playerEpisodeName(episode.title).takeIf { it.isNotBlank() }

/** The episode's synopsis for the card's details panel; null when the provider gave none. */
fun upNextPlot(episode: EpisodeItem): String? =
    episode.metadata.plot
        ?.trim()
        ?.takeIf { it.isNotBlank() }
