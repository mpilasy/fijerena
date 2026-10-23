package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How a PIN prompt looks in both apps (the profile pin plan): the dots for the
 * digits typed and, on TV, the number pad's keys. Colours come from [CinemaTheme]; only the sizes
 * are decided here.
 */
object PinStyle {
    /** A digit's dot, and the space between dots. */
    val dotSize: Dp = 16.dp
    val dotGap: Dp = CinemaSpacing.md
    val dotOutline: Dp = 2.dp
    val dotShape: Shape = CircleShape

    /** A TV number pad key, and the space between keys. */
    val keyWidth: Dp = 96.dp
    val keyGap: Dp = CinemaSpacing.sm

    /** A typed digit's dot, and the outline of one still to type. */
    val filled: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.accent
    val empty: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.textSecondary
}
