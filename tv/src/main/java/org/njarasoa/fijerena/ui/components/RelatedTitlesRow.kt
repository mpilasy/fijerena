package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
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
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

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

    val cardStyle = relatedTitleCardStyle()

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = CinemaTextPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        LazyRow(
            // Coming back to the row lands on the card the user left, not back at the start.
            modifier = Modifier.focusRestorer(),
            // A focused card grows past its layout bounds, and the row clips its children — without
            // this slack the top and bottom of the focus border and glow are cut off.
            contentPadding = PaddingValues(horizontal = FOCUS_BLEED, vertical = FOCUS_BLEED),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(items, key = { it.id }) { item ->
                RelatedTitleCard(item = item, cardStyle = cardStyle, onClick = { onItemClick(item) })
            }
        }
    }
}

/**
 * Card styling for one row, built once per row composition rather than once per card.
 *
 * `CardDefaults.*` are `@Composable`, so they cannot be wrapped in `remember` — hoisting the calls
 * out of the item body is what stops a `CardColors`/`CardScale`/`Border`/`CardGlow`/`CardShape` set
 * being allocated per card per recomposition. Same pattern, and same reason, as `StreamList`'s
 * `StreamCardStyle`.
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
    val shape = RoundedCornerShape(CornerRadius.medium)
    return RelatedCardStyle(
        // The poster covers the card face, so the container tint alone cannot carry focus here —
        // the border and the lift are what actually read from the sofa.
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                focusedContainerColor = TvFocusTokens.focusedContainer,
            ),
        cardScale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
            ),
        border =
            CardDefaults.border(
                focusedBorder =
                    Border(
                        border = BorderStroke(TvFocusTokens.focusBorderWidth, CinemaAccentLight),
                        shape = shape,
                    ),
            ),
        glow = CardDefaults.glow(focusedGlow = TvFocusTokens.focusedGlow),
        shape = CardDefaults.shape(shape = shape),
    )
}

@Composable
private fun RelatedTitleCard(
    item: MediaItem,
    cardStyle: RelatedCardStyle,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(TvDimensions.posterWidth),
        colors = cardStyle.colors,
        scale = cardStyle.cardScale,
        border = cardStyle.border,
        glow = cardStyle.glow,
        shape = cardStyle.shape,
    ) {
        CinemaThumbnail(
            url = item.thumbnailUrl,
            fallbackLetter = item.name.firstOrNull(),
            contentType =
                if (item.mediaType == MediaType.SERIES) {
                    ThumbnailContentType.TV_SHOW
                } else {
                    ThumbnailContentType.MOVIE
                },
            modifier =
                Modifier.size(
                    width = TvDimensions.posterWidth,
                    height = TvDimensions.posterHeightLarge,
                ),
        )
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = CinemaTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .width(TvDimensions.posterWidth)
                    .height(TITLE_BLOCK_HEIGHT)
                    .padding(Spacing.xs),
        )
    }
}

/** Room for two lines of bodySmall, so a one-line title does not make its card shorter than the rest. */
private val TITLE_BLOCK_HEIGHT = Spacing.xl + Spacing.sm

/** Slack around the row so a focused card's border, scale and glow are not clipped away. */
private val FOCUS_BLEED = Spacing.sm
