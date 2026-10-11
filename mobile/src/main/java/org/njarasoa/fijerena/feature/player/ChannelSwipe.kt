package org.njarasoa.fijerena.feature.player

import androidx.compose.ui.unit.dp

/** Drag distance, on either axis, that switches channel (vertical) or opens a channel sheet (horizontal). */
internal val CHANNEL_SWIPE_THRESHOLD = 56.dp

/** Share of the width and of the height, at each edge, where a drag starting there is ignored. */
internal const val CHANNEL_SWIPE_EDGE_FRACTION = 0.1f

/**
 * True when a drag starting at ([x], [y]) on a [width] × [height] player starts in the outer 10 %
 * of any edge. Those drags belong to the system (back from the sides, the notification shade from
 * the top, home from the bottom) or are stray edge touches, so they don't switch channel.
 */
internal fun startsInChannelSwipeEdge(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
): Boolean {
    val edgeX = width * CHANNEL_SWIPE_EDGE_FRACTION
    val edgeY = height * CHANNEL_SWIPE_EDGE_FRACTION
    return x < edgeX || x > width - edgeX || y < edgeY || y > height - edgeY
}
