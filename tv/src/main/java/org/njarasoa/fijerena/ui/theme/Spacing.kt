package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing System (8dp Grid)
 * Provides consistent spacing values throughout the app.
 * All values follow the 8dp grid system for visual consistency.
 */
object Spacing {
    val xxxs: Dp = 2.dp   // Minimal gaps
    val xxs: Dp = 4.dp    // Tight spacing
    val xs: Dp = 8.dp     // Base unit (8dp grid)
    val sm: Dp = 12.dp    // Small spacing
    val md: Dp = 16.dp    // Standard spacing (most common)
    val lg: Dp = 24.dp    // Section spacing
    val xl: Dp = 32.dp    // Large gaps
    val xxl: Dp = 48.dp   // Extra large gaps

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
