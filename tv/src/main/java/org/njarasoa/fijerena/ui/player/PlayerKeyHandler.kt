package org.njarasoa.fijerena.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import android.view.KeyEvent as AndroidKeyEvent

fun handlePlayerKeyEvent(
    keyEvent: KeyEvent,
    state: PlayerScreenState,
    viewModel: PlaybackViewModel,
    playbackState: PlaybackState,
    currentMetadata: PlayerMetadata,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
): Boolean {
    // suppressNextCenterKeyUp is only ever set true from the Center/Enter KeyDown branch in
    // handleKeyDown, which is gated on `!state.isModalOpen` — so it can never be set true while
    // a modal is already open. Observing
    // both true at once here means a dialog/overlay opened *between* that KeyDown and its own
    // KeyUp arriving: that KeyUp went to the new modal's own focus target instead of back here,
    // so nothing would ever clear the flag — leaving it to silently eat the next legitimate
    // center click once the modal closes. Checked on every event, not just Center/Enter KeyUp,
    // so it clears as soon as anything else reaches this handler instead of staying stuck for
    // the rest of the session.
    if (state.suppressNextCenterKeyUp && state.isModalOpen) {
        state.suppressNextCenterKeyUp = false
    }

    val handled =
        when {
            // KeyUp: no-op for scrub mode (we only act on KeyDown to step the cursor and on Center
            // to commit) — except a Center/Enter KeyUp that must be consumed because its own
            // KeyDown just revealed the OSD (see suppressNextCenterKeyUp's kdoc). Un-consumed, it
            // falls through to the button focus just landed on and activates it — a single press
            // should only open the OSD, never also toggle favourite or pause.
            keyEvent.type == KeyEventType.KeyUp ->
                if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) && state.suppressNextCenterKeyUp) {
                    state.suppressNextCenterKeyUp = false
                    true
                } else {
                    false
                }
            keyEvent.type != KeyEventType.KeyDown -> false
            else -> handleKeyDown(keyEvent, state, viewModel, playbackState, currentMetadata, onNextChannel, onPreviousChannel)
        }
    return handled
}

