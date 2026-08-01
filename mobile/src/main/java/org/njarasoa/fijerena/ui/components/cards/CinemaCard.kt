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
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius

/**
 * Themed replacement for [Card] — M3's default card shape ignores the app's
 * [org.njarasoa.fijerena.core.ui.theme.UiStyle] shape tokens. Thin passthrough otherwise.
 */
@Composable
fun CinemaCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
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
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
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
