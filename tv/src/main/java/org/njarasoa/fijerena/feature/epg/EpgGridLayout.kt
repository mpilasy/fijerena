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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.tv.foundation.lazy.list.TvLazyListState
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
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.player.model.TimeSlot
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Pre-compiled formatter — locale-aware full date (e.g., "Thursday, February 27, 2026")
private val EPG_DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

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
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    searchQuery: String = "",
    searchResults: List<EpgViewModel.EpgSearchResult> = emptyList(),
    onSearchQueryChanged: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onBack: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val scale = LocalUiScale.current
    var isSearchActive by remember { mutableStateOf(false) }

    // Shared "now" timestamp refreshed every 60s — avoids per-cell System.currentTimeMillis() calls
    var nowEpochSeconds by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowEpochSeconds = System.currentTimeMillis() / 1000
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = Spacing.tvSafeMarginHorizontal,
                vertical = Spacing.tvSafeMarginVertical
            )
    ) {
        // Header: Title, Date selector, Navigation buttons
        EpgHeader(
            categoryName = categoryName,
            selectedDate = selectedDate,
            onPreviousDay = onPreviousDay,
            onNextDay = onNextDay,
            onJumpToNow = onJumpToNow,
            onRefresh = onRefresh,
            isRefreshing = isRefreshing,
            isSearchActive = isSearchActive,
            onSearchToggle = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) onClearSearch()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.md.scaled(scale))
        )

        if (isSearchActive) {
            // Search mode
            EpgSearchContent(
                searchQuery = searchQuery,
                searchResults = searchResults,
                onSearchQueryChanged = onSearchQueryChanged,
                onProgramSelected = onProgramSelected
            )
        } else if (channelRows.isEmpty()) {
            EmptyEpgMessage()
        } else {
            // Synchronized vertical scroll states for two-pane grid
            val channelListState = rememberTvLazyListState()
            val programGridState = rememberTvLazyListState()

            // Sync channel list → program grid
            LaunchedEffect(channelListState) {
                snapshotFlow {
                    channelListState.firstVisibleItemIndex to
                            channelListState.firstVisibleItemScrollOffset
                }.collectLatest { (index, offset) ->
                    if (programGridState.firstVisibleItemIndex != index ||
                        programGridState.firstVisibleItemScrollOffset != offset
                    ) {
                        programGridState.scrollToItem(index, offset)
                    }
                }
            }

            // Sync program grid → channel list
            LaunchedEffect(programGridState) {
                snapshotFlow {
                    programGridState.firstVisibleItemIndex to
                            programGridState.firstVisibleItemScrollOffset
                }.collectLatest { (index, offset) ->
                    if (channelListState.firstVisibleItemIndex != index ||
                        channelListState.firstVisibleItemScrollOffset != offset
                    ) {
                        channelListState.scrollToItem(index, offset)
                    }
                }
            }

            // Two-pane grid
            Row(modifier = Modifier.fillMaxSize()) {
                // Left: Channel list (20% width)
                ChannelListColumn(
                    channelRows = channelRows,
                    onChannelSelected = onChannelSelected,
                    listState = channelListState,
                    modifier = Modifier
                        .weight(0.2f)
                        .fillMaxHeight()
                )

                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))

                // Right: Scrollable time grid (80% width)
                TimeGridColumn(
                    channelRows = channelRows,
                    timeSlots = timeSlots,
                    currentTimeSlot = currentTimeSlot,
                    nowEpochSeconds = nowEpochSeconds,
                    onProgramSelected = onProgramSelected,
                    verticalScrollState = programGridState,
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
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    isSearchActive: Boolean = false,
    onSearchToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scale = LocalUiScale.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title and date
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TV Guide - $categoryName",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)
                ),
                color = CinemaTextPrimary
            )
            Text(
                text = selectedDate.format(EPG_DATE_FORMATTER),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                ),
                color = CinemaTextSecondary
            )
        }

        // Date navigation + refresh
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
            Button(onClick = onPreviousDay) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Day")
            }
            Button(onClick = onJumpToNow) {
                Text("Now")
            }
            Button(onClick = onNextDay) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Day")
            }
            Button(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                        strokeWidth = TvDimensions.borderDefault,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
            Button(onClick = onSearchToggle) {
                Icon(
                    if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isSearchActive) "Close Search" else "Search"
                )
            }
        }
    }
}

