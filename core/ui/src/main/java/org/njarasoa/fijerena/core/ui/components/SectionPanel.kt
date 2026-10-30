package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.ControlStyle

/**
 * One section's rows on Home, grouped (user, 2026-10-29): a faint tint of the section's colour
 * behind them, with the Look and Feel's card corner. It does not clip, so a focused card can grow
 * past its edge.
 */
@Composable
fun SectionPanel(
    section: ThumbnailContentType,
    modifier: Modifier = Modifier,
    /** Inside the tint: by default [ControlStyle.sectionPanelPadding] top and bottom. */
    contentPadding: PaddingValues = PaddingValues(vertical = ControlStyle.sectionPanelPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CinemaTheme.colors
    val tint = (colors.sectionColor(section) ?: colors.surfaceRaised).copy(alpha = ControlStyle.SECTION_TINT_ALPHA)
    Column(
        modifier =
            modifier
                .background(tint, CinemaTheme.shapes.card)
                .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
