package org.njarasoa.fijerena.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CinemaColorScheme = darkColorScheme(
    // Primary - Electric Blue
    primary = CinemaAccent,
    onPrimary = Color.White,
    primaryContainer = CinemaAccentDark,
    onPrimaryContainer = CinemaAccentLight,

    // Secondary - Vivid Orange
    secondary = CinemaOrange,
    onSecondary = Color.White,
    secondaryContainer = CinemaOrangeDark,
    onSecondaryContainer = CinemaOrangeLight,

    // Tertiary - Light Blue
    tertiary = CinemaAccentLight,
    onTertiary = Color.Black,
    tertiaryContainer = CinemaSurface,
    onTertiaryContainer = CinemaAccentLight,

    // Error - Red
    error = CinemaError,
    onError = Color.White,
    errorContainer = CinemaError.copy(alpha = 0.2f),
    onErrorContainer = CinemaError.copy(alpha = 0.8f),

    // Background & Surface - Deep Night palette
    background = CinemaBackground,
    onBackground = CinemaTextPrimary,

    surface = CinemaSurface,
    onSurface = CinemaTextPrimary,
    surfaceVariant = CinemaSurfaceVariant,
    onSurfaceVariant = CinemaTextSecondary,

    outline = CinemaSurfaceLight,
    outlineVariant = CinemaSurfaceVariant,

    surfaceTint = CinemaAccent,
    inverseSurface = CinemaTextPrimary,
    inverseOnSurface = CinemaBackground,
    inversePrimary = CinemaAccentDark,

    scrim = Color.Black.copy(alpha = 0.5f)
)

@Composable
fun FirstVideoPlayerTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CinemaColorScheme,
        typography = Typography,
        content = content
    )
}
