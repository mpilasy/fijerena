package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.njarasoa.fijerena.core.ui.components.GradientOverlay
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
 * Phase 5). Both screens build on this so their headers can't drift apart the way the current two
 * hand-rolled ones already have. See docs/plans/tv-detail-hero-ui-plan.md Phase 2.
 *
 * Meant as one non-focusable item in the screen's existing `LazyColumn` — it takes no focus and
 * intercepts no input itself, so the screen's own D-pad handling is untouched by adding it.
 *
 * [scoreChips] and [actions] are content slots rather than data lists (unlike the plan sketch's
 * `List<ScoreChip>`): a `data class` with the same shape as the [org.njarasoa.fijerena.core.ui.components.ScoreChip]
 * composable's parameters would collide with it by name, and a slot lets a caller with no second
 * score to show skip the row entirely instead of building a one-element list.
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
    scoreChips: @Composable (RowScope.() -> Unit)? = null,
    tagline: String? = null,
    plot: String? = null,
    sideSlot: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val palette = CinemaThemeHolder.current

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(TvDimensions.heroBackdropAspect)
                .background(palette.background),
    ) {
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Opaque at the left edge, transparent past heroScrimFadeStop: the text column always
        // sits on solid ground, whatever the backdrop looks like there.
        val scrimBrush =
            remember(palette.background) {
                Brush.horizontalGradient(
                    0f to palette.background,
                    CinemaAlpha.heroScrimFadeStop to palette.background.copy(alpha = 0f),
                )
            }
        Box(modifier = Modifier.fillMaxSize().background(scrimBrush))

        // Bottom scrim: keeps the action row legible over whatever is directly behind it,
        // independent of the horizontal one above.
        GradientOverlay(modifier = Modifier.fillMaxSize())

        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(TvDimensions.heroContentWidthFraction)
                    .padding(horizontal = TvDimensions.safeMarginHorizontal, vertical = TvDimensions.safeMarginVertical),
            verticalArrangement = Arrangement.Bottom,
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

            if (scoreChips != null) {
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    scoreChips()
                }
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
