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
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.player.utils.formatTime
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
fun ChapterSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val chapters = remember { viewModel.getChapters() }
    val currentPosition = when (val ps = viewModel.playbackState.value) {
        is PlaybackState.Playing -> ps.position
        is PlaybackState.Paused -> ps.position
        else -> 0L
    }
    val currentChapterIndex = chapters.indexOfLast { it.startTimeMs <= currentPosition }.coerceAtLeast(0)
    var selectedIndex by remember { mutableStateOf(currentChapterIndex) }
    val focusRequesters = remember { List(chapters.size) { FocusRequester() } }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    LaunchedEffect(Unit) {
        if (currentChapterIndex in focusRequesters.indices) {
            focusRequesters[currentChapterIndex].requestFocus()
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "Chapters",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                if (chapters.isEmpty()) {
                    Text(
                        text = "No chapters available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaTextSecondary,
                        modifier = Modifier.padding(vertical = Spacing.md)
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(CenterHorizontally)
                    ) {
                        Text("Close")
                    }
                } else {
                    chapters.forEachIndexed { index, chapter ->
                        val isSelected = index == selectedIndex
                        val isCurrent = index == currentChapterIndex
                        Button(
                            onClick = {
                                viewModel.seekTo(chapter.startTimeMs)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[index])
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        selectedIndex = index
                                    }
                                },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.tint)
                                else CinemaSurfaceVariant,
                                contentColor = CinemaTextPrimary,
                                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim),
                                focusedContentColor = CinemaTextPrimary
                            ),
                            border = ButtonDefaults.border(
                                border = Border(
                                    border = BorderStroke(
                                        width = if (isSelected) TvDimensions.borderFocused else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
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
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                                ) {
                                    Text(
                                        text = chapter.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = formatTime(chapter.startTimeMs),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CinemaTextTertiary
                                    )
                                }
                                if (isCurrent) {
                                    Text(
                                        text = "Now",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(CenterHorizontally)
                            .width(TvDimensions.selectionListWidth)
                    ) {
                        Text("Cancel")
                    }
                }

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
