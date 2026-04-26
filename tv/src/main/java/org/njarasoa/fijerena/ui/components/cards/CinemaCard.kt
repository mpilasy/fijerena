@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GradientOverlay
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableContent
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

/**
 * Content Card - Image-first card for stream/movie items
 *
 * - CinemaThumbnail fills card as background
 * - GradientOverlay over image for text at bottom
 * - Title + subtitle over gradient at bottom-left
 * - Favorite star badge at top-right (optional)
 * - Watch progress bar at very bottom
 * - No card border in unfocused state
 * - Focus: tvFocusableContent (scale + shadow, no bright border)
 */
@Composable
fun CinemaContentCard(
    onClick: () -> Unit,
    title: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    contentType: ThumbnailContentType = ThumbnailContentType.DEFAULT,
    isFavorite: Boolean = false,
    watchProgress: Float = 0f,
) {
    Card(
        onClick = onClick,
        modifier = modifier.tvFocusableContent(cornerRadius = CornerRadius.medium),
        colors =
            CardDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = CinemaTextPrimary,
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.medium)),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background thumbnail
            CinemaThumbnail(
                url = thumbnailUrl,
                fallbackLetter = title.firstOrNull(),
                contentType = contentType,
                modifier = Modifier.fillMaxSize(),
            )

            // Gradient overlay at bottom for text
            GradientOverlay(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(TvDimensions.posterHeight)
                        .align(Alignment.BottomCenter),
            )

            // Favorite star at top-right
            if (isFavorite) {
                Text(
                    text = "\u2605",
                    color = CinemaAccent,
                    fontSize = 18.sp,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.xs),
                )
            }

            // Title + subtitle at bottom-left
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xxxs),
            ) {
                Text(
                    text = title,
                    color = CinemaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = CinemaTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Watch progress bar at very bottom
            if (watchProgress > 0f) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(TvDimensions.borderFocused)
                            .align(Alignment.BottomCenter),
                ) {
                    // Track
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(),
                    )
                    // Progress
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(watchProgress.coerceIn(0f, 1f))
                                .height(TvDimensions.borderFocused)
                                .align(Alignment.CenterStart),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .then(
                                        Modifier.padding(), // accent bar
                                    ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Selectable Card - For Category/Content items (borderless redesign)
 * Interactive card with focus feedback for browsing content.
 */
@Composable
fun CinemaSelectableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.tvFocusableContent(cornerRadius = CornerRadius.medium),
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
                focusedContentColor = CinemaTextPrimary,
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.medium)),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Box(modifier = Modifier.padding(Spacing.md)) {
            content()
        }
    }
}

/**
 * Info Card - For Stats and metadata displays (borderless redesign)
 */
@Composable
fun CinemaInfoCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = { /* Non-interactive */ },
        modifier = modifier,
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.textLow),
                contentColor = CinemaTextPrimary,
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.medium)),
    ) {
        Box(modifier = Modifier.padding(Spacing.sm)) {
            content()
        }
    }
}

/**
 * Compact Selectable Card - For dense grids (borderless redesign)
 */
@Composable
fun CinemaCompactCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.tvFocusableContent(cornerRadius = CornerRadius.small),
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
                focusedContentColor = CinemaTextPrimary,
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.small)),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Box(modifier = Modifier.padding(Spacing.xs)) {
            content()
        }
    }
}

/**
 * Standard Card - For content items with accent block (borderless redesign)
 */
@Composable
fun CinemaStandardCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
                focusedContentColor = CinemaTextPrimary,
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.medium)),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Box(modifier = Modifier.padding(Spacing.md)) {
            content()
        }
    }
}
