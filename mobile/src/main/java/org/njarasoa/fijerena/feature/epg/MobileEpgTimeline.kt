package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgChannelRow
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import java.time.LocalDate
import java.time.ZoneId

private const val NO_PROGRAM_ID_PREFIX = "no_prog_"

private fun isGapEntry(program: EpgProgram): Boolean =
    program.id.startsWith(NO_PROGRAM_ID_PREFIX)

/**
 * Fills gaps between programs with "No program found" placeholder entries.
 * Always covers the full day from dayStart to dayEnd.
 */
private fun fillGapsWithPlaceholders(
    programs: List<EpgProgram>,
    channelId: String,
    dayStart: Long,
    dayEnd: Long
): List<EpgProgram> {
    val result = mutableListOf<EpgProgram>()
    var cursor = dayStart

    for (program in programs) {
        val progStart = program.startTime.coerceAtLeast(dayStart)
        if (progStart > cursor) {
            result.add(
                EpgProgram(
                    id = "${NO_PROGRAM_ID_PREFIX}${channelId}_$cursor",
                    title = "No program found",
                    start = cursor.toString(),
                    end = progStart.toString()
                )
            )
        }
        result.add(program)
        cursor = maxOf(cursor, program.endTime)
    }

    if (cursor < dayEnd) {
        result.add(
            EpgProgram(
                id = "${NO_PROGRAM_ID_PREFIX}${channelId}_$cursor",
                title = "No program found",
                start = cursor.toString(),
                end = dayEnd.toString()
            )
        )
    }

    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpgTimeline(
    channelRows: List<EpgChannelRow>,
    selectedDate: LocalDate,
    onProgramSelected: (EpgProgram, MediaItem) -> Unit,
    onChannelSelected: (String, String, String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    val dayStart = remember(selectedDate) {
        selectedDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
    }
    val dayEnd = remember(selectedDate) {
        selectedDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
    }

    // Shared "now" timestamp, refreshed every 60s to avoid per-chip System.currentTimeMillis() calls
    var nowEpochSeconds by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowEpochSeconds = System.currentTimeMillis() / 1000
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = CinemaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
        ) {
            items(channelRows, key = { it.channel.id }, contentType = { "channel_row" }) { row ->
                ChannelTimelineRow(
                    channelRow = row,
                    dayStart = dayStart,
                    dayEnd = dayEnd,
                    nowEpochSeconds = nowEpochSeconds,
                    onProgramSelected = { program ->
                        onProgramSelected(program, row.channel)
                    },
                    onChannelSelected = {
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
private fun ChannelTimelineRow(
    channelRow: EpgChannelRow,
    dayStart: Long,
    dayEnd: Long,
    nowEpochSeconds: Long,
    onProgramSelected: (EpgProgram) -> Unit,
    onChannelSelected: () -> Unit
) {
    val filledPrograms = remember(channelRow.programs, dayStart, dayEnd) {
        fillGapsWithPlaceholders(channelRow.programs, channelRow.channel.id, dayStart, dayEnd)
    }

    // Find index of program overlapping "now" for auto-scroll
    val nowIndex = remember(filledPrograms, nowEpochSeconds) {
        val idx = filledPrograms.indexOfFirst {
            nowEpochSeconds in it.startTime..it.endTime
        }
        if (idx >= 0) idx else 0
    }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(filledPrograms) {
        if (nowIndex > 0) {
            lazyListState.scrollToItem(nowIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CinemaSpacing.sm)
    ) {
        // Channel name header
        Text(
            text = channelRow.channel.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(MobileDimensions.epgChannelHeaderHeight)
                .clickable { onChannelSelected() }
                .padding(
                    horizontal = CinemaSpacing.xs,
                    vertical = CinemaSpacing.xs
                )
        )

        // Horizontal row of program chips (with gap placeholders)
        LazyRow(
            state = lazyListState,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
            contentPadding = PaddingValues(horizontal = CinemaSpacing.xs)
        ) {
            items(filledPrograms, key = { it.id }, contentType = { "program" }) { program ->
                val isCurrent = nowEpochSeconds in program.startTime..program.endTime
                if (isGapEntry(program)) {
                    GapChip(program = program, isCurrent = isCurrent)
                } else {
                    ProgramChip(
                        program = program,
                        isCurrent = isCurrent,
                        onClick = { onProgramSelected(program) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramChip(
    program: EpgProgram,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .width(MobileDimensions.epgProgramMinWidth)
            .height(MobileDimensions.epgProgramHeight)
            .clip(RoundedCornerShape(CinemaCornerRadius.small))
            .background(bgColor)
            .clickable { onClick() }
            .padding(CinemaSpacing.xs),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = TimeFormat.formatTimeRange(program.startTime, program.endTime),
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = CinemaAlpha.textMedium),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
        Text(
            text = program.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GapChip(program: EpgProgram, isCurrent: Boolean = false) {
    val bgColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .width(MobileDimensions.epgProgramMinWidth)
            .height(MobileDimensions.epgProgramHeight)
            .clip(RoundedCornerShape(CinemaCornerRadius.small))
            .background(bgColor)
            .padding(CinemaSpacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = TimeFormat.formatTimeRange(program.startTime, program.endTime),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = CinemaAlpha.textMedium),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
            Text(
                text = "No program found",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = textColor.copy(alpha = CinemaAlpha.textMedium),
                maxLines = 1
            )
        }
    }
}
