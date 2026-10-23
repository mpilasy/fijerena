package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder

/**
 * Reusable vertical gradient overlay (transparent at top → dark at bottom).
 * Used over images to ensure text legibility.
 */
@Composable
fun GradientOverlay(
    modifier: Modifier = Modifier,
    alpha: Float = CinemaAlpha.imageOverlay,
) {
    val c = CinemaTheme.colors
    // Memoize brush to avoid allocating new Brush + listOf on every recomposition
    val brush =
        remember(c, alpha) {
            Brush.verticalGradient(colors = listOf(c.clear, c.background.copy(alpha = alpha)))
        }
    Box(modifier = modifier.background(brush))
}
