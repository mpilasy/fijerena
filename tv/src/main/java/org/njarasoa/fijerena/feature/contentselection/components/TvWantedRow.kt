package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.wanted.WantedCard
import org.njarasoa.fijerena.core.ui.wanted.statusLabel
import org.njarasoa.fijerena.feature.category.components.RowActionsHint
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * The Wanted list on Home (wanted titles plan → Wanted on Home): available titles first, marked
 * Available; the others dimmed and marked Not available. [title] carries the count of new ones.
 * Long-press OK or the Menu key opens a card's menu (Remove from Wanted). Not shown when empty.
 */
@Composable
fun TvWantedRow(
    title: String,
    cards: List<WantedCard>,
    onCardSelected: (WantedCard) -> Unit,
    onOpenActions: (WantedCard) -> Unit,
    firstItemFocus: FocusRequester,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    itemModifier: (WantedCard) -> Modifier = { Modifier },
) {
    HomeRow(
        title = title,
        items = cards,
        key = { it.key },
        listState = listState,
        firstItemFocus = firstItemFocus,
        modifier = modifier,
    ) { card, rowModifier ->
        TvWantedCard(
            card = card,
            onClick = { onCardSelected(card) },
            onOpenActions = { onOpenActions(card) },
            modifier = itemModifier(card).then(rowModifier),
        )
    }
}

@Composable
private fun TvWantedCard(
    card: WantedCard,
    onClick: () -> Unit,
    onOpenActions: () -> Unit,
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
                .openActionsOnMenuKey(onOpenActions),
        look = TvCardLooks.shelf(),
    ) {
        // A title no source has yet is dimmed; focus brings it back to full strength.
        Column(modifier = Modifier.alpha(if (card.isAvailable || isFocused) 1f else CinemaAlpha.textDisabled)) {
            CinemaThumbnail(
                url = card.posterUrl,
                fallbackLetter = card.entity.title.firstOrNull(),
                contentType = ThumbnailContentType.MOVIE,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    CinemaText(
                        text = card.entity.title,
                        role = TextRole.Heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isFocused) RowActionsHint()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (card.isAvailable) CinemaIcons.CheckCircleFilled else CinemaIcons.CloudOff,
                        contentDescription = null,
                        tint = if (card.isAvailable) CinemaTheme.colors.accent else CinemaTheme.colors.textSecondary,
                        modifier = Modifier.size(TvDimensions.iconSmall),
                    )
                    CinemaText(
                        // "Available · panel-a · FR" (wishlist UX plan, Phase 5), then the year.
                        text = listOfNotNull(card.statusLabel(LocalResources.current), card.entity.year?.toString()).joinToString(" • "),
                        role = TextRole.BodySmall,
                        tone = if (card.isAvailable) TextTone.Success else TextTone.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Home's one-time news (wanted titles plan → Telling the user): "2 titles you wanted are now
 * available". One focus stop, focused on entry; OK goes to the Wanted row. Shown on the visit that
 * first finds the titles available, then never again on any device.
 */
@Composable
fun TvWantedBanner(
    text: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(onClick = onClick, modifier = modifier.fillMaxWidth(), look = TvCardLooks.listRow()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CinemaIcons.Bookmark,
                contentDescription = null,
                tint = CinemaTheme.colors.accent,
                modifier = Modifier.size(TvDimensions.iconMedium),
            )
            CinemaText(text = text, role = TextRole.Heading, modifier = Modifier.weight(1f))
            CinemaText(text = action, role = TextRole.Body, tone = TextTone.Primary)
        }
    }
}
