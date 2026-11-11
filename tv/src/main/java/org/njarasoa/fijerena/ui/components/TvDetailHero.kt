package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.TitleLogoOrText
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Shared header for the TV film and series detail screens: full-bleed backdrop, one left-aligned
 * text column, an action row, and an optional top-right side slot (the series "Next Up" note).
 * Both screens use it, so their headers cannot drift apart (the tv detail hero ui plan, Phase 2).
 *
 * One non-focusable item in the screen's `LazyColumn`: it takes no focus and no input, so the
 * screen's own D-pad handling is unchanged.
 *
 * The rating is part of [metaLine] (plain "8.2/10"), not a chip, and the backdrop fades into the
 * screen background at the bottom and left (TV UI audit, #12).
 *
 * @param title Plain-text title, used only as the logo image's accessibility description;
 *   [titleFallback] draws its own text.
 * @param metaLine Formatted facts (year, content rating, runtime, genres, ...), joined here with
 *   " · "; blank entries are dropped.
 * @param note A line under the plot: the details are the saved copy (`isSavedCopy`).
 */
@Composable
fun TvDetailHero(
    title: String,
    backdropUrl: String?,
    logoUrl: String?,
    titleFallback: @Composable () -> Unit,
    metaLine: List<String>,
    modifier: Modifier = Modifier,
    tagline: String? = null,
    plot: String? = null,
    note: String? = null,
    sideSlot: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val palette = CinemaTheme.colors

    // BoxWithConstraints, not a fixed aspectRatio: at heroBackdropAspect that is exactly the screen
    // height on a 16:9 TV, so taller content (a 3-line plot) went above the top edge and hid the title.
    // heightIn(min = ...) keeps the full-bleed look and grows the hero when needed. Measured here, not
    // from LocalConfiguration.screenWidthDp, which ignores the app's own UI-scale density (see
    // UiScale.kt) and gave the same overflow.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Not the full 16:9 height: the tab row below must show at the bottom, so it is seen and composed
        // for Down to land on (with a full-height hero kept in view, Down did nothing).
        val heroMinHeight = remember(maxWidth) { maxWidth / TvDimensions.heroBackdropAspect * HERO_HEIGHT_FRACTION }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = heroMinHeight)
                    .background(palette.background),
        ) {
            // matchParentSize, not fillMaxSize: this Box is sized by its content (heightIn(min) above). In a
            // LazyColumn item the height constraint is unbounded, so fillMaxSize() sizes off that constraint,
            // not the Box's own size, and brought the overflow back.
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier.matchParentSize(),
                )
            }

            // Opaque at the left edge, transparent past heroScrimFadeStop: the text column
            // always sits on solid ground, whatever the backdrop looks like there.
            val scrimBrush =
                remember(palette.background) {
                    Brush.horizontalGradient(
                        0f to palette.background,
                        CinemaAlpha.heroScrimFadeStop to palette.background.copy(alpha = CinemaAlpha.none),
                    )
                }
            Box(modifier = Modifier.matchParentSize().background(scrimBrush))

            // Bottom scrim: keeps the action row legible, and reaches the solid background at the
            // hero's bottom edge so the backdrop runs into the tab row below without a seam.
            val bottomScrimBrush =
                remember(palette.background) {
                    Brush.verticalGradient(
                        BOTTOM_FADE_START to palette.background.copy(alpha = CinemaAlpha.none),
                        1f to palette.background,
                    )
                }
            Box(modifier = Modifier.matchParentSize().background(bottomScrimBrush))

            // Top scrim: the clock (top end, TvClockOverlay) stays legible on bright art.
            val topScrimBrush =
                remember(palette.background) {
                    Brush.verticalGradient(
                        0f to palette.background.copy(alpha = CinemaAlpha.scrim),
                        TOP_FADE_END to palette.background.copy(alpha = CinemaAlpha.none),
                    )
                }
            Box(modifier = Modifier.matchParentSize().background(topScrimBrush))

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(TvDimensions.heroContentWidthFraction)
                        .padding(horizontal = TvDimensions.safeMarginHorizontal, vertical = TvDimensions.safeMarginVertical),
            ) {
                TitleLogoOrText(
                    contentDescription = title,
                    logoUrl = logoUrl,
                    logoHeight = TvDimensions.heroLogoHeight,
                    fallback = titleFallback,
                )

                val meta = remember(metaLine) { metaLine.filter { it.isNotBlank() }.joinToString(" · ") }
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.sm))
                    // On the art past the scrim's solid part: the halo keeps it legible on bright frames.
                    TvBackdropText(text = meta, tone = TextTone.Secondary)
                }

                if (!tagline.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.md))
                    TvBackdropText(text = tagline, tone = TextTone.Secondary, italic = true)
                }

                if (!plot.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    TvBackdropText(text = plot, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }

                if (note != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    CinemaText(text = note, role = TextRole.BodySmall, tone = TextTone.Secondary)
                }

                Spacer(Modifier.height(Spacing.lg))
                // The text keeps to the column; the buttons may run past it: at a large Text & grid size the column
                // was narrower than Play plus four icon buttons, and More was cut off (TV UI audit).
                Row(
                    modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
            }

            if (sideSlot != null) {
                // A soft scrim behind the side slot, from its end edge: its text stays legible on
                // busy or bright art, as the left scrim does for the text column.
                val sideScrimColors =
                    remember(palette.background) {
                        listOf(
                            palette.background.copy(alpha = CinemaAlpha.osdTextScrimDeep),
                            palette.background.copy(alpha = CinemaAlpha.scrim),
                            palette.background.copy(alpha = CinemaAlpha.none),
                        )
                    }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(horizontal = TvDimensions.safeMarginHorizontal, vertical = TvDimensions.safeMarginVertical)
                            .belowTvClock()
                            .drawBehind {
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors = sideScrimColors,
                                            center = Offset(size.width, size.height / 2),
                                            radius = size.width * SIDE_SCRIM_RADIUS,
                                        ),
                                    topLeft = Offset(size.width - size.width * SIDE_SCRIM_RADIUS, -size.height),
                                    size = Size(size.width * SIDE_SCRIM_RADIUS * 2, size.height * 3),
                                )
                            },
                ) {
                    sideSlot()
                }
            }
        }
    }
}

