package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * CompositionLocal for UI scale factor.
 * Used to scale category/grid UI elements (fonts, spacing, sizes).
 */
val LocalUiScale = compositionLocalOf { 1.0f }

/**
 * Extension function to scale Dp values
 */
fun Dp.scaled(scale: Float): Dp = this * scale

/**
 * Extension function to scale TextUnit values
 */
fun TextUnit.scaled(scale: Float): TextUnit = this * scale

/**
 * Extension function to scale Int values (for counts, dimensions)
 */
fun Int.scaled(scale: Float): Int = (this * scale).toInt()

/**
 * Extension function to scale Float values
 */
fun Float.scaled(scale: Float): Float = this * scale
