package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner Radius System — TV overrides (restored rounded edges).
 * Provides consistent corner radii for TV UI elements.
 */
object CornerRadius {
    val small: Dp = 8.dp   // Buttons, small cards, chips
    val medium: Dp = 12.dp // Standard cards, list items
    val large: Dp = 16.dp  // Dialogs, large surfaces, modals
    val xLarge: Dp = 20.dp // Poster cards, glass panels
}
