package org.njarasoa.fijerena.core.ui.theme

/**
 * Reactive Cinema Color Scheme
 * Bridges the static constants to the current active palette.
 */

// Primary accent
val CinemaAccent get() = CinemaThemeHolder.current.accent
val CinemaAccentDark get() = CinemaThemeHolder.current.accentDark
val CinemaAccentLight get() = CinemaThemeHolder.current.accentLight

// Secondary accent
val CinemaOrange get() = CinemaThemeHolder.current.orange
val CinemaOrangeDark get() = CinemaThemeHolder.current.orangeDark
val CinemaOrangeLight get() = CinemaThemeHolder.current.orangeLight

// Surface colors
val CinemaBackground get() = CinemaThemeHolder.current.background
val CinemaSurface get() = CinemaThemeHolder.current.surface
val CinemaSurfaceVariant get() = CinemaThemeHolder.current.surfaceVariant
val CinemaSurfaceLight get() = CinemaThemeHolder.current.surfaceLight

// Glassmorphism
val CinemaGlassBackground get() = CinemaThemeHolder.current.glassBackground
val CinemaGlassBorder get() = CinemaThemeHolder.current.glassBorder

// Status colors (remain constant across themes in current design)
val CinemaSuccess get() = CinemaThemeHolder.current.success
val CinemaWarning get() = CinemaThemeHolder.current.warning
val CinemaError get() = CinemaThemeHolder.current.error
val CinemaLive get() = CinemaThemeHolder.current.live

// Text colors (remain constant across themes in current design)
val CinemaTextPrimary get() = CinemaThemeHolder.current.textPrimary
val CinemaTextSecondary get() = CinemaThemeHolder.current.textSecondary
val CinemaTextTertiary get() = CinemaThemeHolder.current.textTertiary
val CinemaTextDisabled get() = CinemaThemeHolder.current.textDisabled
