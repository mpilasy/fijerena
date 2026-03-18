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
    // Handle KeyUp to reset fast-forward/rewind speed
    if (keyEvent.type == KeyEventType.KeyUp) {
        return when (keyEvent.key) {
            Key.DirectionRight -> {
                if (!currentMetadata.isLive && state.seekSpeedLabel != null) {
                    viewModel.setPlaybackSpeed(1f)
                    state.seekSpeedLabel = null
                    true
                } else false
            }
            Key.DirectionLeft -> {
                if (!currentMetadata.isLive && state.seekSpeedLabel != null) {
                    state.seekSpeedLabel = null
                    true
                } else false
            }
            else -> false
        }
    }

    if (keyEvent.type != KeyEventType.KeyDown) {
        return false
    }

    return when (keyEvent.key) {
        Key.DirectionCenter, Key.Enter -> {
            val now = System.currentTimeMillis()
            val isDoubleClick = now - state.lastOkClickTime < 350L
            state.lastOkClickTime = now

            if (isDoubleClick && state.showStats) {
                // Double-click ONLY dismisses stats if they are already showing
                state.showStats = false
                true
            } else {
                // Single-click (or double-click when stats are NOT showing):
                // Let it pass if controls are visible, or show controls if not
                if (state.showControls) {
                    false
                } else {
                    state.showControls = true
                    state.showStreamInfo = true
                    true
                }
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
                // VOD: rewind with acceleration on hold
                val repeatCount = keyEvent.nativeKeyEvent.repeatCount
                val position = when (val ps = playbackState) {
                    is PlaybackState.Playing -> ps.position
                    is PlaybackState.Paused -> ps.position
                    else -> null
                }
                if (position != null) {
                    val seekAmount = when {
                        repeatCount < 10 -> 10_000L
                        repeatCount < 20 -> 30_000L
                        repeatCount < 35 -> 60_000L
                        else -> 120_000L
                    }
                    state.seekSpeedLabel = when {
                        repeatCount < 10 -> null
                        repeatCount < 20 -> "<< 3x"
                        repeatCount < 35 -> "<< 6x"
                        else -> "<< 12x"
                    }
                    viewModel.seekTo((position - seekAmount).coerceAtLeast(0L))
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
                // VOD: fast-forward with acceleration on hold
                val repeatCount = keyEvent.nativeKeyEvent.repeatCount
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
                    if (repeatCount == 0) {
                        // Single tap: seek +10s
                        viewModel.seekTo((position + 10_000L).coerceAtMost(duration))
                    } else {
                        // Held: accelerate playback speed
                        val speed = when {
                            repeatCount < 10 -> 2f
                            repeatCount < 20 -> 4f
                            repeatCount < 35 -> 8f
                            else -> 16f
                        }
                        viewModel.setPlaybackSpeed(speed)
                        state.seekSpeedLabel = ">> ${speed.toInt()}x"
                    }
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
            if (!currentMetadata.isLive) viewModel.seekRelative(300_000L)
            true
        }
        Key(AndroidKeyEvent.KEYCODE_MEDIA_REWIND) -> {
            if (!currentMetadata.isLive) viewModel.seekRelative(-60_000L)
            true
        }
        else -> false
    }
}