/** The hero's share of a full 16:9 screen height: the rest is where the tab row peeks in. */
private const val HERO_HEIGHT_FRACTION = 0.82f

/** The radius of the scrim behind the hero's side slot, as a share of the slot's width. */
private const val SIDE_SCRIM_RADIUS = 1.1f

/** Where the hero's top scrim ends: the clock's band, clear below this fraction. */
private const val TOP_FADE_END = 0.25f

/** Where the hero's bottom scrim starts: clear above this fraction, solid background at the bottom. */
private const val BOTTOM_FADE_START = 0.4f

/**
 * Keeps a details screen's hero whole while focus is in it. On Android TV the default
 * [BringIntoViewSpec] pivots each focused child to about a third of the viewport, so focusing Play
 * cut the title off at the top (TV UI audit, #13). While focus is inside the hero ([content] puts
 * the given modifier on it) no scroll is asked for, and [scrollToTop] brings the hero back in full;
 * elsewhere (tab row, episode cards, related titles) the default pivot stays.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeepHeroInView(
    scrollToTop: suspend () -> Unit,
    content: @Composable (heroModifier: Modifier) -> Unit,
) {
    val defaultSpec = LocalBringIntoViewSpec.current
    // Read by the spec straight from the state, not through recomposition: the scroll request
    // comes in the same frame as the focus change.
    val heroFocused = remember { mutableStateOf(false) }
    val spec =
        remember(defaultSpec) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float,
                ): Float =
                    when {
                        !heroFocused.value -> defaultSpec.calculateScrollDistance(offset, size, containerSize)

                        // Only as far as it takes to show a button that is off screen (a hero
                        // taller than the screen), never the pivot.
                        offset < 0f -> offset

                        offset + size > containerSize -> offset + size - containerSize

                        else -> 0f
                    }
            }
        }
    val focused by heroFocused
    LaunchedEffect(focused) {
        if (focused) scrollToTop()
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        content(Modifier.onFocusChanged { heroFocused.value = it.hasFocus })
    }
}

/**
 * One line of a details screen's Details tab: the label in a fixed column, so every value starts
 * at the same place (TV UI audit, #12). [value] is free-form for the stream-name picker.
 */
@Composable
fun TvDetailRow(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        CinemaText(text = label, tone = TextTone.Secondary, modifier = Modifier.width(DETAIL_LABEL_WIDTH))
        Spacer(Modifier.width(Spacing.md))
        value()
    }
}

/** [TvDetailRow] with plain text for its value. */
@Composable
fun TvDetailRow(
    label: String,
    value: String,
) {
    TvDetailRow(label = label) {
        CinemaText(text = value)
    }
}

/** Wide enough for the longest label in every language ("Anaran'ny fantsakana:"). */
private val DETAIL_LABEL_WIDTH = TvDimensions.detailLabelWidth

/**
 * A rating for the hero's meta line: "8.2/10" when it's a number, the provider's own text
 * otherwise. Plain text, no star — a star is the favourite button's icon (TV UI audit, X9).
 */
@Composable
fun ratingOutOfTen(rating: String): String {
    val value = formatRating(rating)
    return if (value.toDoubleOrNull() != null) stringResource(R.string.details_rating_out_of_ten, value) else value
}
