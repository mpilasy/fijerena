package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * CompositionLocal for UI scale factor.
 * Note: Scaling is now applied globally via LocalDensity in MainActivity.
 * The .scaled() extension functions are kept for compatibility but now return
 * the original value to avoid double scaling.
 */
val LocalUiScale = compositionLocalOf { 1.0f }

/**
 * Extension function to scale Dp values (now a no-op due to density scaling)
 */
fun Dp.scaled(scale: Float): Dp = this

/**
 * Extension function to scale TextUnit values (now a no-op due to density scaling)
 */
fun TextUnit.scaled(scale: Float): TextUnit = this

/**
 * Extension function to scale Int values (now a no-op due to density scaling)
 */
fun Int.scaled(scale: Float): Int = this

/**
 * Extension function to scale Float values (now a no-op due to density scaling)
 */
fun Float.scaled(scale: Float): Float = this
