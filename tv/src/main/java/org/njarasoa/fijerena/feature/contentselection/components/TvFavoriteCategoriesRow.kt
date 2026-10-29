package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
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
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.history.SourcedFavoriteCategory
import org.njarasoa.fijerena.feature.category.components.RowActionsHint
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * One section's favourite categories on Home, of every source: a card per category, its initial over
 * the section's colour, its name, and its source's badge while the row mixes several. OK opens the
 * section's list on that category.
 */
@Composable
fun TvFavoriteCategoriesRow(
    title: String,
    categories: List<SourcedFavoriteCategory>,
    onCategorySelected: (SourcedFavoriteCategory) -> Unit,
    /** Long-press OK or the Menu key on a card: its actions menu. */
    onOpenActions: (SourcedFavoriteCategory) -> Unit,
    firstItemFocus: FocusRequester,
    /** See [HomeRow]: the card Back returns to, while it is pending. */
    restoreFocus: FocusRequester? = null,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    itemModifier: (SourcedFavoriteCategory) -> Modifier = { Modifier },
    /** Each source's name while the row mixes several: a badge on its cards. */
    sourceNames: Map<Long, String> = emptyMap(),
) {
    HomeRow(
        title = title,
        items = categories,
        key = { it.key },
        listState = listState,
        firstItemFocus = firstItemFocus,
        restoreFocus = restoreFocus,
        modifier = modifier,
    ) { category, rowModifier ->
        TvFavoriteCategoryCard(
            category = category,
            sourceName = sourceNames[category.providerId],
            onClick = { onCategorySelected(category) },
            onOpenActions = { onOpenActions(category) },
            modifier = itemModifier(category).then(rowModifier),
        )
    }
}

@Composable
private fun TvFavoriteCategoryCard(
    category: SourcedFavoriteCategory,
    sourceName: String?,
    onClick: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val contentType = category.category.contentType
    // The provider's "EN - " tags off, as in the lists.
    val name = remember(category.category.categoryName) { parseDisplayTitle(category.category.categoryName).title }
    TvCard(
        onClick = onClick,
        onLongClick = onOpenActions,
        modifier =
            modifier
                .width(TvDimensions.continueWatchingCardWidth)
                .onFocusChanged { isFocused = it.isFocused }
                .openActionsOnMenuKey(onOpenActions),
        look = TvCardLooks.shelf(),
    ) {
        Column {
            CinemaThumbnail(
                url = null,
                fallbackLetter = name.firstOrNull(Char::isLetterOrDigit),
                contentType =
                    when (contentType) {
                        ContentType.LIVE_TV -> ThumbnailContentType.LIVE_TV
                        ContentType.MOVIES -> ThumbnailContentType.MOVIE
                        else -> ThumbnailContentType.TV_SHOW
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
            )
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CinemaText(
                        text = name.ifBlank { category.category.categoryName },
                        role = TextRole.Heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // The row-actions hint, as on the other Home cards.
                    if (isFocused) RowActionsHint()
                }
                sourceName?.let { SourceBadge(it, providerId = category.providerId) }
            }
        }
    }
}
