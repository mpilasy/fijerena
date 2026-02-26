package org.njarasoa.fijerena.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import java.util.Calendar

@Composable
fun PlayerEffects(
    state: PlayerScreenState,
    playbackState: PlaybackState,
    currentMetadata: PlayerMetadata
) {
    val isDeveloperMode = state.isDeveloperMode

    // Auto-show stats on repeated buffer exhaustion (dev mode only)
    LaunchedEffect(isDeveloperMode) {
        if (!isDeveloperMode) return@LaunchedEffect
        val rebufferTimestamps = mutableListOf<Long>()
        var lastSeenCount = 0
        while (true) {
            val currentCount = StreamingPlaybackService.getInstance()?.rebufferCount?.value ?: 0
            if (currentCount > lastSeenCount) {
                val now = System.currentTimeMillis()
                repeat(currentCount - lastSeenCount) {
                    rebufferTimestamps.add(now)
                }
                lastSeenCount = currentCount
                // Remove timestamps older than 30 seconds
                rebufferTimestamps.removeAll { now - it > 30_000L }
                // Show stats if 3+ rebuffers in 30s window
                if (rebufferTimestamps.size >= 3 && !state.showStats) {
                    state.showStats = true
                }
            }
            delay(1000L)
        }
    }

    // Live position polling for smooth VOD timer updates
    LaunchedEffect(playbackState) {
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

    // Auto-dismiss hints after 7 seconds
    LaunchedEffect(state.showControlHints) {
        if (state.showControlHints) {
            delay(CinemaAnimation.hintsDismissMs)
            state.showControlHints = false
        }
    }

    // Auto-hide overlays
    LaunchedEffect(state.showControls, state.showStreamInfo) {
        if (state.showControls && state.showStreamInfo) {
            // Both visible (OK press) - hide after 15 seconds
            delay(CinemaAnimation.controlsAutoHideTvMs)
            state.showControls = false
            state.showStreamInfo = false
        } else if (state.showStreamInfo) {
            // Stream info alone (channel switch or menu) - hide after 3 seconds
            delay(CinemaAnimation.toastDismissMs)
            state.showStreamInfo = false
        } else if (state.showControls) {
            // Controls alone (shouldn't happen but handle it) - hide after 15 seconds
            delay(CinemaAnimation.controlsAutoHideTvMs)
            state.showControls = false
        }
    }

    // Request focus on mount
    LaunchedEffect(Unit) {
        state.focusRequester.requestFocus()
    }

    // Top-of-hour clock: show 30s before the hour, hide 90s after
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            val totalSecondsIntoHour = now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND)
            val showAtSecond = 59 * 60 + 30 // 3570s into the hour
            state.showTopOfHourClock = totalSecondsIntoHour >= showAtSecond || totalSecondsIntoHour < 90
            state.clockTick = System.currentTimeMillis()
            delay(1000L)
        }
    }

    // Update displayed metadata when stream actually starts playing
    LaunchedEffect(currentMetadata.title, playbackState) {
        // Only update displayed metadata when stream is actually playing/buffering
        if (currentMetadata.title.isNotEmpty() &&
            (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering)) {
            state.displayedMetadata = currentMetadata
        }
    }

    // Show only stream info when stream starts from menu
    LaunchedEffect(currentMetadata.title, playbackState) {
        // Show only stream info when title changes on initial load from menu
        if (currentMetadata.title.isNotEmpty() &&
            currentMetadata.title != state.previousMetadataTitle &&
            (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering)) {

            if (state.isInitialLoad) {
                // From menu selection - show only stream info
                state.showStreamInfo = true
                state.isInitialLoad = false
            }
            // Note: Channel switching sets showStreamInfo directly in key handler
            state.previousMetadataTitle = currentMetadata.title
        }
    }
}
