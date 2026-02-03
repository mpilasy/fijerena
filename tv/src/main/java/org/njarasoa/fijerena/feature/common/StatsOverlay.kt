@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Position of the stats overlay on screen
 */
enum class QuadrantPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

/**
 * Movable stats overlay that displays developer information.
 * Can be moved between screen quadrants using D-pad.
 *
 * @param visible Whether the overlay is visible
 * @param stats Map of stat labels to values
 * @param modifier Modifier for the overlay
 * @param interactive Whether the overlay can be focused and moved (default true for player, false for other screens)
 */
@Composable
fun StatsOverlay(
    visible: Boolean,
    stats: Map<String, String>,
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    if (!visible || stats.isEmpty()) return

    var position by remember { mutableStateOf(QuadrantPosition.TOP_RIGHT) }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val configuration = LocalConfiguration.current

    // Calculate overlay size (1/4 of screen)
    val overlayWidth = (configuration.screenWidthDp * 0.25).dp
    val overlayHeight = (configuration.screenHeightDp * 0.25).dp

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Use Surface for interactive overlay, Box for non-interactive
        if (interactive) {
            Surface(
                modifier = Modifier
                    .width(overlayWidth)
                    .height(overlayHeight)
                    .align(getAlignment(position))
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionUp -> {
                                    position = when (position) {
                                        QuadrantPosition.BOTTOM_LEFT -> QuadrantPosition.TOP_LEFT
                                        QuadrantPosition.BOTTOM_RIGHT -> QuadrantPosition.TOP_RIGHT
                                        else -> position
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    position = when (position) {
                                        QuadrantPosition.TOP_LEFT -> QuadrantPosition.BOTTOM_LEFT
                                        QuadrantPosition.TOP_RIGHT -> QuadrantPosition.BOTTOM_RIGHT
                                        else -> position
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    position = when (position) {
                                        QuadrantPosition.TOP_RIGHT -> QuadrantPosition.TOP_LEFT
                                        QuadrantPosition.BOTTOM_RIGHT -> QuadrantPosition.BOTTOM_LEFT
                                        else -> position
                                    }
                                    true
                                }
                                Key.DirectionRight -> {
                                    position = when (position) {
                                        QuadrantPosition.TOP_LEFT -> QuadrantPosition.TOP_RIGHT
                                        QuadrantPosition.BOTTOM_LEFT -> QuadrantPosition.BOTTOM_RIGHT
                                        else -> position
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .border(
                        width = if (isFocused) 3.dp else 1.dp,
                        color = if (isFocused) CinemaAccentLight else CinemaSurfaceLight,
                        shape = MaterialTheme.shapes.medium
                    )
                    .background(
                        color = CinemaSurface.copy(alpha = 0.85f),
                        shape = MaterialTheme.shapes.medium
                    ),
                onClick = { /* Click to focus */ }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Text(
                        text = "Stats for Nerds",
                        style = MaterialTheme.typography.titleSmall,
                        color = CinemaAccent
                    )

                    stats.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$label:",
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaTextSecondary.copy(alpha = 0.87f)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaTextPrimary
                            )
                        }
                    }

                    if (isFocused) {
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Text(
                            text = "Use D-pad to move",
                            style = MaterialTheme.typography.labelSmall,
                            color = CinemaAccent.copy(alpha = 0.87f)
                        )
                    }
                }
            }
        } else {
            // Non-interactive: plain Box, not focusable
            Box(
                modifier = Modifier
                    .width(overlayWidth)
                    .height(overlayHeight)
                    .align(getAlignment(position))
                    .border(
                        width = 1.dp,
                        color = CinemaSurfaceLight,
                        shape = MaterialTheme.shapes.medium
                    )
                    .background(
                        color = CinemaSurface.copy(alpha = 0.85f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(Spacing.sm)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Text(
                        text = "Stats for Nerds",
                        style = MaterialTheme.typography.titleSmall,
                        color = CinemaAccent
                    )

                    stats.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$label:",
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaTextSecondary.copy(alpha = 0.87f)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaTextPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    // Auto-request focus when overlay becomes visible (only if interactive)
    if (interactive) {
        LaunchedEffect(visible) {
            if (visible) {
                focusRequester.requestFocus()
            }
        }
    }
}

/**
 * Get Box alignment for quadrant position
 */
private fun getAlignment(position: QuadrantPosition): Alignment {
    return when (position) {
        QuadrantPosition.TOP_LEFT -> Alignment.TopStart
        QuadrantPosition.TOP_RIGHT -> Alignment.TopEnd
        QuadrantPosition.BOTTOM_LEFT -> Alignment.BottomStart
        QuadrantPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
}
