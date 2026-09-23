package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Animation & Timing Constants
 * Centralizes all duration and timing values for consistent animations.
 */
object CinemaAnimation {
    const val focusDurationMs = 200
    const val fadeInDurationMs = 600
    const val navTransitionMs = 300
    const val controlsAutoHideTvMs = 15_000L
    const val controlsAutoHideMobileMs = 5_000L
    const val toastDismissMs = 3_000L
    const val hintsDismissMs = 7_000L
    const val statsUpdateMs = 1_000L
    const val loadingDebounceMs = 600L
    const val searchDebounceMs = 300L

    /** How long the double-tap seek ripple pill stays up after the last tap in a burst. */
    const val seekRippleDismissMs = 600L
    const val imageLoadCrossfadeMs = 300
    const val shimmerDurationMs = 1200

    /** Screen slides, crossfades, drawer reveals. */
    val StandardEasing: Easing = FastOutSlowInEasing

    /** Focus scale and player overlay popups — a snappier settle than [StandardEasing]. */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
}
