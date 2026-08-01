package org.njarasoa.fijerena.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.UiShapeTokens
import org.njarasoa.fijerena.core.ui.theme.UiStyleHolder
import org.njarasoa.fijerena.core.ui.theme.paletteById
import org.njarasoa.fijerena.core.ui.theme.styleById

private fun cinemaShapes(tokens: UiShapeTokens) =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(tokens.chip),
        medium = RoundedCornerShape(tokens.card),
        large = RoundedCornerShape(tokens.card + 4.dp),
        extraLarge = RoundedCornerShape(tokens.dialog),
    )

@Composable
fun FirstVideoPlayerTheme(
    themeId: String = "deep_night",
    styleId: String = "material",
    content: @Composable () -> Unit,
) {
    val palette = remember(themeId) { paletteById(themeId) }
    val style = remember(styleId) { styleById(styleId) }

    // Set the global holders so non-composable code (re-export vals) can read them
    SideEffect {
        CinemaThemeHolder.current = palette
        UiStyleHolder.current = style
    }

    val colorScheme = remember(palette) {
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
    }

    CompositionLocalProvider(LocalCinemaTheme provides palette, LocalUiStyle provides style) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember(style) { cinemaTypography(style.type) },
            shapes = remember(style) { cinemaShapes(style.shapes) },
            content = content,
        )
    }
}
