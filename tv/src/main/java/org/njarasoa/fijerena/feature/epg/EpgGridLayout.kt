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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgChannelRow
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.TimeSlot
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
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
    onBack: () -> Unit,
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
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical,
                ),
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md.scaled(scale)),
        )

        if (isSearchActive) {
            // Search mode
            EpgSearchContent(
                searchQuery = searchQuery,
                searchResults = searchResults,
                onSearchQueryChanged = onSearchQueryChanged,
                onProgramSelected = onProgramSelected,
            )
        } else if (channelRows.isEmpty()) {
            EmptyEpgMessage()
        } else {
            val horizontalScrollState = rememberLazyListState()
            val verticalScrollState = rememberTvLazyListState()

            // Auto-scroll to current time on load
            LaunchedEffect(currentTimeSlot) {
                if (currentTimeSlot > 0 && currentTimeSlot < timeSlots.size) {
                    horizontalScrollState.animateScrollToItem(
                        currentTimeSlot.coerceIn(0, timeSlots.lastIndex),
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Time header row with left spacer for channel column
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(TvDimensions.epgChannelColumnWidth.scaled(scale) + Spacing.md.scaled(scale)))
                    TimeHeaderRow(
                        timeSlots = timeSlots,
                        scrollState = horizontalScrollState,
                        currentTimeSlot = currentTimeSlot,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(TvDimensions.epgTimeHeaderHeight.scaled(scale)),
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))

                // Single unified vertical list: each item is channel + programs
                TvLazyColumn(
                    state = verticalScrollState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs.scaled(scale)),
                ) {
                    items(
                        count = channelRows.size,
                        key = { channelRows[it].channel.id },
                        contentType = { "channel_row" },
                    ) { index ->
                        val row = channelRows[index]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Channel name on the left
                            ChannelItem(
                                channel = row.channel,
                                onClick = {
                                    onChannelSelected(
                                        row.channel.id,
                                        row.channel.name,
                                        row.channel.categoryId,
                                    )
                                },
                                modifier = Modifier.width(TvDimensions.epgChannelColumnWidth.scaled(scale)),
                            )

                            Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))

                            // Program row on the right
                            ProgramRow(
                                channelRow = row,
                                timeSlots = timeSlots,
                                nowEpochSeconds = nowEpochSeconds,
                                scrollState = horizontalScrollState,
                                onProgramSelected = { program ->
                                    onProgramSelected(program, row.channel)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
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
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Title and date
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TV Guide - $categoryName",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize =
                            MaterialTheme.typography.titleLarge.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextPrimary,
            )
            Text(
                text = selectedDate.format(EPG_DATE_FORMATTER),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize =
                            MaterialTheme.typography.bodyMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary,
            )
        }

        // Date navigation + refresh
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
            Button(onClick = onPreviousDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Previous Day",
                    tint = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
                )
            }
            Button(onClick = onJumpToNow) {
                Text("Now")
            }
            Button(onClick = onNextDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Next Day",
                    tint = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
                )
            }
            Button(
                onClick = onRefresh,
                enabled = !isRefreshing,
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                        strokeWidth = TvDimensions.borderDefault,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
                    )
                }
            }
            Button(onClick = onSearchToggle) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (isSearchActive) "Close Search" else "Search",
                    tint = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
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
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No EPG data available for these channels",
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize =
                        MaterialTheme.typography.bodyLarge.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextSecondary,
        )
    }
}

