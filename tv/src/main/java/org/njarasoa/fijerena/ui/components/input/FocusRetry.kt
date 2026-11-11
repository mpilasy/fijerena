package org.njarasoa.fijerena.ui.components.input

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester

/** About half a second at 60 Hz: long enough for a slow TV to compose and attach the target. */
const val FOCUS_RETRY_MAX_FRAMES = 30

/**
 * Moves focus to this requester, retrying for at most [maxFrames] frames, after 1, 2, 4, 8…
 * frames, then [fallback] once. Returns whether focus landed on either.
 *
 * The growing gaps matter: each failed attempt makes Compose print a 7-line "FocusRequester is not
 * initialized" warning, and a per-frame retry that kept failing flooded logcat until logd
 * throttled the app and dropped its other lines (it hid a playback failure on a Shield). A failure
 * now costs at most ~6 warnings.
 *
 * Retry on the result, never on an exception: since Compose UI 1.10 a requester whose target is
 * not attached yet returns false instead of throwing `IllegalStateException`. Call from a
 * `LaunchedEffect` (it needs a frame clock). See the next level rock solid resilience plan, R-05.
 */
suspend fun FocusRequester.requestFocusWithRetry(
    maxFrames: Int = FOCUS_RETRY_MAX_FRAMES,
    fallback: FocusRequester? = null,
): Boolean {
    var focused = requestFocus(FocusDirection.Enter)
    var frames = 0
    var gap = 1
    while (!focused && frames < maxFrames) {
        repeat(gap) { withFrameNanos { } }
        frames += gap
        gap *= 2
        focused = requestFocus(FocusDirection.Enter)
    }
    if (!focused && fallback != null) focused = fallback.requestFocus(FocusDirection.Enter)
    return focused
}
