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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import org.njarasoa.fijerena.core.ui.components.TitleLogoOrText
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Shared header for the TV movie and series detail screens: full-bleed backdrop, one left-aligned
 * text column, an action row, and an optional top-right side slot (the series "Next Up" card in
 * Phase 5). Both screens build on this so their headers can't drift apart. See docs/plans/archive/20260902_tv-detail-hero-ui-plan.md Phase 2.
 *
 * Meant as one non-focusable item in the screen's existing `LazyColumn` — it takes no focus and
 * intercepts no input itself, so the screen's own D-pad handling is untouched by adding it.
 *
 * The rating is part of [metaLine] (plain "8.2/10"), not a boxed chip of its own, and the
 * backdrop fades into the screen background at the bottom and left instead of ending in a hard
 * edge (TV UI audit, #12).
 *
 * @param title Plain-text title, used only as the logo image's accessibility description —
 *   [titleFallback] draws its own text and is free to use a different string.
 * @param metaLine Already-formatted facts (year, content rating, runtime, genres, ...), joined
 *   here with " · "; blank entries are dropped rather than left as a dangling separator.
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
    sideSlot: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val palette = CinemaThemeHolder.current

    // BoxWithConstraints, not a fixed aspectRatio: aspectRatio at heroBackdropAspect works out to
    // exactly full screen height on a 16:9 TV (screen width / (16/9) == screen height), so
    // bottom-aligned content taller than that (a 3-line plot, a longer meta line) overflowed
    // above the top edge with nothing to scroll it into view, hiding the title entirely.
    // heightIn(min = ...) keeps the full-bleed look when content fits and grows the hero instead
    // of clipping when it doesn't — measured here, not from LocalConfiguration.screenWidthDp,
    // because that reads the platform density while this app applies its own UI-scale density
    // override (see LocalUiScale/UiScale.kt); the two disagreeing silently reproduced the exact
    // same overflow this was meant to fix.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Not the full 16:9 height: the tab row below has to peek in at the bottom, so it's seen
        // to be there and is composed for Down to land on (with the hero kept in view while
        // focused, a full-height hero left the tab row out of the tree and Down did nothing).
        val heroMinHeight = remember(maxWidth) { maxWidth / TvDimensions.heroBackdropAspect * HERO_HEIGHT_FRACTION }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = heroMinHeight)
                    .background(palette.background),
        ) {
            // matchParentSize, not fillMaxSize: this Box is sized by its content (heightIn(min)
            // above, growing for a plot/meta line taller than the aspect-ratio minimum) — inside
            // a LazyColumn item, the incoming height constraint is unbounded, so a plain
            // fillMaxSize() child sizes itself off that raw incoming constraint instead of the
            // Box's own resolved size. matchParentSize() is Box's own two-pass mechanism for
            // exactly this "background matches content-determined size" shape; using fillMaxSize
            // here silently reproduced the pre-fix full-screen-height overflow bug even with the
            // heightIn(min) fix in place, since the visible clipped area was still bounded by the
            // Column's own height instead of the (now taller) Box's.
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
                        CinemaAlpha.heroScrimFadeStop to palette.background.copy(alpha = 0f),
                    )
                }
            Box(modifier = Modifier.matchParentSize().background(scrimBrush))

            // Bottom scrim: keeps the action row legible, and reaches the solid background at the
            // hero's bottom edge so the backdrop runs into the tab row below without a seam.
            val bottomScrimBrush =
                remember(palette.background) {
                    Brush.verticalGradient(
                        BOTTOM_FADE_START to palette.background.copy(alpha = 0f),
                        1f to palette.background,
                    )
                }
            Box(modifier = Modifier.matchParentSize().background(bottomScrimBrush))

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
                    Text(text = meta, style = MaterialTheme.typography.bodyMedium, color = CinemaTextSecondary)
                }

                if (!tagline.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = CinemaTextSecondary,
                    )
                }

                if (!plot.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(Spacing.lg))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
            }

            if (sideSlot != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(horizontal = TvDimensions.safeMarginHorizontal, vertical = TvDimensions.safeMarginVertical),
                ) {
                    sideSlot()
                }
            }
        }
    }
}

/** The hero's share of a full 16:9 screen height: the rest is where the tab row peeks in. */
private const val HERO_HEIGHT_FRACTION = 0.82f

/** Where the hero's bottom scrim starts: clear above this fraction, solid background at the bottom. */
private const val BOTTOM_FADE_START = 0.4f

/**
 * Keeps a details screen's hero whole while focus is in it. On Android TV the default
 * [BringIntoViewSpec] pivots every focused child to about a third of the viewport, so focusing
 * Play scrolled the screen until the title or logo was cut off at the top (TV UI audit, #13).
 * While focus is inside the hero — [content] puts the modifier it is given on the hero — no
 * scroll is asked for and [scrollToTop] brings the hero back in full; everywhere else (the tab
 * row, episode cards, related titles) the default pivot stays.
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextSecondary,
            modifier = Modifier.width(DETAIL_LABEL_WIDTH),
        )
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
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = CinemaTextPrimary)
    }
}

/** Wide enough for the longest label in every language ("Anaran'ny fantsakana:"). */
private val DETAIL_LABEL_WIDTH = 180.dp

/**
 * A rating for the hero's meta line: "8.2/10" when it's a number, the provider's own text
 * otherwise. Plain text, no star — a star is the favourite button's icon (TV UI audit, X9).
 */
@Composable
fun ratingOutOfTen(rating: String): String {
    val value = formatRating(rating)
    return if (value.toDoubleOrNull() != null) stringResource(R.string.details_rating_out_of_ten, value) else value
}
