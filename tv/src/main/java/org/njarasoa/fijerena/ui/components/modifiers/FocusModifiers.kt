package org.njarasoa.fijerena.ui.components.modifiers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * Standard TV Focus Modifier
 * Applies consistent focus behavior across all focusable elements:
 * - 1.1x scale on focus with 200ms animated transition
 * - 2dp Electric Blue border when focused
 * - Rounded corners matching the design system
 */
fun Modifier.tvFocusable(
    focusScale: Float = TvFocusTokens.focusedScale,
    borderWidth: Dp = TvFocusTokens.focusBorderWidth,
    borderColor: Color = CinemaAccentLight,
    cornerRadius: Dp = CornerRadius.medium
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else TvFocusTokens.defaultScale,
        animationSpec = tween(durationMillis = CinemaAnimation.focusDurationMs),
        label = "focus_scale"
    )

    this
        .focusable()
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
        .scale(animatedScale)
        .border(
            width = if (isFocused) borderWidth else 0.dp,
            color = if (isFocused) borderColor else Color.Transparent,
            shape = RoundedCornerShape(cornerRadius)
        )
}

/**
 * Subtle Focus Modifier
 * For elements that need less dramatic focus feedback:
 * - 1.05x animated scale on focus
 * - 2dp border
 */
fun Modifier.tvFocusableSubtle(
    borderColor: Color = CinemaAccentLight,
    cornerRadius: Dp = CornerRadius.medium
): Modifier = tvFocusable(
    focusScale = TvFocusTokens.focusedScaleSubtle,
    borderWidth = TvFocusTokens.focusBorderWidth,
    borderColor = borderColor,
    cornerRadius = cornerRadius
)

/**
 * No Scale Focus Modifier
 * For elements that should only show border on focus without scaling:
 * - No scale
 * - 2dp animated border
 *
 * Useful for cards in dense grids where scaling would cause overlaps.
 */
fun Modifier.tvFocusableNoScale(
    borderColor: Color = CinemaAccentLight,
    cornerRadius: Dp = CornerRadius.medium
): Modifier = tvFocusable(
    focusScale = TvFocusTokens.defaultScale,
    borderWidth = TvFocusTokens.focusBorderWidth,
    borderColor = borderColor,
    cornerRadius = cornerRadius
)

/**
 * Content-First Focus Modifier (borderless)
 * For image-based cards where the content is the star:
 * - 1.05x scale on focus (gentler than standard)
 * - Shadow elevation glow (12dp accent)
 * - Subtle 1dp border at low opacity only when focused
 * - No bright border, no 1.1x scale
 */
fun Modifier.tvFocusableContent(
    cornerRadius: Dp = CornerRadius.medium
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) TvFocusTokens.focusedScaleContent else TvFocusTokens.defaultScale,
        animationSpec = tween(durationMillis = CinemaAnimation.focusDurationMs),
        label = "content_focus_scale"
    )

    this
        .focusable()
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
        .scale(animatedScale)
        .border(
            width = if (isFocused) TvFocusTokens.focusBorderWidth else 0.dp,
            color = if (isFocused) CinemaAccentLight else Color.Transparent,
            shape = RoundedCornerShape(cornerRadius)
        )
}
