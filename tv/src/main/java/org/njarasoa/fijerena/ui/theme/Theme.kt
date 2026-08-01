package org.njarasoa.fijerena.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.darkColorScheme
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

@OptIn(ExperimentalTvMaterial3Api::class)
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

    // Build Material3 color scheme from active palette
    val colorScheme = remember(palette) {
        darkColorScheme(
            // Primary colors
            primary = palette.accent,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            primaryContainer = palette.accentDark,
            onPrimaryContainer = palette.accentLight,
            // Secondary colors (Vivid Orange)
            secondary = palette.orange,
            onSecondary = androidx.compose.ui.graphics.Color.Black,
            secondaryContainer = palette.orangeDark,
            onSecondaryContainer = palette.orangeLight,
            // Tertiary colors (Light accent)
            tertiary = palette.accentLight,
            onTertiary = androidx.compose.ui.graphics.Color.Black,
            tertiaryContainer = palette.surface,
            onTertiaryContainer = palette.accentLight,
            // Error colors
            error = palette.error,
            onError = androidx.compose.ui.graphics.Color.White,
            errorContainer = palette.error.copy(alpha = 0.2f),
            onErrorContainer = palette.error.copy(alpha = 0.8f),
            // Background colors
            background = palette.background,
            onBackground = palette.textPrimary,
            // Surface colors
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            // Borders
            border = palette.surfaceLight,
            // Surface tint
            surfaceTint = palette.accent,
            inverseSurface = palette.textPrimary,
            inverseOnSurface = palette.background,
            // Scrim (overlays, dialogs)
            scrim =
                androidx.compose.ui.graphics.Color.Black
                    .copy(alpha = 0.5f),
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
