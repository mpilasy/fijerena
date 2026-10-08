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
 * "Text & grid size" is applied in one place: `MainActivity` provides this factor and a
 * [androidx.compose.ui.platform.LocalDensity] scaled by it around the whole nav host, so every
 * `dp` and `sp` on every screen (the player and Settings included) follows it, and dialogs and
 * popups restore it in their own window with
 * [org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity]. Screens don't provide it again.
 *
 * The `.scaled()` extensions below return their receiver unchanged: the density already scales
 * the value, so multiplying it again would shrink it twice. They remain only for existing call
 * sites; new code doesn't call them.
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
