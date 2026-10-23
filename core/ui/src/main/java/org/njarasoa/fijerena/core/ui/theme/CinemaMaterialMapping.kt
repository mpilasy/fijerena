package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * [CinemaTheme]'s roles handed to Material 3, the one mapping both apps use: the phone themes with
 * it, and the TV for the Material 3 parts inside its screens (dialogs, text fields, selection
 * controls), which read Material 3's own theme — left unprovided that is Material's stock light
 * scheme (white dialogs with a purple button on TV).
 */
fun cinemaMaterialColorScheme(colors: CinemaColorRoles): ColorScheme =
    // Every role is set from the theme, so the dark base's defaults never show — a light theme
    // works through the same call.
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
        // Dialogs land on `surfaceContainerHigh`, and the controls inside them rest on
        // `surfaceVariant` — so the dialog sits one step below that, or an unfocused option is the
        // colour of the panel behind it and disappears.
        surfaceContainerLowest = colors.background,
        surfaceContainerLow = colors.background,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surface,
        surfaceContainerHighest = colors.surfaceRaised,
        surfaceBright = colors.surfaceHigh,
        surfaceDim = colors.background,
        outline = colors.surfaceHigh,
        outlineVariant = colors.surfaceRaised,
        surfaceTint = colors.accent,
        inverseSurface = colors.textPrimary,
        inverseOnSurface = colors.background,
        inversePrimary = colors.accentStrong,
        scrim = colors.scrim,
    )

/** The Look and Feel's corners handed to Material 3 ([Shapes]), for both apps. */
fun cinemaMaterialShapes(style: UiStyle): Shapes =
    Shapes(
        extraSmall = style.shapes.shape(CinemaSpacing.xxs),
        small = style.shapes.shape(style.shapes.chip),
        medium = style.shapes.shape(style.shapes.card),
        large = style.shapes.shape(style.shapes.card + CinemaSpacing.xxs),
        extraLarge = style.shapes.shape(style.shapes.dialog),
    )

/** Black on a light colour, white on a dark one. */
fun contentOn(color: Color): Color = if (color.luminance() > LIGHT_LUMINANCE) Color.Black else Color.White

private const val CONTAINER_ALPHA = 0.2f
private const val LIGHT_LUMINANCE = 0.5f
