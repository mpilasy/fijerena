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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.history.SourcedFavoriteCategory
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * The profile's favourite categories on Home, every section's (Live TV, Movies, TV Shows) and every
 * source's: a card per category, its initial over the section's colour, its name and section under
 * it. OK opens the section's list on that category.
 */
@Composable
fun TvFavoriteCategoriesRow(
    title: String,
    categories: List<SourcedFavoriteCategory>,
    onCategorySelected: (SourcedFavoriteCategory) -> Unit,
    firstItemFocus: FocusRequester,
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
        modifier = modifier,
    ) { category, rowModifier ->
        TvFavoriteCategoryCard(
            category = category,
            sourceName = sourceNames[category.providerId],
            onClick = { onCategorySelected(category) },
            modifier = itemModifier(category).then(rowModifier),
        )
    }
}

@Composable
private fun TvFavoriteCategoryCard(
    category: SourcedFavoriteCategory,
    sourceName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentType = category.category.contentType
    // The provider's "EN - " tags off, as in the lists.
    val name = remember(category.category.categoryName) { parseDisplayTitle(category.category.categoryName).title }
    TvCard(
        onClick = onClick,
        modifier = modifier.width(TvDimensions.continueWatchingCardWidth),
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
                CinemaText(
                    text = name.ifBlank { category.category.categoryName },
                    role = TextRole.Heading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    sourceName?.let { SourceBadge(it, providerId = category.providerId) }
                    CinemaText(
                        text =
                            stringResource(
                                when (contentType) {
                                    ContentType.LIVE_TV -> R.string.provider_live_tv_label
                                    ContentType.MOVIES -> R.string.provider_movies_label
                                    else -> R.string.provider_tv_shows_label
                                },
                            ),
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
