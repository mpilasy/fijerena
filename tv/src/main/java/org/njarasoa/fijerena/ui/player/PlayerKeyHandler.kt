package org.njarasoa.fijerena.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
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
    hasChannelPanel: Boolean,
): Boolean {
    // suppressNextCenterKeyUp is set only by the Center/Enter KeyDown branch, which requires
    // `!state.isModalOpen`. Both true here means a modal opened between that KeyDown and its KeyUp,
    // which went to the modal, so nothing would clear the flag and it would eat the next centre click.
    // Checked on every event, so it clears as soon as anything reaches this handler.
    if (state.suppressNextCenterKeyUp && state.isModalOpen) {
        state.suppressNextCenterKeyUp = false
    }

    val handled =
        when {
            // KeyUp: a Center/Enter KeyUp whose KeyDown just revealed the OSD is consumed (see
            // suppressNextCenterKeyUp); else it would also activate the button that just got focus.
            // VOD with the OSD hidden: releasing Left/Right seeks to the scrub cursor (U-M7), so a tap jumps
            // 10 s and a hold scrubs, then seeks on release.
            keyEvent.type == KeyEventType.KeyUp -> {
                if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) && state.suppressNextCenterKeyUp) {
                    state.suppressNextCenterKeyUp = false
                    true
                } else if ((keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight) &&
                    !state.isModalOpen && !state.showControls && !currentMetadata.isLive && state.scrubPositionMs != null
                ) {
                    commitScrub(state, viewModel)
                    state.showStreamInfo = true
                    true
                } else {
                    false
                }
            }

            keyEvent.type != KeyEventType.KeyDown -> {
                false
            }

            else -> {
                handleKeyDown(keyEvent, state, viewModel, playbackState, currentMetadata, onNextChannel, onPreviousChannel, hasChannelPanel)
            }
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
    hasChannelPanel: Boolean,
): Boolean {
    val handled =
        when (keyEvent.key) {
            Key.DirectionCenter, Key.Enter -> {
                // In an open modal, OK activates what is focused (tunes a channel in the channel panel) instead of
                // revealing the OSD, as for Up/Down/Left/Right below.
                if (state.isModalOpen) {
                    false
                } else {
                    val now = System.currentTimeMillis()
                    val isDoubleClick = now - state.lastOkClickTime < 350L
                    state.lastOkClickTime = now

                    if (state.scrubPositionMs != null && !currentMetadata.isLive) {
                        commitScrub(state, viewModel)
                        state.showStreamInfo = true
                        true
                    } else if (isDoubleClick && state.showStats) {
                        // A double click only closes the stats when they show.
                        state.showStats = false
                        true
                    } else if (state.showStats) {
                        // Single click while the stats show: pass it on to the player/controls.
                        if (state.showControls) {
                            false
                        } else {
                            state.showControls = true
                            state.showStreamInfo = true
                            state.suppressNextCenterKeyUp = true
                            true
                        }
                    } else {
                        // Single click, or a double click with no stats: pass it on when the controls show, else show them.
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
                // Let the D-pad move inside an open modal.
                if (state.isModalOpen) {
                    false
                } else if (currentMetadata.isLive) {
                    // Live: Up/Down zap whether or not the OSD shows (LT4); its buttons are one row, moved through with
                    // Left/Right. A zap with the OSD up keeps it up. The first tap fires at once; auto-repeat ticks are
                    // coalesced (see PlayerEffects).
                    if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                        onPreviousChannel()
                    } else {
                        state.pendingChannelDelta -= 1
                    }
                    state.showStreamInfo = true
                    true
                } else if (!state.showControls) {
                    state.showControls = true
                    state.showStreamInfo = true
                    true
                } else {
                    false
                }
            }

            Key.DirectionDown -> {
                // Let the D-pad move inside an open modal.
                if (state.isModalOpen) {
                    false
                } else if (currentMetadata.isLive) {
                    // Live: zap, OSD up or not (see Up). The first tap fires at once; auto-repeat ticks are coalesced.
                    if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                        onNextChannel()
                    } else {
                        state.pendingChannelDelta += 1
                    }
                    state.showStreamInfo = true
                    true
                } else if (!state.showControls) {
                    state.showControls = true
                    state.showStreamInfo = true
                    true
                } else {
                    false
                }
            }

            Key.DirectionLeft -> {
                if (state.isModalOpen) {
                    // A track picker or the channel panel owns the D-pad; do not also seek.
                    false
                } else if (!state.showControls && currentMetadata.isLive) {
                    // Live TV: Left and Right both open the one channel panel (LT3, Decision 4).
                    // Once it is open, isModalOpen (above) leaves the D-pad to the panel.
                    if (hasChannelPanel) state.showChannelPanel = true
                    hasChannelPanel
                } else if (!state.showControls && !currentMetadata.isLive) {
                    // VOD: move the scrub cursor back; releasing the key (or OK) seeks
                    stepScrubCursor(state, playbackState, keyEvent.nativeKeyEvent, forward = false)
                    true
                } else {
                    // Controls visible: the D-pad moves between buttons.
                    false
                }
            }

            Key.DirectionRight -> {
                if (state.isModalOpen) {
                    // A track picker or the channel panel owns the D-pad; do not also seek.
                    false
                } else if (!state.showControls && currentMetadata.isLive) {
                    // Live TV: the same panel as Left.
                    if (hasChannelPanel) state.showChannelPanel = true
                    hasChannelPanel
                } else if (!state.showControls && !currentMetadata.isLive) {
                    // VOD: move the scrub cursor forward; releasing the key (or OK) seeks
                    stepScrubCursor(state, playbackState, keyEvent.nativeKeyEvent, forward = true)
                    true
                } else {
                    // Controls visible: the D-pad moves between buttons.
                    false
                }
            }

            // Key.Back is not handled here (see the BackHandler in PlayerScreen.kt): consuming a raw key event
            // does not stop the OnBackPressedDispatcher chain, so an outer BackHandler (LiveTvSplitLayout's)
            // also ran on changed state and popped twice.
            Key(AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) -> {
                if (!currentMetadata.isLive) {
                    when (playbackState) {
                        is PlaybackState.Playing -> {
                            viewModel.pause()
                        }

                        is PlaybackState.Paused -> {
                            viewModel.resume()
                        }

                        else -> {}
                    }
                }
                true
            }

            // FF/REW move the same scrub cursor as D-pad Left/Right; OK commits, Back cancels.
            Key(AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD) -> {
                if (!currentMetadata.isLive) stepScrubCursor(state, playbackState, keyEvent.nativeKeyEvent, forward = true)
                true
            }

            Key(AndroidKeyEvent.KEYCODE_MEDIA_REWIND) -> {
                if (!currentMetadata.isLive) stepScrubCursor(state, playbackState, keyEvent.nativeKeyEvent, forward = false)
                true
            }

            else -> {
                false
            }
        }
    return handled
}

