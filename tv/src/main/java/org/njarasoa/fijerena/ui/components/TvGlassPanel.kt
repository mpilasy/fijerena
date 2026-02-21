package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.ui.theme.CornerRadius

/**
 * TV-specific GlassPanel with sharp corners (0.dp radius).
 * Delegates to the shared GlassPanel with TV corner radius override.
 */
@Composable
fun TvGlassPanel(
    modifier: Modifier = Modifier,
    blurRadius: Float = 20f,
    backgroundAlpha: Float = 1f,
    content: @Composable () -> Unit
) {
    GlassPanel(
        modifier = modifier,
        blurRadius = blurRadius,
        backgroundAlpha = backgroundAlpha,
        panelShape = RoundedCornerShape(CornerRadius.large),
        content = content
    )
}
