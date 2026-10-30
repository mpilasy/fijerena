package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.rememberChildProfile
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
    // No language tags on a child's cards (UX-D5).
    val showTags = !rememberChildProfile()
    if (items.isEmpty()) return

    Column(modifier = modifier) {
        CinemaText(text = title, role = TextRole.Heading)
        Spacer(modifier = Modifier.height(Spacing.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(items, key = { it.id }) { item ->
                RelatedTitleCard(item = item, showTags = showTags, onClick = { onItemClick(item) })
            }
        }
    }
}

/** A related title as a poster card: its TMDB list title, or its name with tags (none on a child's card, UX-D5). */
@Composable
private fun RelatedTitleCard(
    item: MediaItem,
    showTags: Boolean,
    onClick: () -> Unit,
) {
    val listTitle = item.listTitle
    PosterCard(
        title = listTitle ?: if (showTags) item.name else parseDisplayTitle(item.name).title.ifBlank { item.name },
        subtitle = null,
        thumbnailUrl = item.thumbnailUrl,
        thumbnailType = if (item.mediaType == MediaType.SERIES) ThumbnailContentType.TV_SHOW else ThumbnailContentType.MOVIE,
        sourceName = null,
        providerId = item.providerId,
        onLongClick = null,
        // A TMDB title no source has: opens its TMDB page (wishlist UX plan, Phase 4).
        notAvailable = item.id.startsWith(TmdbOnlyItem.PREFIX),
        // A TMDB list's card: its TMDB title only, without the source's tags (MediaItem.listTitle).
        tags = listTitle == null && showTags,
        onClick = onClick,
    )
}
