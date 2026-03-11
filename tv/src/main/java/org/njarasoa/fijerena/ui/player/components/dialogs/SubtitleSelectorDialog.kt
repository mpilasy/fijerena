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
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextDisabled
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextTertiary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

@Composable
fun SubtitleSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val subtitleTracks = remember { viewModel.getSubtitleTracks() }
    var selectedIndex by remember { mutableStateOf(subtitleTracks.indexOfFirst { it.isSelected }.coerceAtLeast(-1)) }
    val focusRequesters = remember { List(subtitleTracks.size + 1) { FocusRequester() } } // +1 for "Off" option
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Request focus on selected item or "Off" option
    LaunchedEffect(Unit) {
        val focusIndex = if (selectedIndex >= 0) selectedIndex + 1 else 0 // +1 because "Off" is first
        if (focusIndex in focusRequesters.indices) {
            focusRequesters[focusIndex].requestFocus()
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground.copy(alpha = CinemaAlpha.overlayHeavy)),
        contentAlignment = Alignment.Center
    ) {
        TvGlassPanel(
            modifier = Modifier
                .width(TvDimensions.dialogWidth)
                .heightIn(max = screenHeight * 0.8f)
                .padding(Spacing.xxl)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.xxl)
                    .verticalScroll(rememberScrollState())
                    .focusProperties { exit = { FocusRequester.Cancel } },
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Header
                Text(
                    text = "Select Subtitles",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                val defaultColors = ButtonDefaults.colors(
                    containerColor = CinemaSurfaceVariant,
                    contentColor = CinemaTextPrimary,
                    focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim),
                    focusedContentColor = CinemaTextPrimary
                )
                val selectedColors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.tint),
                    contentColor = CinemaTextPrimary,
                    focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim),
                    focusedContentColor = CinemaTextPrimary
                )
                val focusedBorder = ButtonDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(
                            width = TvDimensions.borderFocused,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(CinemaCornerRadius.small)
                    )
                )
                val selectedBorder = ButtonDefaults.border(
                    border = Border(
                        border = BorderStroke(
                            width = TvDimensions.borderFocused,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(CinemaCornerRadius.small)
                    ),
                    focusedBorder = Border(
                        border = BorderStroke(
                            width = TvDimensions.borderFocused,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(CinemaCornerRadius.small)
                    )
                )

                // "Off" option
                val isOffSelected = selectedIndex == -1
                Button(
                    onClick = {
                        selectedIndex = -1
                        viewModel.disableSubtitles()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters[0])
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                selectedIndex = -1
                            }
                        },
                    colors = if (isOffSelected) selectedColors else defaultColors,
                    border = if (isOffSelected) selectedBorder else focusedBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Off",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isOffSelected) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (subtitleTracks.isEmpty()) {
                    Text(
                        text = "No subtitle tracks available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaTextSecondary,
                        modifier = Modifier.padding(vertical = Spacing.md)
                    )
                } else {
                    // Track list
                    subtitleTracks.forEachIndexed { index, track ->
                        val isSelected = index == selectedIndex
                        Button(
                            onClick = {
                                selectedIndex = index
                                viewModel.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[index + 1]) // +1 because "Off" is first
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        selectedIndex = index
                                    }
                                },
                            colors = if (isSelected) selectedColors else defaultColors,
                            border = if (isSelected) selectedBorder else focusedBorder
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = track.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (track.isSelected) {
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = track.mimeType.substringAfterLast("/").uppercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextTertiary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(CenterHorizontally)
                        .width(TvDimensions.selectionListWidth)
                ) {
                    Text("Cancel")
                }

                // Hint text
                Text(
                    text = "Use D-pad to navigate • OK to select • BACK to cancel",
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextDisabled,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
