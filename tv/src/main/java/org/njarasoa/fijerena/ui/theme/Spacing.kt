package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * TV spacing (8dp grid): the shared spacing values plus TV safe margins. The TV keeps the base
 * steps in every Look and Feel (no [org.njarasoa.fijerena.core.ui.theme.UiStyle.spacing] density):
 * TV layouts have fixed sizes, and the focus tests depend on them.
 */
object Spacing {
    val none: Dp = CinemaSpacing.Base.none
    val xxxs: Dp = CinemaSpacing.Base.xxxs
    val xxs: Dp = CinemaSpacing.Base.xxs
    val xs: Dp = CinemaSpacing.Base.xs
    val sm: Dp = CinemaSpacing.Base.sm
    val md: Dp = CinemaSpacing.Base.md
    val lg: Dp = CinemaSpacing.Base.lg
    val xl: Dp = CinemaSpacing.Base.xl
    val xxl: Dp = CinemaSpacing.Base.xxl

    // TV safe margins (Google TV design language).
    val tvSafeMarginHorizontal: Dp = 56.dp
    val tvSafeMarginVertical: Dp = 32.dp

    /** TV safe area, horizontal (5% overscan), so the UI stays visible on TVs with overscan. */
    fun tvSafeHorizontal(screenWidthDp: Int): Dp = (screenWidthDp * 0.05).dp

    /** TV safe area, vertical (5% overscan), so the UI stays visible on TVs with overscan. */
    fun tvSafeVertical(screenHeightDp: Int): Dp = (screenHeightDp * 0.05).dp
}