private fun handleKeyDown(
    keyEvent: KeyEvent,
    state: PlayerScreenState,
    viewModel: PlaybackViewModel,
    playbackState: PlaybackState,
    currentMetadata: PlayerMetadata,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
): Boolean {
    val handled = when (keyEvent.key) {
        Key.DirectionCenter, Key.Enter -> {
            // Let D-pad OK activate whatever's focused inside an open modal (e.g. select a
            // channel in the category/last-watched overlay) instead of revealing the OSD —
            // mirrors the isModalOpen guard on Up/Down/Left/Right below.
            if (state.isModalOpen) {
                false
            } else {
                val now = System.currentTimeMillis()
                val isDoubleClick = now - state.lastOkClickTime < 350L
                state.lastOkClickTime = now

                val pendingScrub = state.scrubPositionMs
                if (pendingScrub != null && !currentMetadata.isLive) {
                    // Commit scrub: seek to the cursor position and exit scrub mode
                    viewModel.seekTo(pendingScrub)
                    state.scrubPositionMs = null
                    state.showStreamInfo = true
                    true
                } else if (isDoubleClick && state.showStats) {
                    // Double-click ONLY dismisses stats if they are already showing
                    state.showStats = false
                    true
                } else if (state.showStats) {
                    // Single-click while stats are showing: pass to player/controls
                    if (state.showControls) {
                        false
                    } else {
                        state.showControls = true
                        state.showStreamInfo = true
                        state.suppressNextCenterKeyUp = true
                        true
                    }
                } else {
                    // Single-click (or double-click when stats are NOT showing):
                    // Let it pass if controls are visible, or show controls if not
                    if (state.showControls) {
                        false
                    } else {
                        state.showControls = true
                        state.showStreamInfo = true
                        state.suppressNextCenterKeyUp = true
                        true
                    }
                }
            }
        }
        Key.DirectionUp -> {
            // Let D-pad navigate inside anything modal that is open
            if (state.isModalOpen) {
                false
            } else if (!state.showControls && currentMetadata.isLive) {
                // First tap fires immediately; auto-repeat ticks are coalesced (see PlayerEffects).
                if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                    onPreviousChannel()
                } else {
                    state.pendingChannelDelta -= 1
                }
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
            // Let D-pad navigate inside anything modal that is open
            if (state.isModalOpen) {
                false
            } else if (!state.showControls) {
                if (currentMetadata.isLive) {
                    // First tap fires immediately; auto-repeat ticks are coalesced (see PlayerEffects).
                    if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                        onNextChannel()
                    } else {
                        state.pendingChannelDelta += 1
                    }
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
            if (state.isModalOpen) {
                // A track picker or overlay owns the D-pad; do not also seek or swap overlays.
                false
            } else if (!state.showControls && currentMetadata.isLive) {
                // Live TV: Left opens category overlay (or closes last-watched)
                when {
                    state.showLastWatchedOverlay -> state.showLastWatchedOverlay = false
                    else -> state.showCategoryOverlay = true
                }
                true
            } else if (!state.showControls && !currentMetadata.isLive) {
                // VOD: move scrub cursor backward; OK commits the seek
                stepScrubCursor(state, playbackState, keyEvent.nativeKeyEvent.repeatCount, forward = false)
                true
            } else {
                // When controls are visible, let D-pad navigate between buttons
                false
            }
        }
        Key.DirectionRight -> {
            if (state.isModalOpen) {
                // A track picker or overlay owns the D-pad; do not also seek or swap overlays.
                false
            } else if (!state.showControls && currentMetadata.isLive) {
                // Live TV: Right opens last-watched overlay (or closes category)
                when {
                    state.showCategoryOverlay -> state.showCategoryOverlay = false
                    else -> state.showLastWatchedOverlay = true
                }
                true
            } else if (!state.showControls && !currentMetadata.isLive) {
                // VOD: move scrub cursor forward; OK commits the seek
                stepScrubCursor(state, playbackState, keyEvent.nativeKeyEvent.repeatCount, forward = true)
                true
            } else {
                // When controls are visible, let D-pad navigate between buttons
                false
            }
        }
        // Key.Back is intentionally NOT handled here — see the BackHandler in PlayerScreen.kt.
        // A raw onKeyEvent consume doesn't stop the separate OnBackPressedDispatcher chain, so
        // handling it in both places raced an outer BackHandler (e.g. LiveTvSplitLayout's) into
        // seeing already-mutated state and double-popping past it.
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
    return handled
}

/**
 * Move the scrub cursor by a step proportional to how long the user has been holding the key.
 * Initializes the cursor at the current playback position when scrubbing starts.
 * The actual seek happens only when the user presses OK/Center to commit.
 */
private fun stepScrubCursor(
    state: PlayerScreenState,
    playbackState: PlaybackState,
    repeatCount: Int,
    forward: Boolean,
) {
    val isScrubbableState =
        when (playbackState) {
            is PlaybackState.Playing, is PlaybackState.Paused -> true
            else -> false
        }
    // Use the live-polled position/duration (state.livePosition/liveDuration, refreshed every
    // 500ms in PlayerEffects), not playbackState.position/duration: that PlaybackState only
    // updates on discrete Player.Listener events, so during steady playback it stays frozen at
    // whatever it was minutes ago. Starting the scrub cursor from that stale value threw the
    // seek destination off by however long playback had been running — the "rewind/fast
    // forward fucked up" bug.
    val duration = state.liveDuration

    if (isScrubbableState && duration > 0L) {
        val origin = state.scrubPositionMs ?: state.livePosition
        val step =
            when {
                repeatCount < 5 -> 10_000L
                repeatCount < 15 -> 30_000L
                repeatCount < 30 -> 60_000L
                else -> 120_000L
            }
        val delta = if (forward) step else -step
        state.scrubPositionMs = (origin + delta).coerceIn(0L, duration)
        state.showStreamInfo = true
    }
}
