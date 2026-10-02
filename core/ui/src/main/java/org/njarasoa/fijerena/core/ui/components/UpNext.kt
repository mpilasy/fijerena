package org.njarasoa.fijerena.core.ui.components

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation

// Autoplay next episode ("Play next episode automatically", per profile). Near the end of an
// episode an "Up next" card is offered over the playing video; when the episode ends with the
// card not cancelled, the next one plays at once. The card's countdown is the playback time left,
// so it stops with the video (paused, buffering, app in the background).

/** Share of an episode's length the card may cover, so a short episode isn't half "Up next". */
const val UP_NEXT_LEAD_FRACTION = 0.15

/**
 * How much playback is left when the card appears: [CinemaAnimation.upNextLeadMs] (90 s), or
 * [UP_NEXT_LEAD_FRACTION] of [durationMs] when that is shorter — 90 s for a 22-minute episode,
 * 45 s for a 5-minute one. 0 for an unknown duration: no card before the end.
 */
fun upNextLeadMs(durationMs: Long): Long =
    if (durationMs > 0L) minOf(CinemaAnimation.upNextLeadMs, (durationMs * UP_NEXT_LEAD_FRACTION).toLong()) else 0L

/**
 * Whether the "Up next" card shows: the setting is on, the provider allows it
 * ([providerSupportsAutoplay] — Xtream, not Jellyfin), the player knows a next episode (the one
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

/** Whole seconds of playback left, rounded up — the card's "Starts in N s". */
fun upNextSecondsLeft(
    positionMs: Long,
    durationMs: Long,
): Int {
    val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
    return ((remainingMs + CinemaAnimation.countdownTickMs - 1) / CinemaAnimation.countdownTickMs).toInt()
}

/**
 * The episode to play when one ends on its own, or null to leave the player as before (back to
 * the episode list): the setting is on, the provider allows it, there is a next episode, and the
 * card wasn't cancelled.
 */
fun upNextOnEnd(
    autoplayEnabled: Boolean,
    providerSupportsAutoplay: Boolean,
    nextEpisode: EpisodeItem?,
    dismissed: Boolean,
): EpisodeItem? = if (autoplayEnabled && providerSupportsAutoplay && !dismissed) nextEpisode else null

/** "S2:E3 · Title" for the up-next card; just the title when the season is unknown. */
fun upNextLabel(episode: EpisodeItem): String =
    episode.seasonNumber?.let { "S$it:E${episode.episodeNumber} · ${episode.title}" } ?: episode.title

/** Suspends until the screen is at least STARTED — the next episode never starts unseen. */
suspend fun Lifecycle.awaitStarted() {
    currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
}
