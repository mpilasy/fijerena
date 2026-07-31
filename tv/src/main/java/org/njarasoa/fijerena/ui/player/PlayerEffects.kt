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
) {
    val isDeveloperMode = state.isDeveloperMode

    val context = androidx.compose.ui.platform.LocalContext.current

    // Auto-show toast on repeated buffer exhaustion
    LaunchedEffect(isDeveloperMode, currentMetadata.streamUrl) {
        val exhaustionTimestamps = mutableListOf<Long>()
        var lastSeenCount = 0
        while (true) {
            val currentCount = StreamingPlaybackService.getInstance()?.exhaustionRebufferCount?.value ?: 0
            if (currentCount < lastSeenCount) {
                // Count was reset (likely channel switch)
                lastSeenCount = currentCount
                exhaustionTimestamps.clear()
            } else if (currentCount > lastSeenCount) {
                val now = System.currentTimeMillis()
                repeat(currentCount - lastSeenCount) {
                    exhaustionTimestamps.add(now)
                }
                lastSeenCount = currentCount
                // Remove timestamps older than 30 seconds
                exhaustionTimestamps.removeAll { now - it > 30_000L }
                // Show toast if 3+ buffer exhaustions in 30s window
                if (exhaustionTimestamps.size >= 3) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(org.njarasoa.fijerena.core.ui.R.string.buffering_excessive_toast),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    // Clear timestamps to prevent repeated toasts for the same event window
                    exhaustionTimestamps.clear()
                }
            }
            delay(1000L)
        }
    }

    // Live position polling for smooth VOD timer updates
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

    // Auto-dismiss hints after 7 seconds
    LaunchedEffect(state.showControlHints) {
        if (state.showControlHints) {
            delay(CinemaAnimation.hintsDismissMs)
            state.showControlHints = false
        }
    }

    // Auto-hide overlays. Re-keyed on scrubPositionMs so each scrub step resets the timer
    // and the cursor stays visible while the user is still moving it.
    LaunchedEffect(state.showControls, state.showStreamInfo, state.scrubPositionMs, state.showStreamInfoTick) {
        if (state.scrubPositionMs != null) {
            // Hold the OSD open while scrubbing; longer timeout so user can keep stepping
            delay(CinemaAnimation.controlsAutoHideTvMs)
            state.scrubPositionMs = null
            state.showStreamInfo = false
        } else if (state.showControls && state.showStreamInfo) {
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
            delay(1000L)
        }
    }

    // Update displayed metadata when stream actually starts playing
    LaunchedEffect(currentMetadata.title, playbackState::class) {
        // Only update displayed metadata when stream is actually playing/buffering
        if (currentMetadata.title.isNotEmpty() &&
            (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering)
        ) {
            state.displayedMetadata = currentMetadata
        }
    }

    // Reset playback speed when paused, ended, or errored
    LaunchedEffect(playbackState::class) {
        if (playbackState is PlaybackState.Paused ||
            playbackState is PlaybackState.Ended ||
            playbackState is PlaybackState.Error
        ) {
            if (state.seekSpeedLabel != null) {
                viewModel?.setPlaybackSpeed(1f)
                state.seekSpeedLabel = null
            }
        }
    }

    // Show only stream info when stream starts from menu
    LaunchedEffect(currentMetadata.title, playbackState::class) {
        // Show only stream info when title changes on initial load from menu
        if (currentMetadata.title.isNotEmpty() &&
            currentMetadata.title != state.previousMetadataTitle &&
            (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering)
        ) {
            if (state.isInitialLoad) {
                // From menu selection - show only stream info
                state.showStreamInfo = true
                state.isInitialLoad = false
            }
            // Note: Channel switching sets showStreamInfo directly in key handler
            state.previousMetadataTitle = currentMetadata.title
        }
    }

    // Coalesce auto-repeat D-pad zap ticks (repeatCount >= 1) accumulated while the key was
    // held. The first tap/tick of every press already fired immediately in PlayerKeyHandler
    // (repeatCount == 0); this only ever applies the *extra* hops beyond that first one.
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
