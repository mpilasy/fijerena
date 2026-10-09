@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.elapsedFraction
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.home.LiveRowEntry
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.feature.category.components.RowActionsHint
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

/** A row of channel cards on Home: Channels (last watched, then Recent) or Favorite channels. */
@Composable
fun TvLiveRow(
    title: String,
    entries: List<LiveRowEntry>,
    nowPlaying: Map<String, EpgProgram>,
    onEntrySelected: (LiveRowEntry) -> Unit,
    /** Long-press OK or the Menu key on a card: its actions menu. */
    onOpenActions: (LiveRowEntry) -> Unit,
    firstItemFocus: FocusRequester,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    itemModifier: (LiveRowEntry) -> Modifier = { Modifier },
) {
    HomeRow(
        title = title,
        items = entries,
        key = { it.item.id },
        listState = listState,
        firstItemFocus = firstItemFocus,
        modifier = modifier,
    ) { entry, rowModifier ->
        TvLiveChannelCard(
            entry = entry,
            now = nowPlaying[entry.item.id],
            onClick = { onEntrySelected(entry) },
            onOpenActions = { onOpenActions(entry) },
            modifier = itemModifier(entry).then(rowModifier),
        )
    }
}

@Composable
private fun TvLiveChannelCard(
    entry: LiveRowEntry,
    now: EpgProgram?,
    onClick: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CinemaCornerRadius.medium)
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        onLongClick = onOpenActions,
        modifier =
            modifier
                .width(TvDimensions.continueWatchingCardWidth)
                .onFocusChanged { isFocused = it.isFocused }
                .openActionsOnMenuKey(onOpenActions),
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaSurface,
                focusedContentColor = CinemaTextPrimary,
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleSubtle,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        shape = CardDefaults.shape(shape = shape),
        border =
            CardDefaults.border(
                border = Border(BorderStroke(TvFocusTokens.borderThin, CinemaGlassBorder)),
                focusedBorder = Border(border = BorderStroke(TvFocusTokens.focusBorderWidth, CinemaTextPrimary), shape = shape),
            ),
        glow = CardDefaults.glow(glow = TvFocusTokens.restingGlow, focusedGlow = TvFocusTokens.focusedGlow),
    ) {
        Column {
            CinemaThumbnail(
                url = entry.item.thumbnailUrl,
                fallbackLetter = entry.item.name.firstOrNull(),
                contentType = ThumbnailContentType.LIVE_TV,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
            )
            if (now == null) {
                // No programme: no bar, but its height, so cards in the row line up.
                Spacer(modifier = Modifier.height(TvDimensions.resumeBarHeight))
            } else {
                LinearProgressIndicator(
                    progress = { now.elapsedFraction() },
                    modifier = Modifier.fillMaxWidth().height(TvDimensions.resumeBarHeight),
                    color = CinemaAccent,
                    trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.focusedTint),
                )
            }
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = CinemaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // The row-actions hint, as on the focused list row.
                    if (isFocused) RowActionsHint()
                }
                val lastWatched = if (entry.lastWatched) stringResource(R.string.home_live_last_watched) else null
                val nowLine = now?.let { stringResource(R.string.epg_now_prefix, it.title) }
                Text(
                    // A line even when empty, so cards in the row line up.
                    text = listOfNotNull(lastWatched, nowLine).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
