package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgChannelRow
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgUtils
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpgTimeline(
    channelRows: List<EpgChannelRow>,
    onProgramSelected: (EpgProgram, MediaItem) -> Unit,
    onChannelSelected: (String, String, String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = CinemaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
        ) {
            items(channelRows, key = { it.channel.id }) { row ->
                ChannelTimelineRow(
                    channelRow = row,
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
    onProgramSelected: (EpgProgram) -> Unit,
    onChannelSelected: () -> Unit
) {
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

        // Horizontal row of program chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
            contentPadding = PaddingValues(horizontal = CinemaSpacing.xs)
        ) {
            items(channelRow.programs, key = { it.id }) { program ->
                ProgramChip(
                    program = program,
                    onClick = { onProgramSelected(program) }
                )
            }
        }
    }
}

@Composable
private fun ProgramChip(
    program: EpgProgram,
    onClick: () -> Unit
) {
    val isCurrent = EpgUtils.isCurrentProgram(program)
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
