package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import org.njarasoa.fijerena.core.ui.theme.LocalUiScale as CoreLocalUiScale

/**
 * Re-export of [org.njarasoa.fijerena.core.ui.theme.LocalUiScale].
 *
 * The single instance lives in `core:ui` so shared components there — dialogs above all, which
 * lose the scaled density when they open their own window — can read the factor back.
 *
 * Note: Scaling is applied globally via LocalDensity in MainActivity.
 * The .scaled() extension functions are kept for compatibility but now return
 * the original value to avoid double scaling.
 */
val LocalUiScale: ProvidableCompositionLocal<Float>
    get() = CoreLocalUiScale

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
