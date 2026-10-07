@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

/**
 * A channel on Home's Live row (TV home overhaul plan, Phase 4). [fromFavorites] picks the list it
 * zaps through once opened: Favorites for a favourite, Recent for the last-watched and recent ones.
 */
data class LiveRowEntry(
    val item: MediaItem,
    val fromFavorites: Boolean,
    val lastWatched: Boolean,
)

/**
 * The Live row: the last channel watched first, then favourite channels, then recent ones, each
 * channel once (the first place it appears wins, so a favourite that is also recent is a
 * favourite), at most [max].
 */
fun mergeLiveRow(
    lastItemId: String?,
    recent: List<MediaItem>,
    favorites: List<MediaItem>,
    max: Int = LIVE_ROW_MAX,
): List<LiveRowEntry> {
    val entries = mutableListOf<LiveRowEntry>()
    val seen = HashSet<String>()
    val last = lastItemId?.let { id -> recent.firstOrNull { it.id == id } ?: favorites.firstOrNull { it.id == id } }
    if (last != null) {
        entries += LiveRowEntry(last, fromFavorites = false, lastWatched = true)
        seen += last.id
    }
    for (item in favorites) if (seen.add(item.id)) entries += LiveRowEntry(item, fromFavorites = true, lastWatched = false)
    for (item in recent) if (seen.add(item.id)) entries += LiveRowEntry(item, fromFavorites = false, lastWatched = false)
    return entries.take(max)
}

const val LIVE_ROW_MAX = 20

@Composable
fun TvLiveRow(
    entries: List<LiveRowEntry>,
    nowPlaying: Map<String, EpgProgram>,
    onEntrySelected: (LiveRowEntry) -> Unit,
    firstItemFocus: FocusRequester,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    itemModifier: (LiveRowEntry) -> Modifier = { Modifier },
) {
    HomeRow(
        title = stringResource(R.string.home_live_row_title),
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
            modifier = itemModifier(entry).then(rowModifier),
        )
    }
}

@Composable
private fun TvLiveChannelCard(
    entry: LiveRowEntry,
    now: EpgProgram?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CinemaCornerRadius.medium)
    Card(
        onClick = onClick,
        modifier = modifier.width(TvDimensions.continueWatchingCardWidth),
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
                Text(
                    text = entry.item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
