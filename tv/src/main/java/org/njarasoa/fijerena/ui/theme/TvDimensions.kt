package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** TV component sizes for a 10-foot viewing distance. */
object TvDimensions {
    val safeMarginHorizontal: Dp = 56.dp
    val safeMarginVertical: Dp = 32.dp

    val formFieldWidth: Dp = 600.dp
    val dialogWidth: Dp = 600.dp
    val dialogWidthLarge: Dp = 700.dp
    val selectionListWidth: Dp = 200.dp
    val channelOverlayWidth: Dp = 320.dp
    val audioTrackSelectorWidth: Dp = 140.dp
    val settingsInputWidth: Dp = 200.dp

    val buttonHeight: Dp = 64.dp
    val cardHeight: Dp = 80.dp

    /**
     * Episode rows are taller than [cardHeight]: they carry a [posterHeight] thumbnail plus the
     * continue-watching badge, the title and a plot line. At 80.dp the poster was squeezed and the plot
     * was clipped.
     */
    val episodeCardHeight: Dp = 122.dp
    val trackItemHeight: Dp = 56.dp
    val moviePosterHeight: Dp = 60.dp
    val statsOverlayPanelHeight: Dp = 200.dp

    val iconSmall: Dp = 20.dp
    val iconMedium: Dp = 28.dp
    val iconLarge: Dp = 48.dp
    val iconXLarge: Dp = 64.dp

    // Icon button containers (player controls).
    val iconButtonSize: Dp = 72.dp

    /** An avatar on "Who's watching?": the screen's only content, so sized to be read across the room. */
    val profilePickerAvatarSize: Dp = 160.dp

    // Height of the TMDB logo art shown in place of the OSD's title text.
    val osdLogoHeight: Dp = 56.dp

    // Detail hero (the tv detail hero ui plan): its logo art reads larger than the OSD's.
    val heroLogoHeight: Dp = 96.dp

    // Share of the hero's width the text column takes, left-aligned: it stays inside the scrim's opaque
    // part (see CinemaAlpha.heroScrimFadeStop), not over the backdrop image.
    const val heroContentWidthFraction: Float = 0.55f

    // The hero backdrop's aspect ratio: the TV screen's own. TvDetailHero's minimum height is a share
    // of the height it gives.
    const val heroBackdropAspect: Float = 16f / 9f

    // Width of the hero's side slot (the series "Next Up" note), clear of the backdrop's scrim.
    val heroSideSlotWidth: Dp = 340.dp

    val progressIndicator: Dp = 48.dp
    val progressBar: Dp = 6.dp

    val epgTimeSlotWidth: Dp = 120.dp
    val epgRowHeight: Dp = 80.dp
    val epgChannelColumnWidth: Dp = 200.dp

    val liveDotSize: Dp = 12.dp
    val liveDotSmall: Dp = 10.dp
    val statsDotSize: Dp = 12.dp

    val posterWidth: Dp = 160.dp

    /** A followed team or league in the Sports section's row: half a poster wide, a square logo over its name. */
    val followedCardWidth: Dp = 80.dp
    val followedLogoSize: Dp = 56.dp
    val posterHeight: Dp = 90.dp
    val posterHeightLarge: Dp = 240.dp
    val contentTypeCardWidth: Dp = 340.dp

    /** "Jump Back In" shelf card — wide enough for a legible 16:9 thumbnail at 10-foot viewing. */
    val continueWatchingCardWidth: Dp = 280.dp

    /** A child profile's Home rows, cards and text, against the rest of the app (kids' Home plan). */
    const val kidsHomeScale: Float = 1.3f

    val borderDefault: Dp = 1.dp
    val borderFocused: Dp = 2.dp

    /** Thickness of the resume-progress bar along the bottom of stream and episode rows. */
    val resumeBarHeight: Dp = 5.dp
    val borderFocusedStats: Dp = 3.dp
    val borderThin: Dp = 0.5.dp

    val detailLabelWidth: Dp = 180.dp
}
