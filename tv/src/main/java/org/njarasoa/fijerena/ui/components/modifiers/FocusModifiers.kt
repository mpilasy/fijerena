package org.njarasoa.fijerena.ui.components.modifiers

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CornerRadius

/**
 * Standard TV Focus Modifier
 * Applies consistent focus behavior across all focusable elements:
 * - 1.05x scale on focus (TV feedback)
 * - 3dp cyan border when focused
 * - Rounded corners matching the design system
 *
 * @param focusScale Scale factor when focused (default 1.05f)
 * @param borderWidth Border width when focused (default 3.dp)
 * @param borderColor Border color when focused (default CinemaAccentLight)
 * @param cornerRadius Corner radius for border (default CornerRadius.medium)
 */
fun Modifier.tvFocusable(
    focusScale: Float = 1.05f,
    borderWidth: Dp = 3.dp,
    borderColor: Color = CinemaAccentLight,
    cornerRadius: Dp = CornerRadius.medium
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }

    this
        .focusable()
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
        .scale(if (isFocused) focusScale else 1.0f)
        .border(
            width = if (isFocused) borderWidth else 0.dp,
            color = if (isFocused) borderColor else Color.Transparent,
            shape = RoundedCornerShape(cornerRadius)
        )
}

/**
 * Subtle Focus Modifier
 * For elements that need less dramatic focus feedback:
 * - 1.03x scale on focus (subtle)
 * - 2dp border
 *
 * @param borderColor Border color when focused (default CinemaAccentLight)
 * @param cornerRadius Corner radius for border (default CornerRadius.medium)
 */
fun Modifier.tvFocusableSubtle(
    borderColor: Color = CinemaAccentLight,
    cornerRadius: Dp = CornerRadius.medium
): Modifier = tvFocusable(
    focusScale = 1.03f,
    borderWidth = 2.dp,
    borderColor = borderColor,
    cornerRadius = cornerRadius
)

/**
 * No Scale Focus Modifier
 * For elements that should only show border on focus without scaling:
 * - No scale
 * - 3dp border
 *
 * Useful for cards in dense grids where scaling would cause overlaps.
 *
 * @param borderColor Border color when focused (default CinemaAccentLight)
 * @param cornerRadius Corner radius for border (default CornerRadius.medium)
 */
fun Modifier.tvFocusableNoScale(
    borderColor: Color = CinemaAccentLight,
    cornerRadius: Dp = CornerRadius.medium
): Modifier = tvFocusable(
    focusScale = 1.0f,
    borderWidth = 3.dp,
    borderColor = borderColor,
    cornerRadius = cornerRadius
)
