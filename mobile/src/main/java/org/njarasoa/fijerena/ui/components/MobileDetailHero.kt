package org.njarasoa.fijerena.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleLogoOrText
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme

/**
 * The film or show page's opening backdrop, shared by [org.njarasoa.fijerena.feature.movie.MobileMovieDetailsScreen]
 * and [org.njarasoa.fijerena.feature.episode.MobileEpisodeSelectionScreen] (TV visual refresh,
 * Phase 5): the full width of the screen ([bleed] beyond the list's side padding), running up
 * under the status bar and the transparent top bar, fading into the page at the bottom, the title
 * large over it (TMDB's logo art, else the title in the Display style). TMDB's landscape art,
 * else the vertical poster centre-cropped. [onCovered] says whether the backdrop has scrolled up
 * under the top bar, so the bar can turn solid and show the title.
 */
@Composable
fun MobileDetailHero(
    title: String,
    backdropUrl: String?,
    posterUrl: String?,
    logoUrl: String?,
    thumbnailContentType: ThumbnailContentType,
    bleed: Dp,
    onCovered: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val barBottomPx = WindowInsets.statusBars.getTop(density) + with(density) { TopAppBarDefaults.TopAppBarExpandedHeight.toPx() }
    Box(
        modifier =
            modifier
                .layout { measurable, constraints ->
                    val extra = bleed.roundToPx()
                    val width = constraints.maxWidth + 2 * extra
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
                }.fillMaxWidth()
                .aspectRatio(HERO_ASPECT)
                .onGloballyPositioned { onCovered(it.positionInRoot().y + it.size.height < barBottomPx) },
    ) {
        CinemaThumbnail(
            url = backdropUrl ?: posterUrl,
            fallbackLetter = title.firstOrNull(),
            contentType = thumbnailContentType,
            modifier = Modifier.fillMaxSize(),
        )
        // Dark at the top behind the status bar and the back button, clear through the middle
        // where the art reads, then the page's own background at the very bottom, so the picture
        // fades into the page instead of ending at an edge.
        val c = CinemaTheme.colors
        val scrimBrush =
            remember(c) {
                Brush.verticalGradient(
                    0f to c.background.copy(alpha = CinemaAlpha.imageOverlayLight),
                    0.25f to c.clear,
                    0.5f to c.clear,
                    0.85f to c.background.copy(alpha = CinemaAlpha.imageOverlay),
                    1f to c.background,
                )
            }
        Box(modifier = Modifier.matchParentSize().background(scrimBrush))
        TitleLogoOrText(
            contentDescription = title,
            logoUrl = logoUrl,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = bleed, vertical = CinemaSpacing.md),
        ) {
            CinemaText(text = title, role = TextRole.Display, maxLines = 3)
        }
    }
}

/** The film or show page's top bar: clear over the backdrop, the surface colour once [solid]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun detailTopBarColors(solid: Boolean): TopAppBarColors {
    val c = CinemaTheme.colors
    val container by animateColorAsState(if (solid) c.surface else c.clear, label = "detail_top_bar")
    return TopAppBarDefaults.topAppBarColors(containerColor = container, scrolledContainerColor = container)
}

/**
 * The back button's backing on the film or show page: a dark disc while the top bar is clear, so
 * the arrow reads over bright art; nothing once the bar is [solid].
 */
@Composable
fun Modifier.detailBackBacking(solid: Boolean): Modifier =
    if (solid) this else background(CinemaTheme.colors.scrim, CinemaTheme.shapes.control)

/** Taller than 16:9, so the picture reaches up under the status bar and the top bar. */
private const val HERO_ASPECT = 4f / 3f
