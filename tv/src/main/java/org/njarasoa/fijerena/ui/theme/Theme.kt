package org.njarasoa.fijerena.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.darkColorScheme
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.UiShapeTokens
import org.njarasoa.fijerena.core.ui.theme.UiStyleHolder
import org.njarasoa.fijerena.core.ui.theme.paletteById
import org.njarasoa.fijerena.core.ui.theme.styleById

/**
 * Non-TV Material3 mirror of the palette.
 *
 * TV screens only theme `androidx.tv.material3.MaterialTheme`, but the dialogs, text fields and
 * selection controls inside them come from `androidx.compose.material3`, which reads its *own*
 * `MaterialTheme`. Left unprovided that resolves to Material3's stock **light** scheme, which is
 * why every [org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog] on TV rendered as a white
 * panel with a purple button regardless of the selected theme.
 *
 * The `surfaceContainer*` roles matter specifically: `AlertDialogDefaults.containerColor` resolves
 * to `surfaceContainerHigh`.
 */
private fun cinemaMaterialColorScheme(palette: org.njarasoa.fijerena.core.ui.theme.CinemaThemePalette) =
    androidx.compose.material3.darkColorScheme(
        primary = palette.accent,
        onPrimary = androidx.compose.ui.graphics.Color.White,
        primaryContainer = palette.accentDark,
        onPrimaryContainer = palette.accentLight,
        secondary = palette.orange,
        onSecondary = androidx.compose.ui.graphics.Color.Black,
        secondaryContainer = palette.orangeDark,
        onSecondaryContainer = palette.orangeLight,
        tertiary = palette.accentLight,
        onTertiary = androidx.compose.ui.graphics.Color.Black,
        tertiaryContainer = palette.surface,
        onTertiaryContainer = palette.accentLight,
        error = palette.error,
        onError = androidx.compose.ui.graphics.Color.White,
        errorContainer = palette.error.copy(alpha = 0.2f),
        onErrorContainer = palette.error.copy(alpha = 0.8f),
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceVariant,
        onSurfaceVariant = palette.textSecondary,
        // Dialogs land on `surfaceContainerHigh`, and the controls inside them rest on
        // `surfaceVariant` — so the dialog has to sit one step *below* that, or an unfocused
        // option is the same colour as the panel behind it and disappears.
        surfaceContainerLowest = palette.background,
        surfaceContainerLow = palette.background,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surface,
        surfaceContainerHighest = palette.surfaceVariant,
        outline = palette.surfaceLight,
        outlineVariant = palette.surfaceVariant,
        surfaceTint = palette.accent,
        inverseSurface = palette.textPrimary,
        inverseOnSurface = palette.background,
        scrim =
            androidx.compose.ui.graphics.Color.Black
                .copy(alpha = 0.5f),
    )

private fun cinemaMaterialShapes(tokens: UiShapeTokens) =
    androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(CinemaSpacing.xxs),
        small = RoundedCornerShape(tokens.chip),
        medium = RoundedCornerShape(tokens.card),
        large = RoundedCornerShape(tokens.card + CinemaSpacing.xxs),
        extraLarge = RoundedCornerShape(tokens.dialog),
    )

private fun cinemaShapes(tokens: UiShapeTokens) =
    Shapes(
        extraSmall = RoundedCornerShape(CinemaSpacing.xxs),
        small = RoundedCornerShape(tokens.chip),
        medium = RoundedCornerShape(tokens.card),
        large = RoundedCornerShape(tokens.card + CinemaSpacing.xxs),
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

    val typography = remember(style) { cinemaTypography(style.type) }

    CompositionLocalProvider(LocalCinemaTheme provides palette, LocalUiStyle provides style) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = remember(palette) { cinemaMaterialColorScheme(palette) },
            shapes = remember(style) { cinemaMaterialShapes(style.shapes) },
            typography = remember(typography) { cinemaMaterialTypography(typography) },
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
                shapes = remember(style) { cinemaShapes(style.shapes) },
                content = content,
            )
        }
    }
}
