package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.TvDimensions

/** The shared GlassPanel with the TV's large corner radius. */
@Composable
fun TvGlassPanel(
    modifier: Modifier = Modifier,
    blurRadius: Float = 20f,
    backgroundAlpha: Float = 1f,
    content: @Composable () -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        blurRadius = blurRadius,
        backgroundAlpha = backgroundAlpha,
        panelShape = cornerShape(CornerRadius.large),
        content = content,
    )
}

/**
 * A list pane on glass (the category list): the glass colour in the chip corner, and an edge that
 * catches the light in its middle.
 */
@Composable
fun Modifier.glassPane(): Modifier {
    val shape = CinemaTheme.shapes.chip
    val edge = CinemaTheme.colors.glassBorder
    val shine = CinemaTheme.colors.textPrimary.copy(alpha = CinemaAlpha.ghost)
    val brush = remember(edge, shine) { Brush.verticalGradient(listOf(edge, shine, edge)) }
    return background(CinemaTheme.colors.glass, shape).border(TvDimensions.borderDefault, brush, shape)
}

/** A card on glass over video (the player's stats): the glass colour, a faint accent edge, the card corner. */
@Composable
fun Modifier.glassCard(): Modifier {
    val shape = CinemaTheme.shapes.card
    return background(CinemaTheme.colors.glass, shape)
        .border(TvDimensions.borderDefault, CinemaTheme.colors.accent.copy(alpha = CinemaAlpha.scrim), shape)
}
