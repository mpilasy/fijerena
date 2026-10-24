package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import org.njarasoa.fijerena.core.ui.theme.CinemaBorders
import org.njarasoa.fijerena.core.ui.theme.CinemaColorRoles
import org.njarasoa.fijerena.core.ui.theme.CinemaShapeRoles
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder
import org.njarasoa.fijerena.core.ui.theme.CinemaThemePalette
import org.njarasoa.fijerena.core.ui.theme.CinemaTypeRoles
import org.njarasoa.fijerena.core.ui.theme.DialogPosition
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaBorders
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaColors
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaShapes
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaType
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.ProvideCinemaTheme
import org.njarasoa.fijerena.core.ui.theme.ProvideMediaTheme
import org.njarasoa.fijerena.core.ui.theme.UiShapeTokens
import org.njarasoa.fijerena.core.ui.theme.UiStyle
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

/**
 * [content] drawn in the Look and Feel [style], whatever Look is in use — its type, corners, borders
 * and focus effect: each row of the Look picker shows how that Look would look. Colours stay.
 */
@Composable
fun ProvideLook(
    style: UiStyle,
    content: @Composable () -> Unit,
) {
    val type = remember(style) { cinemaTypeRoles(cinemaTypography(style.type)) }
    CompositionLocalProvider(
        LocalUiStyle provides style,
        LocalCinemaType provides type,
        LocalCinemaShapes provides remember(style) { CinemaShapeRoles.from(style) },
        LocalCinemaBorders provides remember(style) { CinemaBorders.from(style) },
        content = content,
    )
}

/** TV Material's colours from [CinemaTheme]'s roles. */
internal fun cinemaTvColorScheme(colors: CinemaColorRoles) =
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
    styleId: String = "modern",
    content: @Composable () -> Unit,
) {
    val palette = remember(themeId) { paletteById(themeId) }
    val style = remember(styleId) { tvStyle(styleId) }

    // Set the global holders so non-composable code (re-export vals) can read them
    SideEffect {
        CinemaThemeHolder.current = palette
        UiStyleHolder.current = style
    }

    ProvideTvTheme(palette, style, content)
}

/**
 * [content] drawn in the colour theme [themeId] and the Look and Feel [styleId], as a whole screen
 * would be, without changing the theme in use (the global holders stay): a picker's preview.
 */
@Composable
fun ThemePreview(
    themeId: String,
    styleId: String,
    content: @Composable () -> Unit,
) {
    val palette = remember(themeId) { paletteById(themeId) }
    val style = remember(styleId) { tvStyle(styleId) }
    ProvideTvTheme(palette, style, content)
}

/**
 * [content] in the Look and Feel [styleId] (type, corners, focus) with the accent of the colour
 * theme [themeId]; the other colours stay, so text keeps its contrast on the pane (the soft accent of
 * a selected row's text too): a picker row that shows a theme's font and accent.
 */
@Composable
fun ProvideLookAndAccent(
    themeId: String,
    styleId: String,
    content: @Composable () -> Unit,
) {
    val current = CinemaTheme.colors
    val colors =
        remember(themeId, current) {
            val theme = CinemaColorRoles.from(paletteById(themeId))
            current.copy(
                accent = theme.accent,
                accentStrong = theme.accentStrong,
                onAccent = theme.onAccent,
            )
        }
    ProvideLook(styleById(styleId)) {
        CompositionLocalProvider(LocalCinemaColors provides colors, content = content)
    }
}

// Decision D3 (design-system plan): dialogs stay centred on TV in every Look and Feel — a bottom
// sheet or a full-screen dialog doesn't suit the remote.
private fun tvStyle(styleId: String): UiStyle =
    styleById(styleId).let { it.copy(dialog = it.dialog.copy(position = DialogPosition.CENTERED)) }

/** [CinemaTheme], Material 3 and TV Material from [palette] and [style]. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProvideTvTheme(
    palette: CinemaThemePalette,
    style: UiStyle,
    content: @Composable () -> Unit,
) {
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

/** [ProvideMediaTheme] for TV: the player over video stays dark in every theme, TV Material included. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMediaTheme(content: @Composable () -> Unit) {
    ProvideMediaTheme {
        val media = CinemaTheme.colors
        MaterialTheme(
            colorScheme = remember(media) { cinemaTvColorScheme(media) },
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
