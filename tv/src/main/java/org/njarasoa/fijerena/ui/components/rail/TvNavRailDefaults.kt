package org.njarasoa.fijerena.ui.components.rail

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.ui.theme.Spacing

/** Sizes, alphas and timings of [TvNavRail] (docs/plans/20261008_tv-nav-rail-plan.md → Design). */
internal object TvNavRailDefaults {
    /** The rail at rest: the screen's empty left margin, so it takes no content width. */
    val restWidth: Dp = Spacing.tvSafeMarginHorizontal

    /** Slid out over the content: icons and labels. */
    val expandedWidth: Dp = 200.dp

    /** Gap between the screen edge and the items, so a TV with overscan doesn't crop the icons. */
    val edgeInset: Dp = Spacing.xs

    /** An item's height, and its width at rest ([restWidth] less an [edgeInset] each side). */
    val itemSize: Dp = 40.dp

    /** Icons and labels of the items that are neither current nor focused, at rest and slid out. */
    const val restAlpha = CinemaAlpha.textDisabled
    const val expandedAlpha = CinemaAlpha.textMedium

    const val animationMs = CinemaAnimation.focusDurationMs

    /** How long the first-launch hint keeps the rail slid out. */
    const val hintDurationMs = 4_000L
}
