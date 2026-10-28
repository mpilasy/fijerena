package org.njarasoa.fijerena.feature.player

/** Double-tap seek step (2a) — matches the app-wide seek convention documented in CONTRIBUTING.md. */
internal const val SEEK_STEP_MS = 10_000L
internal const val SEEK_STEP_SECONDS = 10

/**
 * The seek side of a tap at [x] on a player [width] wide: -1 (rewind) in the left 40 %, 1
 * (forward) in the right 40 %, 0 in the centre 20 %, which only toggles the controls.
 */
internal fun seekTapSide(
    x: Float,
    width: Float,
): Int =
    when {
        x < width * 0.4f -> -1
        x > width * 0.6f -> 1
        else -> 0
    }

/**
 * The seconds the seek pill shows after one more step on [side]: added to the burst on the same
 * side ([burstSide], [burstSeconds]), started again on the other side or with no burst.
 */
internal fun seekBurstSeconds(
    burstSide: Int,
    burstSeconds: Int,
    side: Int,
): Int = if (burstSide == side) burstSeconds + SEEK_STEP_SECONDS else SEEK_STEP_SECONDS
