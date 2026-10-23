package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the focused title's poster shows behind Home and the lists: the blurred wash
 * (`AmbientBackdrop`) and the title art on TV Home. Off, the theme's background shows alone. The
 * profile's setting (`AppSettings.posterBackdrop`), provided by each app's `MainActivity`.
 */
val LocalPosterBackdrop = staticCompositionLocalOf { true }
