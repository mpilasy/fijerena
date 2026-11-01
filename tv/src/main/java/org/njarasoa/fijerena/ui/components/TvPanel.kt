@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import org.njarasoa.fijerena.core.ui.components.PanelCorner
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.panelColor
import org.njarasoa.fijerena.core.ui.components.panelShape
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme

/**
 * The TV counterpart of `CinemaPanel`: [fill] behind [content], in [corner], and the theme's text
 * colour as TV Material's content colour for what does not set its own. Use it in place of a raw
 * `Surface`; a focusable row or card is a `TvCard` with a look from `TvCardLooks`.
 */
@Composable
fun TvPanel(
    modifier: Modifier = Modifier,
    fill: PanelFill = PanelFill.Surface,
    corner: PanelCorner = PanelCorner.None,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = panelShape(corner),
        colors =
            SurfaceDefaults.colors(
                containerColor = panelColor(fill),
                contentColor = CinemaTheme.colors.textPrimary,
            ),
        content = content,
    )
}
