package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.domain.rowDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleBadges
import org.njarasoa.fijerena.core.ui.components.rememberChildProfile
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.core.ui.wanted.NotAvailableMark
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * One row of related titles on a detail screen that the provider carries. [title] is the heading
 * above the row, or null when its tab already names it ("Similar"). Draws nothing when [items] is
 * empty: a row is a bonus, and an empty one only takes space.
 */
@Composable
fun RelatedTitlesRow(
    title: String?,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    // The card Back should land on (the one that opened another details screen): scrolled into
    // view here, and given [focusTargetModifier] so the caller can focus it.
    focusTargetId: String? = null,
    focusTargetModifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val rowState = rememberLazyListState()
    LaunchedEffect(focusTargetId) {
        val index = items.indexOfFirst { it.id == focusTargetId }
        if (index >= 0) rowState.scrollToItem(index)
    }

    val cardStyle = relatedTitleCardStyle()
    // No language tags on a child's cards (UX-D5).
    val showTags = !rememberChildProfile()

    Column(modifier = modifier) {
        if (title != null) {
            CinemaText(text = title, role = TextRole.Heading)
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        LazyRow(
            state = rowState,
            // Coming back to the row lands on the card the user left, not back at the start.
            modifier = Modifier.focusRestorer(),
            // A focused card grows past its bounds and the row clips its children: without this slack the
            // focus border and glow are cut off at the top and bottom.
            contentPadding = PaddingValues(horizontal = FOCUS_BLEED, vertical = FOCUS_BLEED),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                RelatedTitleCard(
                    item = item,
                    cardStyle = cardStyle,
                    showTags = showTags,
                    onClick = { onItemClick(item) },
                    // The first card is at the screen's left edge: Left from it opens the TV
                    // navigation rail.
                    modifier =
                        (if (item.id == focusTargetId) focusTargetModifier else Modifier)
                            .then(if (index == 0) Modifier.leftToRail() else Modifier),
                )
            }
        }
    }
}

/**
 * Card styling for one row, built once per row, not once per card: `CardDefaults.*` are
 * `@Composable` and cannot go in `remember`, so hoisting them out of the item body stops a set of
 * card values being allocated per card per recomposition (as `TvCardLooks` in cards/TvCard.kt).
 */
@Immutable
private data class RelatedCardStyle(
    val colors: CardColors,
    val cardScale: CardScale,
    val border: CardBorder,
    val glow: CardGlow,
    val shape: CardShape,
)

@Composable
private fun relatedTitleCardStyle(): RelatedCardStyle {
    val shape = cornerShape(CornerRadius.medium)
    return RelatedCardStyle(
        // The poster covers the card face, so the container tint cannot carry focus: the lift and the glow
        // do (a ring only in a Look that does not scale).
        colors =
            CardDefaults.colors(
                containerColor = CinemaTheme.colors.surface,
                focusedContainerColor = TvFocusTokens.focusedContainer,
            ),
        cardScale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
            ),
        border =
            CardDefaults.border(focusedBorder = TvFocusTokens.cardFocusBorder(shape)),
        glow = CardDefaults.glow(focusedGlow = TvFocusTokens.cardFocusGlow),
        shape = CardDefaults.shape(shape = shape),
    )
}

@Composable
private fun RelatedTitleCard(
    item: MediaItem,
    cardStyle: RelatedCardStyle,
    showTags: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsedTitle = remember(item.name) { parseDisplayTitle(item.name) }
    // look-ok: TV Material's focusable Card, every look from cardStyle (TvFocusTokens)
    Card(
        onClick = onClick,
        modifier = modifier.width(TvDimensions.posterWidth),
        colors = cardStyle.colors,
        scale = cardStyle.cardScale,
        border = cardStyle.border,
        glow = cardStyle.glow,
        shape = cardStyle.shape,
    ) {
        Box(
            modifier =
                Modifier.size(
                    width = TvDimensions.posterWidth,
                    height = TvDimensions.posterHeightLarge,
                ),
        ) {
            CinemaThumbnail(
                url = item.thumbnailUrl,
                fallbackLetter = (item.listTitle ?: item.name).firstOrNull(),
                contentType =
                    if (item.mediaType == MediaType.SERIES) {
                        ThumbnailContentType.TV_SHOW
                    } else {
                        ThumbnailContentType.MOVIE
                    },
                overlayGradient = true,
                modifier =
                    Modifier.size(
                        width = TvDimensions.posterWidth,
                        height = TvDimensions.posterHeightLarge,
                    ),
            )
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                // A TMDB list's card: its TMDB title only, without the source's tags (MediaItem.listTitle).
                if (item.listTitle == null && showTags) TitleBadges(parsedTitle)
            }
            // A TMDB title no source has: opens its TMDB page (wishlist UX plan, Phase 4).
            if (item.id.startsWith(TmdbOnlyItem.PREFIX)) {
                NotAvailableMark(iconSize = TvDimensions.iconSmall, modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xxs))
            }
        }
        // Room for two lines on every card (minLines), so a one-line title does not make its card shorter
        // and a long one gets a second line before the ellipsis (TV UI audit X4). A fixed height of two line
        // heights cut a Look's taller font to one line.
        CinemaText(
            // Provider data sometimes sends a blank name; see mobile's StreamCard.
            text = item.listTitle ?: rowDisplayTitle(parsedTitle, item.name, null) ?: stringResource(R.string.content_untitled),
            role = TextRole.Heading,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .width(TvDimensions.posterWidth)
                    .padding(Spacing.xs),
        )
    }
}

/** Slack around the row so a focused card's border, scale and glow are not clipped away. */
private val FOCUS_BLEED = Spacing.sm
