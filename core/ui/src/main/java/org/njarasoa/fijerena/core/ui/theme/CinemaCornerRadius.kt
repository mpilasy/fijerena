package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner Radius System
 * Provides consistent corner radii for UI elements throughout the app, driven by the active
 * [UiStyle] ([LocalUiStyle]) so a look-and-feel switch retheme every call site for free.
 */
object CinemaCornerRadius {
    val none: Dp = 0.dp // Sharp edges — not style-driven, "sharp" is always sharp

    val small: Dp
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current.shapes.chip // Buttons, small cards, chips

    val medium: Dp
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current.shapes.card // Standard cards, list items

    val large: Dp
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current.shapes.dialog // Dialogs, large surfaces, modals

    val xLarge: Dp
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current.shapes.card + 4.dp // Poster cards, glass panels
}
