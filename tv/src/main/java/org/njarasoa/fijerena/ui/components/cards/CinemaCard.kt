@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusable
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * Selectable Card - For Category/Content items
 * Interactive card with focus feedback for browsing content.
 * Use for categories, streams, movies, episodes, etc.
 */
@Composable
fun CinemaSelectableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.tvFocusable(
            focusScale = TvFocusTokens.focusedScale,
            borderWidth = TvFocusTokens.focusBorderWidth,
            cornerRadius = CornerRadius.medium
        ),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.focusedTint),
            focusedContentColor = CinemaTextPrimary
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.medium)),
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.focusedGlow),
                elevation = TvFocusTokens.glowElevation
            )
        )
    ) {
        Box(modifier = Modifier.padding(Spacing.md)) {
            content()
        }
    }
}

/**
 * Info Card - For Stats and metadata displays
 * Non-interactive card for displaying information.
 */
@Composable
fun CinemaInfoCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        onClick = { /* Non-interactive */ },
        modifier = modifier,
        colors = CardDefaults.colors(
            containerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.textLow),
            contentColor = CinemaTextPrimary
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.medium)),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = TvFocusTokens.focusBorderWidth,
                    color = CinemaSurfaceLight
                )
            )
        )
    ) {
        Box(modifier = Modifier.padding(Spacing.sm)) {
            content()
        }
    }
}

/**
 * Compact Selectable Card - For dense grids
 * Smaller padding variant for EPG grids or dense content layouts.
 */
@Composable
fun CinemaCompactCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.tvFocusable(
            focusScale = TvFocusTokens.focusedScale,
            borderWidth = TvFocusTokens.focusBorderWidth,
            cornerRadius = CornerRadius.small
        ),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.focusedTint),
            focusedContentColor = CinemaTextPrimary
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.small)),
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.focusedGlow),
                elevation = TvFocusTokens.glowElevation
            )
        )
    ) {
        Box(modifier = Modifier.padding(Spacing.xs)) {
            content()
        }
    }
}

/**
 * Standard Card - For content items with accent block.
 * Card with glow, 1.1x scale, and 2dp border on focus.
 */
@Composable
fun CinemaStandardCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.focusedTint),
            focusedContentColor = CinemaTextPrimary
        ),
        scale = CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScale,
            pressedScale = TvFocusTokens.pressedScale
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = TvFocusTokens.focusBorderWidth, color = CinemaAccentLight)
            )
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.medium)),
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.focusedGlow),
                elevation = TvFocusTokens.glowElevation
            )
        )
    ) {
        Box(modifier = Modifier.padding(Spacing.md)) {
            content()
        }
    }
}
