package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FirstVideoPlayerTheme(
    content: @Composable () -> Unit,
) {
    // Use Cinema color scheme - modern dark theme with cyan accents
    // Note: Using TV Material3 darkColorScheme function
    val colorScheme = darkColorScheme(
        // Primary colors (main accent - cyan)
        primary = CinemaAccent,
        onPrimary = androidx.compose.ui.graphics.Color.Black,
        primaryContainer = CinemaAccentDark,
        onPrimaryContainer = CinemaAccentLight,

        // Secondary colors (slightly muted cyan)
        secondary = CinemaAccent.copy(alpha = 0.8f),
        onSecondary = androidx.compose.ui.graphics.Color.Black,
        secondaryContainer = CinemaSurfaceVariant,
        onSecondaryContainer = CinemaAccentLight,

        // Tertiary colors (alternative accent)
        tertiary = CinemaAccentLight,
        onTertiary = androidx.compose.ui.graphics.Color.Black,
        tertiaryContainer = CinemaSurface,
        onTertiaryContainer = CinemaAccentLight,

        // Error colors
        error = CinemaError,
        onError = androidx.compose.ui.graphics.Color.White,
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

        // Borders
        border = CinemaSurfaceLight,

        // Surface tint
        surfaceTint = CinemaAccent,
        inverseSurface = CinemaTextPrimary,
        inverseOnSurface = CinemaBackground,

        // Scrim (overlays, dialogs)
        scrim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}