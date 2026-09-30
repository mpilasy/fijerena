package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Avatar colours for user profiles. Fixed across themes: a profile is recognised by its colour,
 * so it must not change when the theme does. `profiles.colorIndex` stores an index into [palette],
 * never a colour value — see `docs/plans/20260929_live-sync-plan.md` → User profiles.
 */
object CinemaProfileColors {
    val palette: List<Color> =
        listOf(
            Color(0xFF1E88E5),
            Color(0xFFE53935),
            Color(0xFF43A047),
            Color(0xFFFB8C00),
            Color(0xFF8E24AA),
            Color(0xFF00ACC1),
            Color(0xFFD81B60),
            Color(0xFF6D4C41),
        )

    /** Initial on top of any [palette] colour. */
    val onAvatar: Color = Color(0xFFFFFFFF)

    /** Out-of-range indices wrap rather than crash — a synced index from a newer palette, say. */
    fun forIndex(index: Int): Color = palette[Math.floorMod(index, palette.size)]
}
