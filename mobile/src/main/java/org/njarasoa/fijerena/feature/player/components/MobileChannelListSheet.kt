package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CatchupBadge
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ImmutableCatchupDays
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.CinemaChoiceRow
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@Composable
fun MobileChannelListSheet(
    title: String,
    streams: ImmutableMediaList,
    onSelect: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
    panelAlignment: Alignment = Alignment.CenterStart,
    currentStreamId: String? = null,
    /** Archive days per channel id: channels that keep one get a catch-up badge. */
    catchupDays: ImmutableCatchupDays = ImmutableCatchupDays(),
) {
    val listState = rememberLazyListState()

    LaunchedEffect(streams, currentStreamId) {
        if (streams.isNotEmpty() && currentStreamId != null) {
            val targetIndex = streams.indexOfFirst { it.id == currentStreamId }
            if (targetIndex > 0) listState.scrollToItem(targetIndex)
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .panel(PanelFill.Veil)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
    ) {
        GlassPanel(
            modifier =
                Modifier
                    .align(panelAlignment)
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* consume clicks */ },
            backgroundAlpha = 0.5f,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(CinemaSpacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CinemaText(
                        text = title,
                        role = TextRole.Heading,
                        tone = TextTone.Primary,
                    )
                    IconAction(
                        onClick = onDismiss,
                        icon = CinemaIcons.Close,
                        label = stringResource(R.string.common_close),
                        tone = TextTone.Primary,
                    )
                }
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                if (streams.isEmpty()) {
                    CinemaText(
                        text = stringResource(R.string.player_no_channels),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                    ) {
                        items(streams, key = { it.id }, contentType = { "channel" }) { stream ->
                            val isCurrentStream = stream.id == currentStreamId
                            CinemaChoiceRow(
                                onClick = { onSelect(stream) },
                                modifier = Modifier.fillMaxWidth(),
                                selected = isCurrentStream,
                                overMedia = true,
                            ) {
                                Row(
                                    modifier =
                                        Modifier.padding(
                                            horizontal = CinemaSpacing.md,
                                            vertical = CinemaSpacing.sm,
                                        ),
                                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    catchupDays[stream.id]?.let { CatchupBadge(it, iconSize = MobileDimensions.iconSmall) }
                                    CinemaText(
                                        text = stream.name,
                                        maxLines = 1,
                                        role = TextRole.Heading,
                                        tone = TextTone.Primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
