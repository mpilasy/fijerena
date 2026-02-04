package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Deep Night Cinema Color Scheme - Google TV Material 3 Design Language
 * Professional, video-focused dark theme with Electric Blue and Vivid Orange accents.
 * Consistent across Mobile and TV platforms.
 */

// Primary accent - Electric Blue
val CinemaAccent = Color(0xFF2979FF)
val CinemaAccentDark = Color(0xFF1565C0)
val CinemaAccentLight = Color(0xFF82B1FF)

// Secondary accent - Vivid Orange (for LIVE indicators, destructive actions)
val CinemaOrange = Color(0xFFFF6D00)
val CinemaOrangeDark = Color(0xFFE65100)
val CinemaOrangeLight = Color(0xFFFFAB40)

// Background & Surface colors - Deep Night palette
val CinemaBackground = Color(0xFF0F1014)        // Deep Night background
val CinemaSurface = Color(0xFF161A20)            // Card/surface background
val CinemaSurfaceVariant = Color(0xFF1E2228)     // Elevated surfaces
val CinemaSurfaceLight = Color(0xFF2A3038)       // Borders, dividers

// Glassmorphism support
val CinemaGlassBackground = Color(0xFF0F1014).copy(alpha = 0.75f)  // 75% opacity
val CinemaGlassBorder = Color(0xFF2979FF).copy(alpha = 0.15f)      // 15% opacity

// Status colors
val CinemaSuccess = Color(0xFF4CAF50)
val CinemaWarning = Color(0xFFFFC107)
val CinemaError = Color(0xFFF44336)

// Text colors
val CinemaTextPrimary = Color(0xFFFFFFFF)
val CinemaTextSecondary = Color(0xFFB0B0B0)
val CinemaTextTertiary = Color(0xFF808080)
val CinemaTextDisabled = Color(0xFF606060)
