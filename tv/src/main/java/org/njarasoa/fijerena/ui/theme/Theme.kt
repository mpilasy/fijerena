package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import org.njarasoa.fijerena.core.ui.theme.CinemaColorRoles
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder
import org.njarasoa.fijerena.core.ui.theme.CinemaTypeRoles
import org.njarasoa.fijerena.core.ui.theme.DialogPosition
import org.njarasoa.fijerena.core.ui.theme.ProvideCinemaTheme
import org.njarasoa.fijerena.core.ui.theme.UiShapeTokens
import org.njarasoa.fijerena.core.ui.theme.UiStyleHolder
import org.njarasoa.fijerena.core.ui.theme.cinemaMaterialColorScheme
import org.njarasoa.fijerena.core.ui.theme.cinemaMaterialShapes
import org.njarasoa.fijerena.core.ui.theme.contentOn
import org.njarasoa.fijerena.core.ui.theme.paletteById
import org.njarasoa.fijerena.core.ui.theme.shape
import org.njarasoa.fijerena.core.ui.theme.styleById

private fun cinemaShapes(tokens: UiShapeTokens) =
    Shapes(
        extraSmall = tokens.shape(CinemaSpacing.xxs),
        small = tokens.shape(tokens.chip),
        medium = tokens.shape(tokens.card),
        large = tokens.shape(tokens.card + CinemaSpacing.xxs),
        extraLarge = tokens.shape(tokens.dialog),
    )

/** [CinemaTheme]'s text roles from the TV's typography (which carries the Look and Feel). */
private fun cinemaTypeRoles(typography: Typography) =
    CinemaTypeRoles(
        display = typography.headlineMedium,
        title = typography.titleLarge,
        heading = typography.titleMedium,
        body = typography.bodyMedium,
        bodySmall = typography.bodySmall,
        label = typography.labelLarge,
        caption = typography.labelSmall,
    )

/** TV Material's colours from [CinemaTheme]'s roles. */
private fun cinemaTvColorScheme(colors: CinemaColorRoles) =
    darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accentStrong,
        onPrimaryContainer = colors.accentSoft,
        secondary = colors.highlight,
        onSecondary = contentOn(colors.highlight),
        secondaryContainer = colors.highlight.copy(alpha = CONTAINER_ALPHA),
        onSecondaryContainer = colors.highlight,
        tertiary = colors.accentSoft,
        onTertiary = contentOn(colors.accentSoft),
        tertiaryContainer = colors.surface,
        onTertiaryContainer = colors.accentSoft,
        error = colors.danger,
        onError = colors.onDanger,
        errorContainer = colors.danger.copy(alpha = CONTAINER_ALPHA),
        onErrorContainer = colors.danger,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceRaised,
        onSurfaceVariant = colors.textSecondary,
        border = colors.surfaceHigh,
        surfaceTint = colors.accent,
        inverseSurface = colors.textPrimary,
        inverseOnSurface = colors.background,
        scrim = colors.scrim,
    )

private const val CONTAINER_ALPHA = 0.2f

/**
 * The TV's theme: [CinemaTheme] from the colour theme [themeId] and the Look and Feel [styleId];
 * TV Material and Material 3 (dialogs, text fields, selection controls inside TV screens) both
 * themed from it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FirstVideoPlayerTheme(
    themeId: String = "deep_night",
    styleId: String = "material",
    content: @Composable () -> Unit,
) {
    val palette = remember(themeId) { paletteById(themeId) }
    // Decision D3 (design-system plan): dialogs stay centred on TV in every Look and Feel — a bottom
    // sheet or a full-screen dialog doesn't suit the remote.
    val style =
        remember(styleId) {
            styleById(styleId).let { it.copy(dialog = it.dialog.copy(position = DialogPosition.CENTERED)) }
        }

    // Set the global holders so non-composable code (re-export vals) can read them
    SideEffect {
        CinemaThemeHolder.current = palette
        UiStyleHolder.current = style
    }

    val colors = remember(palette) { CinemaColorRoles.from(palette) }
    val typography = remember(style) { cinemaTypography(style.type) }
    ProvideCinemaTheme(palette, style, remember(typography) { cinemaTypeRoles(typography) }) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = remember(colors) { cinemaMaterialColorScheme(colors) },
            shapes = remember(style) { cinemaMaterialShapes(style) },
            typography = remember(typography) { cinemaMaterialTypography(typography) },
        ) {
            MaterialTheme(
                colorScheme = remember(colors) { cinemaTvColorScheme(colors) },
                typography = typography,
                shapes = remember(style) { cinemaShapes(style.shapes) },
                content = content,
            )
        }
    }
}