@Composable
private fun EmptyEpgMessage() {
    val scale = LocalUiScale.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No EPG data available for these channels",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize.scaled(scale)
            ),
            color = CinemaTextSecondary
        )
    }
}

@Composable
private fun ChannelListColumn(
    channelRows: List<EpgChannelRow>,
    onChannelSelected: (String, String, String) -> Unit,
    listState: TvLazyListState,
    modifier: Modifier = Modifier
) {
    val scale = LocalUiScale.current

    GlassPanel(modifier = modifier) {
        TvLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs.scaled(scale))
        ) {
            items(
                count = channelRows.size,
                key = { channelRows[it].channel.id },
                contentType = { "channel" }
            ) { index ->
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
    val scale = LocalUiScale.current

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimensions.epgRowHeight.scaled(scale))
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = org.njarasoa.fijerena.ui.theme.CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = org.njarasoa.fijerena.ui.theme.CinemaAccent.copy(alpha = CinemaAlpha.tint),
            focusedContentColor = CinemaTextPrimary
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
                .padding(Spacing.sm.scaled(scale)),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                ),
                color = CinemaTextPrimary,
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
    nowEpochSeconds: Long,
    onProgramSelected: (EpgProgram, MediaItem) -> Unit,
    verticalScrollState: TvLazyListState,
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberLazyListState()

    // Auto-scroll to current time on load
    LaunchedEffect(currentTimeSlot) {
        if (currentTimeSlot > 0 && currentTimeSlot < timeSlots.size) {
            horizontalScrollState.animateScrollToItem(
                currentTimeSlot.coerceIn(0, timeSlots.lastIndex)
            )
        }
    }

    val scale = LocalUiScale.current

    Column(modifier = modifier) {
        // Time header row
        TimeHeaderRow(
            timeSlots = timeSlots,
            scrollState = horizontalScrollState,
            currentTimeSlot = currentTimeSlot,
            modifier = Modifier
                .fillMaxWidth()
                .height(TvDimensions.epgTimeHeaderHeight.scaled(scale))
        )

        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))

        // Program grid
        TvLazyColumn(
            state = verticalScrollState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs.scaled(scale))
        ) {
            items(
                count = channelRows.size,
                key = { channelRows[it].channel.id },
                contentType = { "program_row" }
            ) { rowIndex ->
                val row = channelRows[rowIndex]
                ProgramRow(
                    channelRow = row,
                    timeSlots = timeSlots,
                    nowEpochSeconds = nowEpochSeconds,
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
    val scale = LocalUiScale.current

    GlassPanel(modifier = modifier) {
        LazyRow(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false // Synchronized with program rows
        ) {
            items(timeSlots.size, contentType = { "time_slot" }) { index ->
                val slot = timeSlots[index]
                val isCurrent = index == currentTimeSlot

                Box(
                    modifier = Modifier
                        .width(TvDimensions.epgTimeSlotWidth.scaled(scale))
                        .fillMaxHeight()
                        .background(
                            if (isCurrent) org.njarasoa.fijerena.ui.theme.CinemaAccentDark
                            else androidx.compose.ui.graphics.Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                Text(
                    text = TimeFormat.formatTime(slot.startTime),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = MaterialTheme.typography.labelMedium.fontSize.scaled(scale)
                    ),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) CinemaTextPrimary else CinemaTextSecondary
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
    nowEpochSeconds: Long,
    scrollState: LazyListState,
    onProgramSelected: (EpgProgram) -> Unit
) {
    val scale = LocalUiScale.current

    LazyRow(
        state = scrollState,
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimensions.epgRowHeight.scaled(scale)),
        userScrollEnabled = false // Synchronized with header row
    ) {
        items(
            count = channelRow.programs.size,
            key = { channelRow.programs[it].startTime },
            contentType = { "program" }
        ) { index ->
            val program = channelRow.programs[index]
            ProgramCell(
                program = program,
                nowEpochSeconds = nowEpochSeconds,
                onClick = { onProgramSelected(program) }
            )
        }
    }
}

@Composable
private fun ProgramCell(
    program: EpgProgram,
    nowEpochSeconds: Long,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    // Use shared nowEpochSeconds instead of per-cell System.currentTimeMillis()
    val isCurrent = nowEpochSeconds in program.startTime..program.endTime
    val scale = LocalUiScale.current

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(calculateProgramWidth(program.duration, scale))
            .fillMaxHeight()
            .padding(Spacing.xxs.scaled(scale))
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isCurrent) org.njarasoa.fijerena.ui.theme.CinemaAccentDark else org.njarasoa.fijerena.ui.theme.CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = org.njarasoa.fijerena.ui.theme.CinemaAccent.copy(alpha = CinemaAlpha.tint),
            focusedContentColor = CinemaTextPrimary
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
                .padding(Spacing.sm.scaled(scale))
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = TimeFormat.formatTime(program.startTime),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                ),
                color = CinemaTextSecondary,
                maxLines = 1
            )
            Text(
                text = program.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                ),
                color = CinemaTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EpgSearchContent(
    searchQuery: String,
    searchResults: List<EpgViewModel.EpgSearchResult>,
    onSearchQueryChanged: (String) -> Unit,
    onProgramSelected: (EpgProgram, MediaItem) -> Unit
) {
    val scale = LocalUiScale.current

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            label = { Text("Search programs") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.sm.scaled(scale))
        )

        if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No programs found matching \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize.scaled(scale)
                    ),
                    color = CinemaTextSecondary
                )
            }
        } else {
            TvLazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))
            ) {
                items(
                    count = searchResults.size,
                    key = { searchResults[it].program.startTime },
                    contentType = { "epg_search_result" }
                ) { index ->
                    val result = searchResults[index]
                    SearchResultItem(
                        result = result,
                        onClick = { onProgramSelected(result.program, result.channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: EpgViewModel.EpgSearchResult,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledStyles = remember(scale, typography) {
        object {
            val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
            val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            val labelSmall = typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale))
            val labelMedium = typography.labelMedium.copy(fontSize = typography.labelMedium.fontSize.scaled(scale))
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = org.njarasoa.fijerena.ui.theme.CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = org.njarasoa.fijerena.ui.theme.CinemaAccent.copy(alpha = CinemaAlpha.tint),
            focusedContentColor = CinemaTextPrimary
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.program.title,
                    style = scaledStyles.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.channel.name,
                    style = scaledStyles.bodySmall,
                    color = CinemaTextSecondary,
                    maxLines = 1
                )
                Text(
                    text = TimeFormat.formatTimeRange(
                        result.program.startTime,
                        result.program.endTime
                    ),
                    style = scaledStyles.labelSmall,
                    color = CinemaTextSecondary
                )
                result.program.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = scaledStyles.bodySmall,
                            color = CinemaTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (result.isCurrent) {
                Text(
                    text = "NOW",
                    style = scaledStyles.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = org.njarasoa.fijerena.ui.theme.CinemaOrangeLight
                )
            }
        }
    }
}

// Helper functions
private fun calculateProgramWidth(durationSeconds: Long, scale: Float = 1.0f): androidx.compose.ui.unit.Dp {
    // 2dp per minute
    val minutes = durationSeconds / 60
    return ((minutes * 2).coerceAtLeast(120).toInt().dp).scaled(scale)
}
