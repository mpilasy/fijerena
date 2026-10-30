package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Component sizes optimized for handheld touch interaction.
 */
object MobileDimensions {
    val safeMarginHorizontal: Dp = 16.dp
    val safeMarginVertical: Dp = 16.dp
    val buttonHeight: Dp = 56.dp
    val iconSmall: Dp = 20.dp
    val iconDefault: Dp = 24.dp
    val iconMedium: Dp = 28.dp
    val iconLarge: Dp = 32.dp
    val iconXLarge: Dp = 48.dp
    val iconPlayContainer: Dp = 56.dp
    val iconPlayIcon: Dp = 36.dp

    // Height of the TMDB logo art rendered in place of the OSD's big title text.
    val osdLogoHeight: Dp = 40.dp
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

    /** Thickness of the resume-progress bar along the bottom of stream and episode rows. */
    val resumeBarHeight: Dp = 5.dp

    /** Diameter of a swipe-reveal action button (see stream row swipe actions). */
    val swipeActionCircleSize: Dp = 40.dp

    val posterWidth: Dp = 72.dp
    val posterHeight: Dp = 40.dp
    val posterHeightLarge: Dp = 200.dp
    val streamCardHeight: Dp = 64.dp

    val epgProgramMinWidth: Dp = 140.dp
    val epgProgramHeight: Dp = 64.dp
    val epgChannelHeaderHeight: Dp = 44.dp

    /** A tab root's top bar: the section's name over the source line's 48 dp touch row. */
    val homeTopBarHeight: Dp = 84.dp

    /** Home's Continue Watching and channel cards: 16:9 art this wide, as on TV Home. */
    val homeCardWidth: Dp = 160.dp

    /** A film or show in a row of titles (PosterCard): 2:3 poster art this wide, about three a screen. */
    val posterCardWidth: Dp = 112.dp

    val streamThumbnailWidth: Dp = 96.dp
    val streamThumbnailHeight: Dp = 54.dp
    val channelSwipeThreshold: Dp = 56.dp
    val detailActionMaxWidth: Dp = 96.dp
    val detailLabelWidth: Dp = 112.dp
    val minTouchTarget: Dp = 48.dp

    /** A QR code a phone camera can read from another phone's screen. */
    val qrCodeSize: Dp = 280.dp

    /** The gear badge on the profile's avatar that opens the profile menu and Settings, and its icon. */
    val profileSettingsBadge: Dp = 14.dp
    val profileSettingsBadgeIcon: Dp = 10.dp
}
