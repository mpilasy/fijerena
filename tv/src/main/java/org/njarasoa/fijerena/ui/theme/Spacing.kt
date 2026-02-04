package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * TV Spacing System (8dp Grid)
 * Re-exports shared spacing values and adds TV-specific safe margins.
 */
object Spacing {
    val xxxs: Dp = CinemaSpacing.xxxs
    val xxs: Dp = CinemaSpacing.xxs
    val xs: Dp = CinemaSpacing.xs
    val sm: Dp = CinemaSpacing.sm
    val md: Dp = CinemaSpacing.md
    val lg: Dp = CinemaSpacing.lg
    val xl: Dp = CinemaSpacing.xl
    val xxl: Dp = CinemaSpacing.xxl

    // TV Safe Margins (Google TV design language)
    val tvSafeMarginHorizontal: Dp = 56.dp
    val tvSafeMarginVertical: Dp = 32.dp

    /**
     * TV Safe Area - Horizontal (5% overscan)
     * Ensures UI remains visible on Sony TVs and other devices with overscan.
     *
     * @param screenWidthDp Screen width in dp
     * @return Safe horizontal padding in dp
     */
    fun tvSafeHorizontal(screenWidthDp: Int): Dp = (screenWidthDp * 0.05).dp

    /**
     * TV Safe Area - Vertical (5% overscan)
     * Ensures UI remains visible on Sony TVs and other devices with overscan.
     *
     * @param screenHeightDp Screen height in dp
     * @return Safe vertical padding in dp
     */
    fun tvSafeVertical(screenHeightDp: Int): Dp = (screenHeightDp * 0.05).dp
}
