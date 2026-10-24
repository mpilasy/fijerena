package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of the player's wait indicator (iris, reel, ring) and of the name badge above it. */
object WaitStyle {
    /** The indicator over the full-screen player. */
    val indicatorSize: Dp = 96.dp

    /** The indicator in the Live TV preview pane. */
    val compactIndicatorSize: Dp = 48.dp

    /** The indicator while the phone's controls are up: big enough for a channel logo in the ring. */
    val liftedIndicatorSize: Dp = 64.dp

    /** How far the phone controls' Play/Pause row moves down during a wait, clear of the centred indicator. */
    val controlsDrop: Dp = 80.dp

    /** The outer ring's line. */
    val stroke: Dp = 4.dp

    /** The line between two iris blades. */
    val seam: Dp = 1.5.dp

    /** The title or channel logo at the top left while the player waits (the TV banner's logo height). */
    val badgeLogoHeight: Dp = 56.dp

    /** The same badge on the phone. */
    val compactBadgeLogoHeight: Dp = 40.dp

    /** The soft disc behind the indicator, as a share of its size: it keeps it readable on any frame. */
    const val DISC_SCALE = 1.6f

    /** The channel logo inside the ring, as a share of the ring's size. */
    const val RING_CONTENT_SCALE = 0.6f

    /** One full turn of the iris (the blades also turn as they open). */
    const val IRIS_TURN_MS = 8_000

    /** The iris opening and closing while the player does not know yet how much it needs. */
    const val IRIS_BREATH_MS = 1_200

    /** One turn of the ring's arc while the player does not know yet how much it needs. */
    const val RING_TURN_MS = 1_100

    /** How fast the reel turns when data arrives as fast as the stream needs it. */
    const val REEL_TURNS_PER_SECOND = 0.6f
}
