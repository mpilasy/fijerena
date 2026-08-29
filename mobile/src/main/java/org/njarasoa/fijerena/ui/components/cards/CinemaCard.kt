package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * Default hairline border and elevation so a bare [CinemaCard] reads as a raised card against the
 * screen behind it instead of a flat single-tone block — every list row (stream, category,
 * episode, search result) goes through this component, so the fix lands once here rather than
 * per screen.
 */
@Composable
private fun defaultCardElevation(): CardElevation =
    CardDefaults.cardElevation(defaultElevation = MobileDimensions.cardRowElevation)

/**
 * The same hairline used as [CinemaCard]'s own default border — exposed for the handful of
 * callers that need to swap in a different border conditionally (e.g. an accent border while
 * "now playing") and fall back to the hairline the rest of the time.
 */
@Composable
fun cinemaCardHairlineBorder(): BorderStroke =
    BorderStroke(MobileDimensions.dividerThin, CinemaSurfaceLight.copy(alpha = CinemaAlpha.cardHairline))

/**
 * Themed replacement for [Card] — M3's default card shape ignores the app's
 * [org.njarasoa.fijerena.core.ui.theme.UiStyle] shape tokens. Thin passthrough otherwise.
 */
@Composable
fun CinemaCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = defaultCardElevation(),
    border: BorderStroke? = cinemaCardHairlineBorder(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(CinemaCornerRadius.medium),
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
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(CinemaCornerRadius.medium),
        colors = colors,
        elevation = elevation,
        border = border,
        content = content,
    )
}
