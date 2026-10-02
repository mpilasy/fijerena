package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation

/**
 * The episode the player counts down to when a title ends on its own, or null to leave the
 * player as before. Only with the active profile's "Play next episode automatically" on, and only
 * when the player knows a next episode (the one its Next button plays) — movies and Live TV never
 * have one.
 */
fun upNextOnEnd(
    autoplayEnabled: Boolean,
    nextEpisode: EpisodeItem?,
): EpisodeItem? = if (autoplayEnabled) nextEpisode else null

/** "S2:E3 · Title" for the up-next card; just the title when the season is unknown. */
fun upNextLabel(episode: EpisodeItem): String =
    episode.seasonNumber?.let { "S$it:E${episode.episodeNumber} · ${episode.title}" } ?: episode.title

/**
 * Seconds left of the "Up next" countdown for [episode], counting down from
 * [CinemaAnimation.upNextCountdownMs]; calls [onElapsed] once it reaches zero. Leaving the
 * composition (Play now, Cancel, Back) stops it. Counts only while the screen is at least
 * STARTED: in the background it pauses (a tick that ends there doesn't count) and resumes on
 * return, so the next episode never starts unseen. Picture-in-picture is still STARTED, so a
 * phone in PiP keeps counting — the viewer is still watching.
 */
@Composable
fun rememberUpNextCountdown(
    episode: EpisodeItem,
    onElapsed: () -> Unit,
): Int {
    val totalSeconds = (CinemaAnimation.upNextCountdownMs / CinemaAnimation.countdownTickMs).toInt()
    var secondsLeft by remember(episode.id) { mutableIntStateOf(totalSeconds) }
    val latestOnElapsed by rememberUpdatedState(onElapsed)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(episode.id, lifecycle) {
        val started = { state: Lifecycle.State -> state.isAtLeast(Lifecycle.State.STARTED) }
        while (secondsLeft > 0) {
            lifecycle.currentStateFlow.first(started)
            delay(CinemaAnimation.countdownTickMs)
            if (started(lifecycle.currentState)) secondsLeft--
        }
        lifecycle.currentStateFlow.first(started)
        latestOnElapsed()
    }
    return secondsLeft
}
