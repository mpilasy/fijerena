package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TV Focus & Interaction Tokens
 * Scale, border, and glow values for D-pad focus states.
 */
object TvFocusTokens {
    const val focusedScale = 1.1f
    const val focusedScaleSubtle = 1.05f
    const val pressedScale = 0.95f
    const val pressedScaleSubtle = 0.98f
    const val defaultScale = 1.0f
    val focusBorderWidth: Dp = 2.dp
    val borderDefault: Dp = 1.dp
    val borderThin: Dp = 0.5.dp
    val glowElevation: Dp = 8.dp
    val focusShadowElevation: Dp = 16.dp
    const val focusedScaleContent = 1.05f
}
