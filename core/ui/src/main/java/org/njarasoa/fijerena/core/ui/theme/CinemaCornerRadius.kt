package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radii for UI elements, driven by the active [UiStyle] ([LocalUiStyle]), so a Look and Feel
 * change reaches every call site.
 */
object CinemaCornerRadius {
    val none: Dp = 0.dp // Sharp edges — not style-driven, "sharp" is always sharp

    val small: Dp
        @Composable @ReadOnlyComposable
        get() = LocalUiStyle.current.shapes.chip // Buttons, small cards, chips

    val medium: Dp
        @Composable @ReadOnlyComposable
        get() = LocalUiStyle.current.shapes.card // Standard cards, list items

    val large: Dp
        @Composable @ReadOnlyComposable
        get() = LocalUiStyle.current.shapes.dialog // Dialogs, large surfaces, modals

    val xLarge: Dp
        @Composable @ReadOnlyComposable
        get() = LocalUiStyle.current.shapes.card + 4.dp // Poster cards, glass panels
}
