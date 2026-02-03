package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Dark Cinema Color Scheme
 * A professional, video-focused dark theme inspired by modern streaming apps.
 * Completely eliminates purple in favor of cyan/teal accents.
 */

// Primary accent - Teal/Cyan (modern, tech-forward)
val CinemaAccent = Color(0xFF00BCD4)        // Bright cyan
val CinemaAccentDark = Color(0xFF00838F)    // Darker cyan
val CinemaAccentLight = Color(0xFF6EEFFF)   // Light cyan

// Surface colors - Dark grays (video-friendly)
val CinemaBackground = Color(0xFF121212)     // Almost black
val CinemaSurface = Color(0xFF1E1E1E)        // Dark gray
val CinemaSurfaceVariant = Color(0xFF2A2A2A) // Medium gray
val CinemaSurfaceLight = Color(0xFF383838)   // Light gray

// Status colors
val CinemaSuccess = Color(0xFF4CAF50)       // Green
val CinemaWarning = Color(0xFFFFC107)       // Yellow
val CinemaError = Color(0xFFF44336)         // Red
val CinemaLive = Color(0xFFFF5252)          // Bright red for LIVE badge

// Text colors
val CinemaTextPrimary = Color(0xFFFFFFFF)   // Pure white
val CinemaTextSecondary = Color(0xFFB0B0B0) // Light gray
val CinemaTextTertiary = Color(0xFF808080)  // Medium gray
val CinemaTextDisabled = Color(0xFF606060)  // Dark gray

/**
 * Creates a complete Material3 ColorScheme using the Cinema color palette.
 * This replaces the purple-based color scheme entirely.
 */
fun CinemaColorScheme() = androidx.compose.material3.darkColorScheme(
    // Primary colors (main accent - cyan)
    primary = CinemaAccent,
    onPrimary = Color.Black,  // Dark text on cyan for contrast
    primaryContainer = CinemaAccentDark,
    onPrimaryContainer = CinemaAccentLight,

    // Secondary colors (slightly muted cyan)
    secondary = CinemaAccent.copy(alpha = 0.8f),
    onSecondary = Color.Black,
    secondaryContainer = CinemaSurfaceVariant,
    onSecondaryContainer = CinemaAccentLight,

    // Tertiary colors (alternative accent)
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
