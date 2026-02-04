package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TV-Specific Dimensions
 * Component sizes optimized for 10-foot viewing distance.
 */
object TvDimensions {
    // Safe margins
    val safeMarginHorizontal: Dp = 56.dp
    val safeMarginVertical: Dp = 32.dp

    // Form / Dialog widths
    val formFieldWidth: Dp = 600.dp
    val dialogWidth: Dp = 600.dp
    val dialogWidthLarge: Dp = 700.dp
    val selectionListWidth: Dp = 200.dp
    val audioTrackSelectorWidth: Dp = 140.dp
    val settingsInputWidth: Dp = 200.dp

    // Button / Card heights
    val buttonHeight: Dp = 64.dp
    val cardHeight: Dp = 80.dp
    val trackItemHeight: Dp = 56.dp
    val moviePosterHeight: Dp = 60.dp
    val statsOverlayPanelHeight: Dp = 200.dp

    // Icons
    val iconSmall: Dp = 20.dp
    val iconMedium: Dp = 28.dp
    val iconLarge: Dp = 48.dp
    val iconXLarge: Dp = 48.dp

    // Progress indicators
    val progressIndicator: Dp = 48.dp
    val progressBar: Dp = 6.dp

    // EPG (Electronic Program Guide)
    val epgTimeSlotWidth: Dp = 120.dp
    val epgRowHeight: Dp = 80.dp
    val epgTimeHeaderHeight: Dp = 60.dp

    // Dots / indicators
    val liveDotSize: Dp = 12.dp
    val liveDotSmall: Dp = 10.dp
    val statsDotSize: Dp = 12.dp

    // Borders
    val borderDefault: Dp = 1.dp
    val borderFocused: Dp = 2.dp
    val borderFocusedStats: Dp = 3.dp
    val borderThin: Dp = 0.5.dp
}
