package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.components.ShimmerPlaceholder
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Home's rows while they load on the first show of Home: shimmer card shapes where the rows come,
 * so the rows do not come in one after the other and push each other down. [rowTop] is the space
 * above each row, as above the real rows. Not focusable.
 */
@Composable
internal fun HomeRowsPlaceholder(
    rowTop: Dp,
    modifier: Modifier = Modifier,
) {
    // Measured with no height limit and cut at the bottom edge, so the last row is cut, not squeezed.
    Column(modifier = modifier.fillMaxWidth().clipToBounds().wrapContentHeight(Alignment.Top, unbounded = true)) {
        repeat(PLACEHOLDER_ROWS) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = rowTop)) {
                ShimmerBar(
                    modifier =
                        Modifier
                            .padding(start = Spacing.xxs, bottom = Spacing.sm)
                            .width(TvDimensions.continueWatchingCardWidth * TITLE_WIDTH_SHARE)
                            .height(Spacing.md),
                )
                // A row wider than the screen: its last card is cut at the edge, as a real row's is.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.padding(horizontal = Spacing.xxs),
                ) {
                    repeat(PLACEHOLDER_CARDS) { PlaceholderCard() }
                }
            }
        }
    }
}

/** A card's shape: its art (16:9) and two text lines, as a Continue Watching card. */
@Composable
private fun PlaceholderCard() {
    Column(
        modifier =
            Modifier
                .width(TvDimensions.continueWatchingCardWidth)
                .clip(CinemaTheme.shapes.card)
                .background(CinemaTheme.colors.surface),
    ) {
        ShimmerPlaceholder(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ShimmerBar(modifier = Modifier.fillMaxWidth(TITLE_WIDTH_SHARE).height(Spacing.md))
            ShimmerBar(modifier = Modifier.fillMaxWidth(LINE_WIDTH_SHARE).height(Spacing.sm))
        }
    }
}

@Composable
private fun ShimmerBar(modifier: Modifier) {
    Box(modifier = modifier.clip(cornerShape(CornerRadius.small))) {
        ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
    }
}

private const val PLACEHOLDER_ROWS = 2
private const val PLACEHOLDER_CARDS = 6
private const val TITLE_WIDTH_SHARE = 0.6f
private const val LINE_WIDTH_SHARE = 0.4f