/**
 * Moves the scrub cursor one step, starting it at the playback position. The seek happens only on
 * [commitScrub]: on the release of D-pad Left/Right, or on OK/Center (FF/REW).
 */
internal fun stepScrubCursor(
    state: PlayerScreenState,
    playbackState: PlaybackState,
    nativeEvent: AndroidKeyEvent,
    forward: Boolean,
) {
    // Use the polled position/duration (state.livePosition/liveDuration, every 500 ms in PlayerEffects),
    // not playbackState.position/duration, which updates only on Player.Listener events and is stale
    // during steady playback. A new cursor reads the player itself: the poll stops while Buffering, and
    // ExoPlayer reports a pending seek's target, so quick taps add up.
    if (state.scrubPositionMs == null) {
        StreamingPlaybackService.getInstance()?.getPlayer()?.let { player ->
            state.livePosition = player.currentPosition
            state.liveDuration = player.duration.coerceAtLeast(0L)
        }
    }
    val duration = state.liveDuration

    if (canScrub(playbackState, duration)) {
        val origin = state.scrubPositionMs ?: state.livePosition
        val step = scrubStepMs(nativeEvent.repeatCount, nativeEvent.eventTime - nativeEvent.downTime)
        val delta = if (forward) step else -step
        state.scrubPositionMs = (origin + delta).coerceIn(0L, duration)
        state.showStreamInfo = true
    }
}

/**
 * Whether the scrub cursor may move. Buffering counts once the duration is known: a tap seeks
 * at once, so the next tap often lands while that seek is still buffering and must not be lost.
 */
internal fun canScrub(
    playbackState: PlaybackState,
    durationMs: Long,
): Boolean {
    val scrubbableState =
        when (playbackState) {
            is PlaybackState.Playing, is PlaybackState.Paused, PlaybackState.Buffering -> true
            else -> false
        }
    return scrubbableState && durationMs > 0L
}

/**
 * Cursor step for one key event. A tap moves 10 s. A hold ramps by how long the key has been held,
 * not by repeatCount: auto-repeat fires every ~50 ms, so repeatCount tiers overshot. At ~20
 * repeats/s: ~1 min/s for the first 2 s, ~3 min/s up to 5 s, then ~10 min/s.
 */
internal fun scrubStepMs(
    repeatCount: Int,
    heldMs: Long,
): Long =
    when {
        repeatCount == 0 -> 10_000L
        heldMs < 2_000L -> 3_000L
        heldMs < 5_000L -> 10_000L
        else -> 30_000L
    }

/** Seek to the scrub cursor and leave scrub mode. No-op when not scrubbing. */
internal fun commitScrub(
    state: PlayerScreenState,
    viewModel: PlaybackViewModel,
) {
    val target = state.scrubPositionMs ?: return
    viewModel.seekTo(target)
    // The 500ms position poll pauses while Buffering, so without this the bar would snap back
    // to the pre-seek position until the seek lands, then jump forward again.
    state.livePosition = target
    state.scrubPositionMs = null
}
