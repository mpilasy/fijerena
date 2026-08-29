package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.LanguageBadge
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * One row of related titles on a detail screen — those the provider actually carries. Detail
 * screens show two, [title] telling them apart. Draws nothing at all when [items] is empty — no
 * header, no empty state — since a row is a bonus and an empty one only takes up space.
 */
@Composable
fun RelatedTitlesRow(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(items, key = { it.id }) { item ->
                RelatedTitleCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun RelatedTitleCard(
    item: MediaItem,
    onClick: () -> Unit,
) {
    val parsedTitle = remember(item.name) { parseDisplayTitle(item.name) }
    CinemaCard(
        onClick = onClick,
        modifier = Modifier.width(CARD_WIDTH),
    ) {
        Box(modifier = Modifier.size(width = CARD_WIDTH, height = POSTER_HEIGHT)) {
            CinemaThumbnail(
                url = item.thumbnailUrl,
                fallbackLetter = item.name.firstOrNull(),
                contentType =
                    if (item.mediaType == MediaType.SERIES) {
                        ThumbnailContentType.TV_SHOW
                    } else {
                        ThumbnailContentType.MOVIE
                    },
                overlayGradient = true,
                modifier = Modifier.size(width = CARD_WIDTH, height = POSTER_HEIGHT),
            )
            parsedTitle.badge?.let {
                LanguageBadge(
                    code = it,
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xxs),
                )
            }
        }
        Text(
            text = parsedTitle.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(CARD_WIDTH).padding(Spacing.xs),
        )
    }
}

// MobileDimensions.poster* sizes the thumbnail in a list row, which is far too small and the wrong
// shape for a card meant to be looked at — these are a 2:3 poster.
private val CARD_WIDTH = MobileDimensions.posterHeightLarge * 0.6f
private val POSTER_HEIGHT = MobileDimensions.posterHeightLarge * 0.9f
