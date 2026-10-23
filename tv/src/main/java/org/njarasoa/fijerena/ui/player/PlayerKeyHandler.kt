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
            // KeyUp: a Center/Enter KeyUp must be consumed when its own KeyDown just revealed the
            // OSD (see suppressNextCenterKeyUp's kdoc). Un-consumed, it falls through to the
            // button focus just landed on and activates it — a single press should only open
            // the OSD, never also toggle favourite or pause.
            // VOD with the OSD hidden: releasing Left/Right seeks to the scrub cursor (U-M7), so
            // a tap jumps 10 s and a hold scrubs, then seeks on release.
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
                // Let D-pad OK activate whatever's focused inside an open modal (e.g. tune a
                // channel in the channel panel) instead of revealing the OSD —
                // mirrors the isModalOpen guard on Up/Down/Left/Right below.
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
                } else if (currentMetadata.isLive) {
                    // Live: Up/Down zap whether or not the OSD is up (LT4) — its buttons are one
                    // row, moved through with Left/Right. A zap with the OSD up keeps it up.
                    // First tap fires immediately; auto-repeat ticks are coalesced (see PlayerEffects).
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
                // Let D-pad navigate inside anything modal that is open
                if (state.isModalOpen) {
                    false
                } else if (currentMetadata.isLive) {
                    // Live: zap, OSD up or not (see Up).
                    // First tap fires immediately; auto-repeat ticks are coalesced (see PlayerEffects).
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
                    // When controls are visible, let D-pad navigate between buttons
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
 * Move the scrub cursor one step. Initializes the cursor at the current playback position
 * when scrubbing starts. The actual seek happens only on [commitScrub]: on the release of
 * D-pad Left/Right, or on OK/Center (FF/REW).
 */
internal fun stepScrubCursor(
    state: PlayerScreenState,
    playbackState: PlaybackState,
    nativeEvent: AndroidKeyEvent,
    forward: Boolean,
) {
    // Use the live-polled position/duration (state.livePosition/liveDuration, refreshed every
    // 500ms in PlayerEffects), not playbackState.position/duration: that PlaybackState only
    // updates on discrete Player.Listener events, so during steady playback it stays frozen at
    // whatever it was minutes ago. Starting the scrub cursor from that stale value threw the
    // seek destination off by however long playback had been running.
    // A new cursor reads the player itself: the poll stops while Buffering, and ExoPlayer
    // already reports a pending seek's target, so taps in quick succession add up.
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
 * Cursor step for one key event. A tap moves 10s. Holding ramps by how long the key has been
 * held, not by repeatCount: auto-repeat fires every ~50ms, so the old repeatCount tiers hit
 * 2-minute steps (~40 min/s) within 1.5s and overshot every time.
 * At ~20 repeats/s: ~1 min/s for the first 2s, ~3 min/s up to 5s, then ~10 min/s.
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
