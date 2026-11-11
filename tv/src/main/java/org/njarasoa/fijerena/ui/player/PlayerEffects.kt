package org.njarasoa.fijerena.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import java.util.Calendar

@Composable
fun PlayerEffects(
    state: PlayerScreenState,
    playbackState: PlaybackState,
    currentMetadata: PlayerMetadata,
    viewModel: PlaybackViewModel? = null,
    onNextChannel: () -> Unit = {},
    onPreviousChannel: () -> Unit = {},
    // The channel being tuned (PlayerScreen's tuningChannelName). Null = nothing being tuned.
    tuningChannelName: String? = null,
) {
    // Poll the position for smooth VOD timer updates.
    LaunchedEffect(playbackState::class) {
        if (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused) {
            while (true) {
                StreamingPlaybackService.getInstance()?.getPlayer()?.let { player ->
                    state.livePosition = player.currentPosition
                    state.liveDuration = player.duration.coerceAtLeast(0L)
                }
                delay(500L)
            }
        }
    }

    // Auto-hide overlays. Keyed on scrubPositionMs, so each scrub step restarts the timer, and on
    // controlsKeyTick, so any key while the OSD is up restarts its 5 s. Keyed on holdBanner too: the
    // banner alone stays up while the stream starts, and its 3 s start once it plays.
    val holdBanner =
        holdStreamInfo(
            showControls = state.showControls,
            showStreamInfo = state.showStreamInfo,
            scrubbing = state.scrubPositionMs != null,
            waiting = isStreamWaiting(playbackState, tuningChannelName),
        )
    LaunchedEffect(
        state.showControls,
        state.showStreamInfo,
        state.scrubPositionMs,
        state.showStreamInfoTick,
        state.controlsKeyTick,
        holdBanner,
    ) {
        if (state.scrubPositionMs != null) {
            // Keep the OSD open while scrubbing; each step restarts the timer.
            delay(CinemaAnimation.controlsAutoHideTvMs)
            state.scrubPositionMs = null
            state.showStreamInfo = false
        } else if (state.showControls && state.showStreamInfo) {
            // Both visible (OK press): hide 5 s after the last key.
            delay(CinemaAnimation.controlsAutoHideTvMs)
            state.showControls = false
            state.showStreamInfo = false
        } else if (state.showStreamInfo && !holdBanner) {
            // Stream info alone (channel switch or menu): hide after 3 s.
            delay(CinemaAnimation.toastDismissMs)
            state.showStreamInfo = false
        } else if (state.showControls) {
            // Controls alone (not expected): hide after 5 s.
            delay(CinemaAnimation.controlsAutoHideTvMs)
            state.showControls = false
        }
    }

    LaunchedEffect(Unit) {
        state.focusRequester.requestFocus()
    }

    // Top-of-hour clock: show 30 s before the hour, hide 90 s after.
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            val totalSecondsIntoHour = now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND)
            val showAtSecond = 59 * 60 + 30 // 3570s into the hour
            state.showTopOfHourClock = totalSecondsIntoHour >= showAtSecond || totalSecondsIntoHour < 90
            delay(1000L)
        }
    }

    // Update the displayed metadata when the stream starts to play.
    LaunchedEffect(currentMetadata.title, playbackState::class) {
        if (currentMetadata.title.isNotEmpty() &&
            (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering)
        ) {
            state.displayedMetadata = currentMetadata
        }
    }

    // Show only the stream info when a stream starts from a menu.
    LaunchedEffect(currentMetadata.title, playbackState::class) {
        if (currentMetadata.title.isNotEmpty() &&
            currentMetadata.title != state.previousMetadataTitle &&
            (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering)
        ) {
            if (state.isInitialLoad) {
                state.showStreamInfo = true
                state.isInitialLoad = false
            }
            // Channel switching sets showStreamInfo in the key handler.
            state.previousMetadataTitle = currentMetadata.title
        }
    }

    // Apply the auto-repeat zap ticks (repeatCount >= 1) collected while the key was held. The first
    // tick of each press already fired in PlayerKeyHandler (repeatCount == 0); this applies only the
    // extra hops.
    LaunchedEffect(state.pendingChannelDelta) {
        if (state.pendingChannelDelta == 0) return@LaunchedEffect
        delay(300)
        val delta = state.pendingChannelDelta
        state.pendingChannelDelta = 0
        repeat(kotlin.math.abs(delta)) {
            if (delta > 0) onNextChannel() else onPreviousChannel()
        }
    }
}

/**
 * True while the stream is not playing yet: it buffers or is idle, or a channel is being tuned.
 * Buffering in the middle of a stream counts too, but it never opens the banner.
 */
internal fun isStreamWaiting(
    playbackState: PlaybackState,
    tuningChannelName: String?,
): Boolean = playbackState is PlaybackState.Buffering || playbackState is PlaybackState.Idle || tuningChannelName != null

/**
 * True when the stream info banner shows alone (no controls, no scrub cursor) while the stream
 * waits: its 3 s auto-hide must not run until the stream plays.
 */
internal fun holdStreamInfo(
    showControls: Boolean,
    showStreamInfo: Boolean,
    scrubbing: Boolean,
    waiting: Boolean,
): Boolean = showStreamInfo && !showControls && !scrubbing && waiting
