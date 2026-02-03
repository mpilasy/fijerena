package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Deep Night Cinema Color Scheme
 * Google TV Material 3 design language with Electric Blue + Vivid Orange dual accent.
 */

// Primary accent - Electric Blue
val CinemaAccent = Color(0xFF2979FF)        // Electric Blue - focus, primary actions
val CinemaAccentDark = Color(0xFF1565C0)    // Dark blue
val CinemaAccentLight = Color(0xFF82B1FF)   // Light blue - focus borders

// Secondary accent - Vivid Orange
val CinemaOrange = Color(0xFFFF6D00)        // Vivid Orange - LIVE, destructive
val CinemaOrangeDark = Color(0xFFE65100)    // Dark orange
val CinemaOrangeLight = Color(0xFFFFAB40)   // Light orange

// Surface colors - Deep Night
val CinemaBackground = Color(0xFF0F1014)     // Deep Night bg
val CinemaSurface = Color(0xFF161A20)        // Card/surface bg
val CinemaSurfaceVariant = Color(0xFF1E2228) // Elevated surfaces
val CinemaSurfaceLight = Color(0xFF2A3038)   // Borders, dividers

// Glassmorphism
val CinemaGlassBackground = Color(0xBF0F1014)  // #0F1014 @ 75%
val CinemaGlassBorder = Color(0x262979FF)       // #2979FF @ 15%

// Status colors
val CinemaSuccess = Color(0xFF4CAF50)       // Green
val CinemaWarning = Color(0xFFFFC107)       // Yellow
val CinemaError = Color(0xFFF44336)         // Red
val CinemaLive = Color(0xFFFF6D00)          // Vivid Orange for LIVE badge

// Text colors
val CinemaTextPrimary = Color(0xFFFFFFFF)   // Pure white
val CinemaTextSecondary = Color(0xFFB0B0B0) // Light gray
val CinemaTextTertiary = Color(0xFF808080)  // Medium gray
val CinemaTextDisabled = Color(0xFF606060)  // Dark gray

/**
 * Creates a complete Material3 ColorScheme using the Deep Night Cinema color palette.
 */
fun CinemaColorScheme() = androidx.compose.material3.darkColorScheme(
    // Primary colors (main accent - Electric Blue)
    primary = CinemaAccent,
    onPrimary = Color.White,
    primaryContainer = CinemaAccentDark,
    onPrimaryContainer = CinemaAccentLight,

    // Secondary colors (Vivid Orange)
    secondary = CinemaOrange,
    onSecondary = Color.Black,
    secondaryContainer = CinemaOrangeDark,
    onSecondaryContainer = CinemaOrangeLight,

    // Tertiary colors (Light Blue)
    tertiary = CinemaAccentLight,
    onTertiary = Color.Black,
    tertiaryContainer = CinemaSurface,
    onTertiaryContainer = CinemaAccentLight,

    // Error colors
    error = CinemaError,
    onError = Color.White,
    errorContainer = CinemaError.copy(alpha = 0.2f),
    onErrorContainer = CinemaError.copy(alpha = 0.8f),

    // Background colors
    background = CinemaBackground,
    onBackground = CinemaTextPrimary,

    // Surface colors
    surface = CinemaSurface,
    onSurface = CinemaTextPrimary,
    surfaceVariant = CinemaSurfaceVariant,
    onSurfaceVariant = CinemaTextSecondary,

    // Outline colors (borders, dividers)
    outline = CinemaSurfaceLight,
    outlineVariant = CinemaSurfaceVariant,

    // Additional surface tones
    surfaceTint = CinemaAccent,
    inverseSurface = CinemaTextPrimary,
    inverseOnSurface = CinemaBackground,
    inversePrimary = CinemaAccentDark,

    // Scrim (overlays, dialogs)
    scrim = Color.Black.copy(alpha = 0.5f)
)
