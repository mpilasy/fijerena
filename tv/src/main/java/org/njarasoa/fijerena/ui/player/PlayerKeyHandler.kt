package org.njarasoa.fijerena.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel

fun handlePlayerKeyEvent(
    keyEvent: KeyEvent,
    state: PlayerScreenState,
    viewModel: PlaybackViewModel,
    playbackState: PlaybackState,
    currentMetadata: PlayerMetadata,
    onBack: () -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) {
        return false
    }

    return when (keyEvent.key) {
        Key.DirectionCenter, Key.Enter -> {
            // When controls are visible, let ENTER pass through to buttons
            if (state.showControls) {
                false
            } else if (!state.showStats) {
                // Show controls overlay only — never pause on OK
                state.showControls = true
                state.showStreamInfo = true
                true
            } else {
                false
            }
        }
        Key.DirectionUp -> {
            // Let D-pad navigate inside overlays when they are open
            if (state.showCategoryOverlay || state.showLastWatchedOverlay) {
                false
            } else if (!state.showControls && currentMetadata.isLive) {
                onPreviousChannel()
                state.showStreamInfo = true
                true
            } else if (!state.showControls && !currentMetadata.isLive) {
                state.showControls = true
                state.showStreamInfo = true
                true
            } else {
                false
            }
        }
        Key.DirectionDown -> {
            // Let D-pad navigate inside overlays when they are open
            if (state.showCategoryOverlay || state.showLastWatchedOverlay) {
                false
            } else if (!state.showControls) {
                if (currentMetadata.isLive) {
                    onNextChannel()
                    state.showStreamInfo = true
                    true
                } else {
                    state.showControls = true
                    state.showStreamInfo = true
                    true
                }
            } else {
                false
            }
        }
        Key.DirectionLeft -> {
            if (!state.showControls && currentMetadata.isLive) {
                // Live TV: Left opens category overlay (or closes last-watched)
                when {
                    state.showLastWatchedOverlay -> state.showLastWatchedOverlay = false
                    else -> state.showCategoryOverlay = true
                }
                true
            } else if (!state.showControls && !currentMetadata.isLive) {
                // VOD: seek backward 10s
                val position = when (val ps = playbackState) {
                    is PlaybackState.Playing -> ps.position
                    is PlaybackState.Paused -> ps.position
                    else -> null
                }
                if (position != null) {
                    val newPosition = (position - 10_000L).coerceAtLeast(0L)
                    viewModel.seekTo(newPosition)
                    state.showStreamInfo = true
                }
                true
            } else {
                // When controls are visible, let D-pad navigate between buttons
                false
            }
        }
        Key.DirectionRight -> {
            if (!state.showControls && currentMetadata.isLive) {
                // Live TV: Right opens last-watched overlay (or closes category)
                when {
                    state.showCategoryOverlay -> state.showCategoryOverlay = false
                    else -> state.showLastWatchedOverlay = true
                }
                true
            } else if (!state.showControls && !currentMetadata.isLive) {
                // VOD: seek forward 10s
                val position = when (val ps = playbackState) {
                    is PlaybackState.Playing -> ps.position
                    is PlaybackState.Paused -> ps.position
                    else -> null
                }
                val duration = when (val ps = playbackState) {
                    is PlaybackState.Playing -> ps.duration
                    is PlaybackState.Paused -> ps.duration
                    else -> null
                }
                if (position != null && duration != null) {
                    val newPosition = (position + 10_000L).coerceAtMost(duration)
                    viewModel.seekTo(newPosition)
                    state.showStreamInfo = true
                }
                true
            } else {
                // When controls are visible, let D-pad navigate between buttons
                false
            }
        }
        Key.Back -> {
            // Close any visible overlays first, then exit
            when {
                state.showCategoryOverlay -> { state.showCategoryOverlay = false; true }
                state.showLastWatchedOverlay -> { state.showLastWatchedOverlay = false; true }
                state.showStats || state.showControls || state.showStreamInfo -> {
                    state.showStats = false
                    state.showControls = false
                    state.showStreamInfo = false
                    true
                }
                else -> {
                    viewModel.stop()
                    onBack()
                    true
                }
            }
        }
        Key(AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) -> {
            if (!currentMetadata.isLive) {
                when (playbackState) {
                    is PlaybackState.Playing -> viewModel.pause()
                    is PlaybackState.Paused -> viewModel.resume()
                    else -> {}
                }
            }
            true
        }
        Key(AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD) -> {
            if (!currentMetadata.isLive) viewModel.seekRelative(60_000L)
            true
        }
        Key(AndroidKeyEvent.KEYCODE_MEDIA_REWIND) -> {
            if (!currentMetadata.isLive) viewModel.seekRelative(-30_000L)
            true
        }
        else -> false
    }
}
