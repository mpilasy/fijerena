@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextDisabled
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextTertiary
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

@Composable
fun QualitySelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    val videoQualities = remember { viewModel.getVideoQualities() }
    var selectedIndex by remember { mutableStateOf(videoQualities.indexOfFirst { it.isSelected }.coerceAtLeast(-1)) }
    val focusRequesters = remember { List(videoQualities.size + 1) { FocusRequester() } } // +1 for "Auto" option
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Request focus on selected item or "Auto" option
    LaunchedEffect(Unit) {
        val focusIndex = if (selectedIndex >= 0) selectedIndex + 1 else 0 // +1 because "Auto" is first
        if (focusIndex in focusRequesters.indices) {
            focusRequesters[focusIndex].requestFocus()
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CinemaBackground.copy(alpha = CinemaAlpha.overlayHeavy)),
        contentAlignment = Alignment.Center,
    ) {
        TvGlassPanel(
            modifier =
                Modifier
                    .width(TvDimensions.dialogWidth)
                    .heightIn(max = screenHeight * 0.8f)
                    .padding(Spacing.xxl),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(Spacing.xxl)
                        .verticalScroll(rememberScrollState())
                        .focusProperties { exit = { FocusRequester.Cancel } },
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // Header
                Text(
                    text = "Select Quality",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )

                // "Auto" option
                val isAutoSelected = selectedIndex == -1
                Button(
                    onClick = {
                        selectedIndex = -1
                        viewModel.enableAutoQuality()
                        onDismiss()
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequesters[0])
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    selectedIndex = -1
                                }
                            },
                    colors =
                        ButtonDefaults.colors(
                            containerColor =
                                if (isAutoSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.tint)
                                } else {
                                    CinemaSurfaceVariant
                                },
                            contentColor = CinemaTextPrimary,
                            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim),
                            focusedContentColor = CinemaTextPrimary,
                        ),
                    border =
                        ButtonDefaults.border(
                            border =
                                Border(
                                    border =
                                        BorderStroke(
                                            width = if (isAutoSelected) TvDimensions.borderFocused else 0.dp,
                                            color = if (isAutoSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        ),
                                    shape = RoundedCornerShape(CinemaCornerRadius.small),
                                ),
                            focusedBorder =
                                Border(
                                    border =
                                        BorderStroke(
                                            width = TvDimensions.borderFocused,
                                            color = MaterialTheme.colorScheme.primary,
                                        ),
                                    shape = RoundedCornerShape(CinemaCornerRadius.small),
                                ),
                        ),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Auto (Adaptive)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isAutoSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = "Automatically adjust quality based on network",
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaTextTertiary,
                            )
                        }
                        if (isAutoSelected) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                if (videoQualities.isEmpty()) {
                    Text(
                        text = "No quality options available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaTextSecondary,
                        modifier = Modifier.padding(vertical = Spacing.md),
                    )
                } else {
                    // Quality list
                    videoQualities.forEachIndexed { index, quality ->
                        val isSelected = index == selectedIndex
                        Button(
                            onClick = {
                                selectedIndex = index
                                viewModel.selectVideoQuality(quality.groupIndex, quality.trackIndex)
                                onDismiss()
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[index + 1]) // +1 because "Auto" is first
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            selectedIndex = index
                                        }
                                    },
                            colors =
                                ButtonDefaults.colors(
                                    containerColor =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.tint)
                                        } else {
                                            CinemaSurfaceVariant
                                        },
                                    contentColor = CinemaTextPrimary,
                                    focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim),
                                    focusedContentColor = CinemaTextPrimary,
                                ),
                            border =
                                ButtonDefaults.border(
                                    border =
                                        Border(
                                            border =
                                                BorderStroke(
                                                    width = if (isSelected) TvDimensions.borderFocused else 0.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                ),
                                            shape = RoundedCornerShape(CinemaCornerRadius.small),
                                        ),
                                    focusedBorder =
                                        Border(
                                            border =
                                                BorderStroke(
                                                    width = TvDimensions.borderFocused,
                                                    color = MaterialTheme.colorScheme.primary,
                                                ),
                                            shape = RoundedCornerShape(CinemaCornerRadius.small),
                                        ),
                                ),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = quality.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (quality.isSelected) {
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Text(
                                    text = "${quality.width}×${quality.height} • ${quality.frameRate.toInt()}fps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextTertiary,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .align(CenterHorizontally)
                            .width(TvDimensions.selectionListWidth),
                ) {
                    Text("Cancel")
                }

                // Hint text
                Text(
                    text = "Use D-pad to navigate • OK to select • BACK to cancel",
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextDisabled,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
