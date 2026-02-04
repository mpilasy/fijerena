@file:Suppress("unused")

package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.graphics.Color
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder

// Dynamic color re-exports — reads from CinemaThemeHolder.current at access time.
// All references across mobile screen files resolve through these properties.

// Primary accent - Electric Blue (or theme override)
val CinemaAccent: Color get() = CinemaThemeHolder.current.accent
val CinemaAccentDark: Color get() = CinemaThemeHolder.current.accentDark
val CinemaAccentLight: Color get() = CinemaThemeHolder.current.accentLight

// Secondary accent - Vivid Orange
val CinemaOrange: Color get() = CinemaThemeHolder.current.orange
val CinemaOrangeDark: Color get() = CinemaThemeHolder.current.orangeDark
val CinemaOrangeLight: Color get() = CinemaThemeHolder.current.orangeLight

// Background & Surface colors
val CinemaBackground: Color get() = CinemaThemeHolder.current.background
val CinemaSurface: Color get() = CinemaThemeHolder.current.surface
val CinemaSurfaceVariant: Color get() = CinemaThemeHolder.current.surfaceVariant
val CinemaSurfaceLight: Color get() = CinemaThemeHolder.current.surfaceLight

// Glassmorphism support
val CinemaGlassBackground: Color get() = CinemaThemeHolder.current.glassBackground
val CinemaGlassBorder: Color get() = CinemaThemeHolder.current.glassBorder

// Status colors
val CinemaSuccess: Color get() = CinemaThemeHolder.current.success
val CinemaWarning: Color get() = CinemaThemeHolder.current.warning
val CinemaError: Color get() = CinemaThemeHolder.current.error
val CinemaLive: Color get() = CinemaThemeHolder.current.live

// Text colors
val CinemaTextPrimary: Color get() = CinemaThemeHolder.current.textPrimary
val CinemaTextSecondary: Color get() = CinemaThemeHolder.current.textSecondary
val CinemaTextTertiary: Color get() = CinemaThemeHolder.current.textTertiary
val CinemaTextDisabled: Color get() = CinemaThemeHolder.current.textDisabled
