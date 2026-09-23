package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleLogoOrText
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * Cinematic 16:9 backdrop header shared by [org.njarasoa.fijerena.feature.movie.MobileMovieDetailsScreen]
 * and [org.njarasoa.fijerena.feature.episode.MobileEpisodeSelectionScreen] — see
 * docs/plans/20260923_ui-ux-transitions-flow-uplift-plan.md, Phase 4 (3a). Replaces forcing a 2:3
 * vertical poster into a horizontal banner (severe cropping) with TMDB's actual landscape art,
 * falling back to the vertical poster — still center-cropped to 16:9, same as before this item —
 * only when no backdrop is available.
 */
@Composable
fun MobileDetailHero(
    title: String,
    backdropUrl: String?,
    posterUrl: String?,
    logoUrl: String?,
    thumbnailContentType: ThumbnailContentType,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
    ) {
        CinemaThumbnail(
            url = backdropUrl ?: posterUrl,
            fallbackLetter = title.firstOrNull(),
            contentType = thumbnailContentType,
            modifier = Modifier.fillMaxSize(),
        )
        // One brush for both scrims: dark at the very top (legible against a translucent status
        // bar / back button), clear through the middle where the art itself should read, dark
        // again at the bottom where the title overlay sits.
        val scrimBrush =
            remember {
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = CinemaAlpha.imageOverlayLight),
                    0.25f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1f to Color.Black.copy(alpha = CinemaAlpha.imageOverlay),
                )
            }
        Box(modifier = Modifier.matchParentSize().background(scrimBrush))
        TitleLogoOrText(
            contentDescription = title,
            logoUrl = logoUrl,
            modifier = Modifier.align(Alignment.BottomStart).padding(CinemaSpacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
        }
    }
}