@Composable
private fun ChannelItem(
    channel: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    // Memoize scaled TextStyle to avoid allocating a new copy per channel per recomposition
    val scaledBodyMedium =
        remember(scale, typography) {
            typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
        }

    Card(
        onClick = onClick,
        modifier =
            modifier
                .height(TvDimensions.epgRowHeight.scaled(scale))
                .onFocusChanged { isFocused = it.isFocused },
        colors =
            CardDefaults.colors(
                containerColor = org.njarasoa.fijerena.ui.theme.CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor =
                    org.njarasoa.fijerena.ui.theme.CinemaAccent
                        .copy(alpha = CinemaAlpha.tint),
                focusedContentColor = CinemaTextPrimary,
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    androidx.tv.material3.Glow(
                        elevationColor =
                            org.njarasoa.fijerena.ui.theme.CinemaAccent
                                .copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.sm.scaled(scale)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = channel.name,
                style = scaledBodyMedium,
                color = CinemaTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TimeHeaderRow(
    timeSlots: List<TimeSlot>,
    scrollState: LazyListState,
    currentTimeSlot: Int,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledLabelMedium =
        remember(scale, typography) {
            typography.labelMedium.copy(fontSize = typography.labelMedium.fontSize.scaled(scale))
        }

    GlassPanel(modifier = modifier) {
        LazyRow(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false, // Synchronized with program rows
        ) {
            items(timeSlots.size, contentType = { "time_slot" }) { index ->
                val slot = timeSlots[index]
                val isCurrent = index == currentTimeSlot

                Box(
                    modifier =
                        Modifier
                            .width(TvDimensions.epgTimeSlotWidth.scaled(scale))
                            .fillMaxHeight()
                            .background(
                                if (isCurrent) {
                                    org.njarasoa.fijerena.ui.theme.CinemaAccentDark
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = TimeFormat.formatTime(slot.startTime),
                        style = scaledLabelMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) CinemaTextPrimary else CinemaTextSecondary,
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
    onProgramSelected: (EpgProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current

    LazyRow(
        state = scrollState,
        modifier =
            modifier
                .height(TvDimensions.epgRowHeight.scaled(scale)),
        userScrollEnabled = false, // Synchronized with header row
    ) {
        items(
            count = channelRow.programs.size,
            key = { channelRow.programs[it].id },
            contentType = { "program" },
        ) { index ->
            val program = channelRow.programs[index]
            ProgramCell(
                program = program,
                nowEpochSeconds = nowEpochSeconds,
                onClick = { onProgramSelected(program) },
            )
        }
    }
}

@Composable
private fun ProgramCell(
    program: EpgProgram,
    nowEpochSeconds: Long,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    // Use shared nowEpochSeconds instead of per-cell System.currentTimeMillis()
    val isCurrent = nowEpochSeconds in program.startTime..program.endTime
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    // Memoize scaled TextStyles to avoid allocating new copies per cell per recomposition (50×N cells)
    val scaledLabelSmall =
        remember(scale, typography) {
            typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale))
        }
    val scaledBodyMedium =
        remember(scale, typography) {
            typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
        }

    Card(
        onClick = onClick,
        modifier =
            Modifier
                .width(calculateProgramWidth(program.duration, scale))
                .fillMaxHeight()
                .padding(Spacing.xxs.scaled(scale))
                .onFocusChanged { isFocused = it.isFocused },
        colors =
            CardDefaults.colors(
                containerColor = if (isCurrent) org.njarasoa.fijerena.ui.theme.CinemaAccentDark else org.njarasoa.fijerena.ui.theme.CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor =
                    org.njarasoa.fijerena.ui.theme.CinemaAccent
                        .copy(alpha = CinemaAlpha.tint),
                focusedContentColor = CinemaTextPrimary,
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    androidx.tv.material3.Glow(
                        elevationColor =
                            org.njarasoa.fijerena.ui.theme.CinemaAccent
                                .copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(Spacing.sm.scaled(scale))
                    .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = TimeFormat.formatTime(program.startTime),
                style = scaledLabelSmall,
                color = CinemaTextSecondary,
                maxLines = 1,
            )
            Text(
                text = program.title,
                style = scaledBodyMedium,
                color = CinemaTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EpgSearchContent(
    searchQuery: String,
    searchResults: List<EpgViewModel.EpgSearchResult>,
    onSearchQueryChanged: (String) -> Unit,
    onProgramSelected: (EpgProgram, MediaItem) -> Unit,
) {
    val scale = LocalUiScale.current

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            label = { Text("Search programs") },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm.scaled(scale)),
        )

        if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No programs found matching \"$searchQuery\"",
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize =
                                MaterialTheme.typography.bodyLarge.fontSize
                                    .scaled(scale),
                        ),
                    color = CinemaTextSecondary,
                )
            }
        } else {
            TvLazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
            ) {
                items(
                    count = searchResults.size,
                    key = {
                        "search_${searchResults[it].channel.id}_${searchResults[it].program.id}_${searchResults[it].program.startTime}"
                    },
                    contentType = { "epg_search_result" },
                ) { index ->
                    val result = searchResults[index]
                    SearchResultItem(
                        result = result,
                        onClick = { onProgramSelected(result.program, result.channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: EpgViewModel.EpgSearchResult,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledStyles =
        remember(scale, typography) {
            object {
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
                val labelSmall = typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale))
                val labelMedium = typography.labelMedium.copy(fontSize = typography.labelMedium.fontSize.scaled(scale))
            }
        }

    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
        colors =
            CardDefaults.colors(
                containerColor = org.njarasoa.fijerena.ui.theme.CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor =
                    org.njarasoa.fijerena.ui.theme.CinemaAccent
                        .copy(alpha = CinemaAlpha.tint),
                focusedContentColor = CinemaTextPrimary,
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    androidx.tv.material3.Glow(
                        elevationColor =
                            org.njarasoa.fijerena.ui.theme.CinemaAccent
                                .copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.program.title,
                    style = scaledStyles.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.channel.name,
                    style = scaledStyles.bodySmall,
                    color = CinemaTextSecondary,
                    maxLines = 1,
                )
                Text(
                    text =
                        TimeFormat.formatTimeRange(
                            result.program.startTime,
                            result.program.endTime,
                        ),
                    style = scaledStyles.labelSmall,
                    color = CinemaTextSecondary,
                )
                result.program.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = scaledStyles.bodySmall,
                            color = CinemaTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (result.isCurrent) {
                Text(
                    text = "NOW",
                    style = scaledStyles.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = org.njarasoa.fijerena.ui.theme.CinemaOrangeLight,
                )
            }
        }
    }
}

// Helper functions
private fun calculateProgramWidth(
    durationSeconds: Long,
    scale: Float = 1.0f,
): androidx.compose.ui.unit.Dp {
    // 2dp per minute
    val minutes = durationSeconds / 60
    return ((minutes * 2).coerceAtLeast(120).toInt().dp).scaled(scale)
}
