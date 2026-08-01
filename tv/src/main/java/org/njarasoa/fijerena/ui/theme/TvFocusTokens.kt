package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle

/**
 * TV Focus & Interaction Tokens
 * Scale, border, and glow values for D-pad focus states, driven by the active look-and-feel
 * style's [org.njarasoa.fijerena.core.ui.theme.UiGridTokens] where the style expresses an opinion
 * (focus scale, outline weight) — press feedback and elevation stay fixed across styles.
 */
object TvFocusTokens {
    val focusedScale: Float
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current.grid.focusScale

    val focusedScaleSubtle: Float
        @Composable @ReadOnlyComposable get() = 1f + (LocalUiStyle.current.grid.focusScale - 1f) * 0.5f

    val focusedScaleContent: Float
        @Composable @ReadOnlyComposable get() = 1f + (LocalUiStyle.current.grid.focusScale - 1f) * 0.5f

    const val pressedScale = 0.95f
    const val pressedScaleSubtle = 0.98f
    const val defaultScale = 1.0f

    val focusBorderWidth: Dp
        @Composable @ReadOnlyComposable get() = if (LocalUiStyle.current.grid.focusUsesOutline) 3.dp else 1.5.dp

    val borderDefault: Dp = 1.dp
    val borderThin: Dp = 0.5.dp
    val glowElevation: Dp = 8.dp
    val focusShadowElevation: Dp = 16.dp
}
