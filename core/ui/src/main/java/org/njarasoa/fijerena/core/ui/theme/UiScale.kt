package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * The user's UI scale preference (0.4f - 1.0f), provided once by the TV `MainActivity`.
 *
 * Scaling itself is done by overriding [LocalDensity], so every `dp`/`sp` in the tree follows
 * automatically. The raw factor is kept here for the few places that need the number, and for
 * [ProvideUiScaledDensity].
 */
val LocalUiScale = compositionLocalOf { 1.0f }

/**
 * Re-applies [LocalUiScale] to [LocalDensity].
 *
 * Every `Dialog`/`Popup`/`ModalBottomSheet` opens its own window, and each window owns an
 * `AndroidComposeView` that re-provides `LocalDensity` from the *system* density — so the scaled
 * density installed by `MainActivity` stops at the window boundary and dialog content renders at
 * 100% no matter what the user picked. Custom composition locals are not re-provided that way,
 * which is why [LocalUiScale] still reaches in and can be used to restore the density.
 *
 * Call this as the outermost thing inside any dialog/popup content.
 */
@Composable
fun ProvideUiScaledDensity(content: @Composable () -> Unit) {
    val scale = LocalUiScale.current
    val density = LocalDensity.current
    CompositionLocalProvider(
        // fontScale is left alone: the density factor already scales sp.
        LocalDensity provides Density(density.density * scale, density.fontScale),
        content = content,
    )
}
