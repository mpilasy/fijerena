@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.player.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
fun ChannelListOverlay(
    title: String,
    streams: ImmutableMediaList,
    onSelect: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
    panelAlignment: Alignment = Alignment.CenterStart,
    emptyMessage: String = "No channels"
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(streams) {
        if (streams.isNotEmpty()) firstItemFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.tint))
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        TvGlassPanel(
            modifier = Modifier
                .align(panelAlignment)
                .fillMaxWidth(0.5f)
                .fillMaxHeight()
                .padding(Spacing.xxl),
            backgroundAlpha = 0.5f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(bottom = Spacing.md)
                        .bounceMarquee()
                )
                if (streams.isEmpty()) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
                    )
                } else {
                    TvLazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        itemsIndexed(
                            items = streams,
                            key = { _, stream -> stream.id }
                        ) { index, stream ->
                            Button(
                                onClick = { onSelect(stream) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (index == 0) Modifier.focusRequester(firstItemFocusRequester)
                                        else Modifier
                                    )
                            ) {
                                Text(
                                    text = stream.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bounceMarquee()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
