@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.LanguageBadge
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.wanted.NotAvailableMark
import org.njarasoa.fijerena.feature.category.components.RowActionsHint
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLook
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.cards.TvRowTitle
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

/**
 * Favourite movies or favourite shows on Home (TV home overhaul plan, Phase 5), and the chart rows.
 * A TMDB-only card (`TmdbOnlyItem`, a Trending row with its switch on) is marked Not available and
 * has no actions.
 */
@Composable
fun TvFavoritesRow(
    title: String,
    items: List<MediaItem>,
    thumbnailType: ThumbnailContentType,
    onItemSelected: (MediaItem) -> Unit,
    /** Long-press OK or the Menu key on a card: its actions menu. */
    /** Null: the cards have no actions (a child profile's Home). */
    onOpenActions: ((MediaItem) -> Unit)?,
    firstItemFocus: FocusRequester,
    /** See [HomeRow]: the card Back returns to, while it is pending. */
    restoreFocus: FocusRequester? = null,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    itemModifier: (MediaItem) -> Modifier = { Modifier },
    /** Each source's name while the row mixes several: a badge on its cards. */
    sourceNames: Map<Long, String> = emptyMap(),
    /** A card's short state, first on its second line: Gone from source (wanted titles plan). */
    statusOf: (MediaItem) -> String? = { null },
    /** A card's note after its year: the person's role on an actor page ("as Paul"). */
    noteOf: (MediaItem) -> String? = { null },
    /** See [HomeRow]: one more item before the cards, the row's first. */
    leading: (@Composable (modifier: Modifier) -> Unit)? = null,
    /** See [HomeRow]: said beside [leading] while the row has no card. */
    emptyText: String? = null,
) {
    HomeRow(
        title = title,
        items = items,
        key = { it.sourceKey },
        listState = listState,
        firstItemFocus = firstItemFocus,
        restoreFocus = restoreFocus,
        modifier = modifier,
        leading = leading,
        emptyText = emptyText,
    ) { item, rowModifier ->
        val tmdbOnly = item.id.startsWith(TmdbOnlyItem.PREFIX)
        TvFavoriteCard(
            item = item,
            sourceName = sourceNames[item.providerId],
            status = statusOf(item),
            note = noteOf(item),
            thumbnailType = thumbnailType,
            onClick = { onItemSelected(item) },
            onOpenActions = onOpenActions?.takeUnless { tmdbOnly }?.let { open -> { open(item) } },
            notAvailable = tmdbOnly,
            modifier = itemModifier(item).then(rowModifier),
        )
    }
}

@Composable
private fun TvFavoriteCard(
    item: MediaItem,
    sourceName: String?,
    status: String?,
    note: String?,
    thumbnailType: ThumbnailContentType,
    onClick: () -> Unit,
    onOpenActions: (() -> Unit)?,
    notAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    TvCard(
        onClick = onClick,
        onLongClick = onOpenActions,
        modifier =
            modifier
                .width(TvDimensions.continueWatchingCardWidth)
                .onFocusChanged { isFocused = it.isFocused }
                .then(onOpenActions?.let { Modifier.openActionsOnMenuKey(it) } ?: Modifier),
        look = TvCardLooks.shelf(),
    ) {
        Column {
            Box {
                CinemaThumbnail(
                    url = item.thumbnailUrl,
                    fallbackLetter = (item.listTitle ?: item.name).firstOrNull(),
                    contentType = thumbnailType,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                )
                if (notAvailable) {
                    NotAvailableMark(iconSize = TvDimensions.iconSmall, modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xxs))
                }
            }
            Column(modifier = Modifier.padding(Spacing.sm)) {
                // The provider's "EN - " tag as a badge on the second line, as in the lists. A TMDB
                // list's card shows its TMDB title, without the tags (MediaItem.listTitle).
                val parsedTitle = remember(item.name) { parseDisplayTitle(item.name) }
                val listTitle = item.listTitle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CinemaText(
                        text =
                            listTitle ?: parsedTitle.title.ifBlank {
                                item.name
                            },
                        role = TextRole.Heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // The row-actions hint, as on the focused list row.
                    if (isFocused && onOpenActions != null) RowActionsHint()
                }
                // The source opens the second line (Expert mode, SourceBadge), then the title's badges.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    sourceName?.let { SourceBadge(it, providerId = item.providerId) }
                    // A card is narrow: the language only, not the formats.
                    if (listTitle == null) parsedTitle.badge?.let { LanguageBadge(it) }
                    CinemaText(
                        // A line even when empty, so cards in the row line up.
                        text = listOfNotNull(status, item.metadata.releaseDate?.take(4), note, item.metadata.genre).joinToString(" • "),
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
