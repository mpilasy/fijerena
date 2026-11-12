package org.njarasoa.fijerena.core.player.display

import kotlin.math.abs

/**
 * The display mode for content at [fps] frames per second (the match frame rate plan, Phase 2), or
 * null when no mode fits:
 * 1. Only the modes with [current]'s width and height: the resolution does not change.
 * 2. A mode whose refresh rate is a whole multiple (1× to 5×) of [fps] within 0.05 %, so 23.976
 *    and 24 stay apart.
 * 3. 25, 29.97 and 30 fps prefer twice their rate (50, 59.94, 60 Hz): 25 and 30 Hz modes flicker
 *    on some TVs and make the UI slow. Other rates prefer 1×, then 2×, then the higher multiples.
 * 4. With no mode at 0.05 %, a mode at 0.2 % (23.976 content on a 24 Hz mode repeats one frame in
 *    42 seconds).
 *
 * Among modes that fit equally well, [current] comes first.
 */
fun pickDisplayMode(
    modes: List<ModeSpec>,
    current: ModeSpec,
    fps: Float,
): ModeSpec? {
    val sameSize = (listOf(current) + modes).filter { it.width == current.width && it.height == current.height }.distinctBy { it.id }
    val multiples = if (fps in DOUBLE_FIRST_RATES) DOUBLE_FIRST else SINGLE_FIRST
    return if (fps > 0f) {
        bestMatch(sameSize, fps, multiples, EXACT_TOLERANCE) ?: bestMatch(sameSize, fps, multiples, CLOSE_TOLERANCE)
    } else {
        null
    }
}

/**
 * The mode the full-screen player asks for, or null to keep the mode in place. [enabled] is the
 * This Device switch, with the system's Match Content Frame Rate not at Never. Live does not
 * switch in Phase 2.
 */
fun displayModeToRequest(
    enabled: Boolean,
    isLive: Boolean,
    fps: Float?,
    modes: List<ModeSpec>,
    current: ModeSpec,
): ModeSpec? {
    val target = fps?.takeIf { enabled && !isLive }?.let { pickDisplayMode(modes, current, it) }
    return target?.takeIf { it.id != current.id }
}

/** The mode nearest to the first multiple of [fps] in [multiples] that a mode fits within [tolerance]. */
private fun bestMatch(
    modes: List<ModeSpec>,
    fps: Float,
    multiples: List<Int>,
    tolerance: Float,
): ModeSpec? =
    multiples.firstNotNullOfOrNull { multiple ->
        val refresh = fps * multiple
        modes
            .filter { abs(it.refreshRate - refresh) <= refresh * tolerance }
            .minByOrNull { abs(it.refreshRate - refresh) }
    }

/** A refresh rate fits a multiple of the frame rate within this part of it: 23.976 and 24 are 0.1 % apart. */
private const val EXACT_TOLERANCE = 0.0005f

/** The second try, when no mode fits at [EXACT_TOLERANCE]. */
private const val CLOSE_TOLERANCE = 0.002f

/** 25, 29.97 and 30 fps. */
private val DOUBLE_FIRST_RATES = 24.5f..31f
private val DOUBLE_FIRST = listOf(2, 1, 3, 4, 5)
private val SINGLE_FIRST = listOf(1, 2, 3, 4, 5)
