package org.njarasoa.fijerena.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaTheme
import org.njarasoa.fijerena.core.ui.theme.paletteById

@Composable
fun FirstVideoPlayerTheme(
    themeId: String = "deep_night",
    content: @Composable () -> Unit,
) {
    val palette = paletteById(themeId)

    // Set the global holder so non-composable code (re-export vals) can read it
    CinemaThemeHolder.current = palette

    // Build color scheme inside composable — NOT at file level (was a bug)
    val colorScheme =
        darkColorScheme(
            // Primary - accent color
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.accentDark,
            onPrimaryContainer = palette.accentLight,
            // Secondary - Vivid Orange
            secondary = palette.orange,
            onSecondary = Color.White,
            secondaryContainer = palette.orangeDark,
            onSecondaryContainer = palette.orangeLight,
            // Tertiary - Light accent
            tertiary = palette.accentLight,
            onTertiary = Color.Black,
            tertiaryContainer = palette.surface,
            onTertiaryContainer = palette.accentLight,
            // Error - Red
            error = palette.error,
            onError = Color.White,
            errorContainer = palette.error.copy(alpha = 0.2f),
            onErrorContainer = palette.error.copy(alpha = 0.8f),
            // Background & Surface
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.surfaceLight,
            outlineVariant = palette.surfaceVariant,
            surfaceTint = palette.accent,
            inverseSurface = palette.textPrimary,
            inverseOnSurface = palette.background,
            inversePrimary = palette.accentDark,
            scrim = Color.Black.copy(alpha = 0.5f),
        )

    CompositionLocalProvider(LocalCinemaTheme provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
