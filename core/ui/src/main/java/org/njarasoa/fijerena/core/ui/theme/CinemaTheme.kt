package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one place the app's look is read from (docs/plans/20261016_design-system-plan.md). Everything
 * here is derived from the two inputs — the colour theme ([CinemaThemePalette]) and the Look and
 * Feel ([UiStyle]) — and provided by each app's theme through [ProvideCinemaTheme]. Style classes
 * (`ButtonStyle` and the others) read these roles; components read their style; feature code reads
 * neither, it uses components. A new colour theme or a changed Look and Feel value reaches every
 * screen with no other edit.
 */
object CinemaTheme {
    val colors: CinemaColorRoles
        @Composable @ReadOnlyComposable
        get() = LocalCinemaColors.current

    val type: CinemaTypeRoles
        @Composable @ReadOnlyComposable
        get() = LocalCinemaType.current

    val shapes: CinemaShapeRoles
        @Composable @ReadOnlyComposable
        get() = LocalCinemaShapes.current

    val borders: CinemaBorders
        @Composable @ReadOnlyComposable
        get() = LocalCinemaBorders.current

    val spacing: CinemaSpacing get() = CinemaSpacing
    val motion: CinemaAnimation get() = CinemaAnimation
}

/** Colours by what they are for, never by hue: a theme decides the hues. */
@Immutable
data class CinemaColorRoles(
    val background: Color,
    val surface: Color,
    /** A row, card or control at rest, one step above [surface]. */
    val surfaceRaised: Color,
    /** A focused or pressed row or control: lighter than [surfaceRaised]. */
    val surfaceHigh: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    /** Text and icons on [accent]. */
    val onAccent: Color,
    val danger: Color,
    val onDanger: Color,
    val success: Color,
    val warning: Color,
    val live: Color,
    /** Secondary accent (the palette's orange): badges and highlights that are not the accent. */
    val highlight: Color,
    val outline: Color,
    val divider: Color,
    val scrim: Color,
    val focusRing: Color,
    val glass: Color,
    val glassBorder: Color,
    /** Text and icons over video or artwork, which stay white whatever the theme. */
    val onMedia: Color,
    /** Darkens video or artwork behind text (gradients, chips over posters). */
    val mediaScrim: Color,
    /** Nothing: a container that shows what is behind it. */
    val clear: Color,
) {
    companion object {
        fun from(palette: CinemaThemePalette): CinemaColorRoles =
            CinemaColorRoles(
                background = palette.background,
                surface = palette.surface,
                surfaceRaised = palette.surfaceVariant,
                surfaceHigh = palette.surfaceLight,
                textPrimary = palette.textPrimary,
                textSecondary = palette.textSecondary,
                textTertiary = palette.textTertiary,
                textDisabled = palette.textDisabled,
                accent = palette.accent,
                accentStrong = palette.accentDark,
                accentSoft = palette.accentLight,
                onAccent = palette.onAccent,
                danger = palette.error,
                onDanger = palette.textPrimary,
                success = palette.success,
                warning = palette.warning,
                live = palette.live,
                highlight = palette.orange,
                outline = palette.textPrimary.copy(alpha = CinemaAlpha.textFaint),
                divider = palette.surfaceLight,
                scrim = palette.scrim,
                focusRing = palette.textPrimary,
                glass = palette.glassBackground,
                glassBorder = palette.glassBorder,
                onMedia = Color.White,
                mediaScrim = Color.Black,
                clear = Color.Transparent,
            )
    }
}

/**
 * Text by role (decision D1): every piece of text is one of these. Each app builds them from its
 * typography, which carries the Look and Feel's weights and tracking ([UiTypeTokens]).
 */
@Immutable
data class CinemaTypeRoles(
    /** Hero titles: a film's name on its page. */
    val display: TextStyle,
    /** A screen's title. */
    val title: TextStyle,
    /** A section or row title. */
    val heading: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    /** Buttons, tabs, chips. */
    val label: TextStyle,
    /** Meta lines, hints, badges. */
    val caption: TextStyle,
)

/** Shapes by role, from the Look and Feel's corners ([UiShapeTokens]). */
@Immutable
data class CinemaShapeRoles(
    val button: Shape,
    val chip: Shape,
    val field: Shape,
    val card: Shape,
    val dialog: Shape,
    /** Icon buttons and badges: follow the style (decision D2) — round on Rounded, square on Sharp. */
    val control: Shape,
    /** Avatars and colour dots: round in every style (decision D2), they identify. */
    val identity: Shape,
) {
    companion object {
        fun from(style: UiStyle): CinemaShapeRoles =
            CinemaShapeRoles(
                button = style.shapes.shape(style.shapes.button),
                chip = style.shapes.shape(style.shapes.chip),
                field = style.shapes.shape(style.shapes.chip),
                card = style.shapes.shape(style.shapes.card),
                dialog = style.shapes.shape(style.dialog.cornerRadius),
                control = style.shapes.shape(style.shapes.chip),
                identity = CircleShape,
            )
    }
}

/** Line widths by role. */
@Immutable
data class CinemaBorders(
    /** Dividers and the faintest edges. */
    val hairline: Dp,
    /** The outline of an outlined control at rest. */
    val outline: Dp,
    /** The focus ring (TV), from the style: thicker where the style draws focus with an outline. */
    val focus: Dp,
) {
    companion object {
        fun from(style: UiStyle): CinemaBorders =
            CinemaBorders(
                hairline = 1.dp,
                outline = 1.dp,
                focus = if (style.grid.focusUsesOutline) 3.dp else 2.dp,
            )
    }
}

val LocalCinemaColors = staticCompositionLocalOf { CinemaColorRoles.from(DeepNightPalette) }
val LocalCinemaShapes = staticCompositionLocalOf { CinemaShapeRoles.from(MaterialStyle) }
val LocalCinemaBorders = staticCompositionLocalOf { CinemaBorders.from(MaterialStyle) }
val LocalCinemaType =
    staticCompositionLocalOf {
        CinemaTypeRoles(TextStyle(), TextStyle(), TextStyle(), TextStyle(), TextStyle(), TextStyle(), TextStyle())
    }

/**
 * Provides [CinemaTheme] to [content] from [palette] and [style]; [type] is the app's own text roles
 * (built from its typography). Each app's theme calls this once, around everything.
 */
@Composable
fun ProvideCinemaTheme(
    palette: CinemaThemePalette,
    style: UiStyle,
    type: CinemaTypeRoles,
    content: @Composable () -> Unit,
) {
    val colors = remember(palette) { CinemaColorRoles.from(palette) }
    val shapes = remember(style) { CinemaShapeRoles.from(style) }
    val borders = remember(style) { CinemaBorders.from(style) }
    CompositionLocalProvider(
        LocalCinemaTheme provides palette,
        LocalUiStyle provides style,
        LocalCinemaColors provides colors,
        LocalCinemaShapes provides shapes,
        LocalCinemaBorders provides borders,
        LocalCinemaType provides type,
        content = content,
    )
}
