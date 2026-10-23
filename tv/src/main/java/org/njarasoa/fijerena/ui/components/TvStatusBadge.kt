package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.Spacing

/** What a [TvStatusBadge] says: its fill comes with it. */
enum class TvStatusBadgeKind {
    /** On air now. */
    Success,

    /** Starting soon. */
    Warning,
}

/**
 * A small filled status badge ("ON AIR", "SOON"): the status colour as its fill, the theme's
 * background colour for its text, the Look and Feel's chip corner.
 */
@Composable
fun TvStatusBadge(
    text: String,
    kind: TvStatusBadgeKind,
    modifier: Modifier = Modifier,
) {
    val colors = CinemaTheme.colors
    val fill: Color =
        when (kind) {
            TvStatusBadgeKind.Success -> colors.success
            TvStatusBadgeKind.Warning -> colors.warning
        }
    // look-ok: the component that wraps Material's Text
    Text(
        text = text,
        style = textStyleOf(TextRole.Caption),
        color = colors.background,
        modifier =
            modifier
                .background(fill, CinemaTheme.shapes.chip)
                .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
    )
}
