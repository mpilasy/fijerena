package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mobile-Specific Dimensions
 * Component sizes optimized for handheld touch interaction.
 */
object MobileDimensions {
    val safeMarginHorizontal: Dp = 16.dp
    val safeMarginVertical: Dp = 16.dp
    val buttonHeight: Dp = 56.dp
    val contentTypeCardHeight: Dp = 100.dp
    val iconSmall: Dp = 20.dp
    val iconDefault: Dp = 24.dp
    val iconMedium: Dp = 28.dp
    val iconLarge: Dp = 32.dp
    val iconXLarge: Dp = 48.dp
    val iconPlayContainer: Dp = 56.dp
    val iconPlayIcon: Dp = 36.dp
    val progressIndicatorSmall: Dp = 20.dp
    val progressIndicatorDefault: Dp = 24.dp
    val progressIndicatorLarge: Dp = 48.dp
    val liveDotSize: Dp = 8.dp
    val statsOverlayMaxWidth: Dp = 320.dp
    val dividerThin: Dp = 0.5.dp
    val strokeWidth: Dp = 2.dp

    /** Default elevation for [org.njarasoa.fijerena.ui.components.cards.CinemaCard] list rows —
     * enough shadow that a row reads as a raised card against the screen behind it, not a flat
     * table cell. */
    val cardRowElevation: Dp = 2.dp

    /** VOD scrubber thumb — M3's default is a 4dp-wide bar, which reads as a thin needle rather
     * than a draggable knob. Round (equal width/height) and sized for a thumb, not a tick mark. */
    val playerScrubberThumbSize: Dp = 20.dp

    /** Content Type Selection hero cards — resting/pressed elevation so a card reads as a raised
     * surface, not a flat gradient rectangle. */
    val heroCardElevation: Dp = 8.dp
    val heroCardPressedElevation: Dp = 3.dp

    /** Thickness of the resume-progress bar along the bottom of stream and episode rows. */
    val resumeBarHeight: Dp = 5.dp

    // Poster / Thumbnail
    val posterWidth: Dp = 72.dp
    val posterHeight: Dp = 40.dp
    val posterHeightLarge: Dp = 200.dp
    val streamCardHeight: Dp = 64.dp

    // EPG
    val epgProgramMinWidth: Dp = 140.dp
    val epgProgramHeight: Dp = 64.dp
    val epgChannelHeaderHeight: Dp = 44.dp
}
