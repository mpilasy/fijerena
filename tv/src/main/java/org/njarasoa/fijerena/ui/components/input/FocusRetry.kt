package org.njarasoa.fijerena.ui.components.input

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester

/** About half a second at 60 Hz: long enough for a slow TV to compose and attach the target. */
const val FOCUS_RETRY_MAX_FRAMES = 30

/**
 * Moves focus to this requester, retrying once per frame until it takes, for at most [maxFrames]
 * frames, then [fallback] once. Returns whether focus landed on either.
 *
 * Retry on the result, never on an exception: since Compose UI 1.10 a requester whose target isn't
 * attached yet (an item not composed, a parent still laying out) prints a warning and returns false
 * instead of throwing `IllegalStateException`, so `try { requestFocus() } catch (_: IllegalStateException)`
 * retries nothing. Call from a `LaunchedEffect` (a frame clock is needed). See
 * docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-05.
 */
suspend fun FocusRequester.requestFocusWithRetry(
    maxFrames: Int = FOCUS_RETRY_MAX_FRAMES,
    fallback: FocusRequester? = null,
): Boolean {
    var focused = requestFocus(FocusDirection.Enter)
    var frames = 0
    while (!focused && frames < maxFrames) {
        withFrameNanos { }
        frames++
        focused = requestFocus(FocusDirection.Enter)
    }
    if (!focused && fallback != null) focused = fallback.requestFocus(FocusDirection.Enter)
    return focused
}
