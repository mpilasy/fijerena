@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgChannelRow
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.TimeSlot
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EpgGridLayout(
    categoryName: String,
    channelRows: List<EpgChannelRow>,
    timeSlots: List<TimeSlot>,
    currentTimeSlot: Int,
    selectedDate: LocalDate,
    onProgramSelected: (EpgProgram, MediaItem) -> Unit,
    onChannelSelected: (String, String, String) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToNow: () -> Unit,
    onBack: () -> Unit
) {
    val configuration = LocalConfiguration.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = org.njarasoa.fijerena.ui.theme.Spacing.tvSafeMarginHorizontal,
                vertical = org.njarasoa.fijerena.ui.theme.Spacing.tvSafeMarginVertical
            )
    ) {
        // Header: Title, Date selector, Navigation buttons
        EpgHeader(
            categoryName = categoryName,
            selectedDate = selectedDate,
            onPreviousDay = onPreviousDay,
            onNextDay = onNextDay,
            onJumpToNow = onJumpToNow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.md)
        )

        if (channelRows.isEmpty()) {
            EmptyEpgMessage()
        } else {
            // Two-pane grid
            Row(modifier = Modifier.fillMaxSize()) {
                // Left: Channel list (20% width)
                ChannelListColumn(
                    channelRows = channelRows,
                    onChannelSelected = onChannelSelected,
                    modifier = Modifier
                        .weight(0.2f)
                        .fillMaxHeight()
                )

                Spacer(modifier = Modifier.width(Spacing.md))

                // Right: Scrollable time grid (80% width)
                TimeGridColumn(
                    channelRows = channelRows,
                    timeSlots = timeSlots,
                    currentTimeSlot = currentTimeSlot,
                    onProgramSelected = onProgramSelected,
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun EpgHeader(
    categoryName: String,
    selectedDate: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title and date
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TV Guide - $categoryName",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Date navigation
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = onPreviousDay) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Day")
            }
            Button(onClick = onJumpToNow) {
                Text("Now")
            }
            Button(onClick = onNextDay) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Day")
            }
        }
    }
}

@Composable
private fun EmptyEpgMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No EPG data available for these channels",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ChannelListColumn(
    channelRows: List<EpgChannelRow>,
    onChannelSelected: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberTvLazyListState()

    GlassPanel(modifier = modifier) {
        TvLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            items(channelRows.size) { index ->
                val row = channelRows[index]
                ChannelItem(
                    channel = row.channel,
                    onClick = {
                        onChannelSelected(
                            row.channel.id,
                            row.channel.name,
                            row.channel.categoryId
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ChannelItem(
    channel: MediaItem,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimensions.epgRowHeight)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = org.njarasoa.fijerena.ui.theme.CinemaSurface,
            focusedContainerColor = org.njarasoa.fijerena.ui.theme.CinemaAccent.copy(alpha = CinemaAlpha.tint)
        ),
        scale = CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleContent,
            pressedScale = TvFocusTokens.pressedScaleSubtle
        ),
        glow = CardDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = org.njarasoa.fijerena.ui.theme.CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                elevation = TvFocusTokens.focusShadowElevation
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.sm),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TimeGridColumn(
    channelRows: List<EpgChannelRow>,
    timeSlots: List<TimeSlot>,
    currentTimeSlot: Int,
    onProgramSelected: (EpgProgram, MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberLazyListState()
    val verticalScrollState = rememberTvLazyListState()

    // Auto-scroll to current time on load
    LaunchedEffect(currentTimeSlot) {
        if (currentTimeSlot > 0 && currentTimeSlot < timeSlots.size) {
            horizontalScrollState.animateScrollToItem(
                currentTimeSlot.coerceIn(0, timeSlots.lastIndex)
            )
        }
    }

    Column(modifier = modifier) {
        // Time header row
        TimeHeaderRow(
            timeSlots = timeSlots,
            scrollState = horizontalScrollState,
            currentTimeSlot = currentTimeSlot,
            modifier = Modifier
                .fillMaxWidth()
                .height(TvDimensions.epgTimeHeaderHeight)
        )

        Spacer(modifier = Modifier.height(Spacing.xxs))

        // Program grid
        TvLazyColumn(
            state = verticalScrollState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            items(channelRows.size) { rowIndex ->
                val row = channelRows[rowIndex]
                ProgramRow(
                    channelRow = row,
                    timeSlots = timeSlots,
                    scrollState = horizontalScrollState,
                    onProgramSelected = { program ->
                        onProgramSelected(program, row.channel)
                    }
                )
            }
        }
    }
}

@Composable
private fun TimeHeaderRow(
    timeSlots: List<TimeSlot>,
    scrollState: LazyListState,
    currentTimeSlot: Int,
    modifier: Modifier = Modifier
) {
    GlassPanel(modifier = modifier) {
        LazyRow(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false // Synchronized with program rows
        ) {
            items(timeSlots.size) { index ->
                val slot = timeSlots[index]
                val isCurrent = index == currentTimeSlot

                Box(
                    modifier = Modifier
                        .width(TvDimensions.epgTimeSlotWidth)
                        .fillMaxHeight()
                        .background(
                            if (isCurrent) org.njarasoa.fijerena.ui.theme.CinemaAccentDark
                            else androidx.compose.ui.graphics.Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                Text(
                    text = formatTime(slot.startTime),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                )
                }
            }
        }
    }
}

@Composable
private fun ProgramRow(
    channelRow: EpgChannelRow,
    timeSlots: List<TimeSlot>,
    scrollState: LazyListState,
    onProgramSelected: (EpgProgram) -> Unit
) {
    LazyRow(
        state = scrollState,
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimensions.epgRowHeight),
        userScrollEnabled = false // Synchronized with header row
    ) {
        items(channelRow.programs.size) { index ->
            val program = channelRow.programs[index]
            ProgramCell(
                program = program,
                onClick = { onProgramSelected(program) }
            )
        }
    }
}

@Composable
private fun ProgramCell(
    program: EpgProgram,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val isCurrent = isCurrentProgram(program)

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(calculateProgramWidth(program.duration))
            .fillMaxHeight()
            .padding(Spacing.xxs)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isCurrent) org.njarasoa.fijerena.ui.theme.CinemaAccentDark else org.njarasoa.fijerena.ui.theme.CinemaSurface,
            focusedContainerColor = org.njarasoa.fijerena.ui.theme.CinemaAccent.copy(alpha = CinemaAlpha.tint)
        ),
        scale = CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleContent,
            pressedScale = TvFocusTokens.pressedScaleSubtle
        ),
        glow = CardDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = org.njarasoa.fijerena.ui.theme.CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                elevation = TvFocusTokens.focusShadowElevation
            )
        )
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.sm)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = formatTime(program.startTime),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            Text(
                text = program.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Helper functions
private fun calculateProgramWidth(durationSeconds: Long): androidx.compose.ui.unit.Dp {
    // 2dp per minute
    val minutes = durationSeconds / 60
    return (minutes * 2).coerceAtLeast(120).toInt().dp
}

private fun isCurrentProgram(program: EpgProgram): Boolean {
    val now = System.currentTimeMillis() / 1000
    return now in program.startTime..program.endTime
}

private fun formatTime(timestampSeconds: Long): String {
    val instant = Instant.ofEpochSecond(timestampSeconds)
    val localTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    return localTime.format(DateTimeFormatter.ofPattern("HH:mm"))
}
