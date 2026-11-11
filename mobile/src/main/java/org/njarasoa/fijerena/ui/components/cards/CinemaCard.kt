package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * Default hairline border and elevation, so a bare [CinemaCard] reads as a raised card, not a flat
 * block. Every list row (stream, category, episode, search result) uses this component.
 */
@Composable
private fun defaultCardElevation(): CardElevation = CardDefaults.cardElevation(defaultElevation = MobileDimensions.cardRowElevation)

/**
 * [CinemaCard]'s default hairline, for callers that swap in another border conditionally (for
 * example an accent border while "now playing").
 */
@Composable
fun cinemaCardHairlineBorder(): BorderStroke =
    BorderStroke(MobileDimensions.dividerThin, CinemaTheme.colors.surfaceHigh.copy(alpha = CinemaAlpha.cardHairline))

/**
 * Themed replacement for [Card]: M3's default card shape ignores the
 * [org.njarasoa.fijerena.core.ui.theme.UiStyle] shape tokens. A thin passthrough otherwise.
 */
@Composable
fun CinemaCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = defaultCardElevation(),
    border: BorderStroke? = cinemaCardHairlineBorder(),
    content: @Composable ColumnScope.() -> Unit,
) {
    // look-ok: the component that wraps Material's Card
    Card(
        modifier = modifier,
        shape = cornerShape(CinemaCornerRadius.medium),
        colors = colors,
        elevation = elevation,
        border = border,
        content = content,
    )
}

/** Clickable variant, see [CinemaCard]. */
@Composable
fun CinemaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = defaultCardElevation(),
    border: BorderStroke? = cinemaCardHairlineBorder(),
    content: @Composable ColumnScope.() -> Unit,
) {
    // look-ok: the component that wraps Material's Card
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = cornerShape(CinemaCornerRadius.medium),
        colors = colors,
        elevation = elevation,
        border = border,
        content = content,
    )
}

/**
 * A card with a tap and a long press (a list row whose long press opens its actions), see
 * [CinemaCard]. [onLongClickLabel] names the long press for TalkBack.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CinemaCard(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClickLabel: String? = null,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = defaultCardElevation(),
    border: BorderStroke? = cinemaCardHairlineBorder(),
    content: @Composable ColumnScope.() -> Unit,
) {
    CinemaCard(
        modifier =
            modifier
                .clip(cornerShape(CinemaCornerRadius.medium))
                .combinedClickable(onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick),
        colors = colors,
        elevation = elevation,
        border = border,
        content = content,
    )
}
