@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusable
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Selectable Card - For Category/Content items
 * Interactive card with focus feedback for browsing content.
 * Use for categories, streams, movies, episodes, etc.
 *
 * @param onClick Callback when card is clicked
 * @param modifier Optional modifier
 * @param content Card content composable
 */
@Composable
fun CinemaSelectableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.tvFocusable(cornerRadius = CornerRadius.medium),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaAccent.copy(alpha = 0.2f),
            focusedContentColor = CinemaTextPrimary
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium))
    ) {
        Box(modifier = Modifier.padding(Spacing.md)) {
            content()
        }
    }
}

/**
 * Info Card - For Stats and metadata displays
 * Non-interactive card for displaying information.
 * Use for stats overlays, info panels, metadata, etc.
 *
 * @param modifier Optional modifier
 * @param content Card content composable
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
            containerColor = CinemaSurfaceVariant.copy(alpha = 0.6f),
            contentColor = CinemaTextPrimary
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium)),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = 2.dp,
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
 *
 * @param onClick Callback when card is clicked
 * @param modifier Optional modifier
 * @param content Card content composable
 */
@Composable
fun CinemaCompactCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.tvFocusable(cornerRadius = CornerRadius.small),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaAccent.copy(alpha = 0.2f),
            focusedContentColor = CinemaTextPrimary
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.small))
    ) {
        Box(modifier = Modifier.padding(Spacing.xs)) {
            content()
        }
    }
}
