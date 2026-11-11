package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import org.njarasoa.fijerena.core.ui.theme.LocalUiScale as CoreLocalUiScale

/**
 * Re-export of [org.njarasoa.fijerena.core.ui.theme.LocalUiScale]. The one instance is in `core:ui`,
 * so shared components there (dialogs above all, which lose the scaled density in their own window)
 * can read the factor.
 *
 * "Text & grid size" is applied in one place: `MainActivity` provides this factor and a
 * [androidx.compose.ui.platform.LocalDensity] scaled by it around the whole nav host, so every `dp`
 * and `sp` on every screen follows it, and dialogs and popups restore it in their own window with
 * [org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity]. Screens do not provide it again.
 *
 * The `.scaled()` extensions below return their receiver unchanged: the density already scales the
 * value. They remain only for existing call sites; new code does not call them.
 */
val LocalUiScale: ProvidableCompositionLocal<Float>
    get() = CoreLocalUiScale

/** No-op: the density already scales. */
fun Dp.scaled(scale: Float): Dp = this

/** No-op: the density already scales. */
fun TextUnit.scaled(scale: Float): TextUnit = this

/** No-op: the density already scales. */
fun Int.scaled(scale: Float): Int = this

/** No-op: the density already scales. */
fun Float.scaled(scale: Float): Float = this
