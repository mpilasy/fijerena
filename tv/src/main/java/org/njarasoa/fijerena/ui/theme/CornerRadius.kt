package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle

/**
 * Corner Radius System — TV overrides (restored rounded edges).
 * Provides consistent corner radii for TV UI elements, driven by the active look-and-feel style.
 */
object CornerRadius {
    val small: Dp
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current.shapes.chip // Buttons, small cards, chips

    val medium: Dp
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current.shapes.card // Standard cards, list items

    val large: Dp
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current.shapes.dialog // Dialogs, large surfaces, modals

    val xLarge: Dp
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current.shapes.card + 4.dp // Poster cards, glass panels
}
