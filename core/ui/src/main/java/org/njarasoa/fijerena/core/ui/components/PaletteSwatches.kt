package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.CinemaThemePalette

/** Four dots of the palette's colours — a static sample next to a colour theme's name. */
@Composable
fun PaletteSwatches(
    palette: CinemaThemePalette,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs)) {
        listOf(palette.accent, palette.accentLight, palette.orange, palette.surfaceLight).forEach { color ->
            Box(modifier = Modifier.size(CinemaSpacing.md).background(color, CinemaTheme.shapes.identity))
        }
    }
}
