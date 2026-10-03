package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Glow
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle

/**
 * TV Focus & Interaction Tokens
 * Scale, border, and glow values for D-pad focus states, driven by the active look-and-feel
 * style's [org.njarasoa.fijerena.core.ui.theme.UiGridTokens] where the style expresses an opinion
 * (focus scale, outline weight) — press feedback and elevation stay fixed across styles.
 *
 * Focus and selection are two independent channels and must stay simultaneously legible: a focused
 * row that happens to be unselected, and a selected row that happens to be unfocused, both have to
 * read from ten feet. Focus lifts the element (scale, outline, [focusedContainer]); "selected" or
 * "current" (the category being browsed, the channel playing, the episode to continue, the active
 * Settings group or source, a checked option) keeps the resting container and adds a
 * [currentBarWidth] bar in [currentAccent] on the leading edge plus [currentText] for the title
 * (UX overhaul plan Part II P5) — never a fill or a full outline, which read as a second focus.
 */
object TvFocusTokens {
    val focusedScale: Float
        @Composable @ReadOnlyComposable
        get() = LocalUiStyle.current.grid.focusScale

    val focusedScaleSubtle: Float
        @Composable @ReadOnlyComposable
        get() = 1f + (LocalUiStyle.current.grid.focusScale - 1f) * 0.5f

    val focusedScaleContent: Float
        @Composable @ReadOnlyComposable
        get() = 1f + (LocalUiStyle.current.grid.focusScale - 1f) * 0.5f

    const val pressedScale = 0.95f
    const val pressedScaleSubtle = 0.98f
    const val defaultScale = 1.0f

    /**
     * Floor on the focus outline. [org.njarasoa.fijerena.core.ui.theme.RokuStyle] pins
     * `focusScale` to 1.0, so on that style the outline is the *only* focus cue and a hairline
     * leaves focus invisible.
     */
    val minFocusBorderWidth: Dp = 2.dp

    val focusBorderWidth: Dp
        @Composable @ReadOnlyComposable
        get() =
            (if (LocalUiStyle.current.grid.focusUsesOutline) 3.dp else 1.5.dp).coerceAtLeast(minFocusBorderWidth)

    /** Resting container for an interactive row or button. */
    val restingContainer: Color
        @Composable @ReadOnlyComposable
        get() = CinemaSurfaceVariant

    /**
     * Container behind the focused row or button. Must stay *lighter* than [restingContainer]:
     * focus should lift an element toward the viewer, and the previous mapping (`CinemaSurface`)
     * was darker than rest on every palette, which read as the element receding.
     */
    val focusedContainer: Color
        @Composable @ReadOnlyComposable
        get() = CinemaSurfaceLight

    /** Width of the "current" bar on a selected / current row's leading edge (see `Modifier.currentIndicator`). */
    val currentBarWidth: Dp = 4.dp

    /** Colour of the "current" bar. The full accent, not [CinemaAccentLight], so it never reads as the focus outline. */
    val currentAccent: Color
        @Composable @ReadOnlyComposable
        get() = CinemaAccent

    /**
     * Title colour on a selected / current row, focused or not. The light accent, so it stays
     * legible on both [restingContainer] and the focused containers.
     */
    val currentText: Color
        @Composable @ReadOnlyComposable
        get() = CinemaAccentLight

    /**
     * Focus ring on a profile avatar (F-PP-1): twice [focusBorderWidth], since a ring the width of
     * a row's outline vanished against a coloured avatar at ten feet.
     */
    val avatarFocusBorderWidth: Dp
        @Composable @ReadOnlyComposable
        get() = focusBorderWidth * 2

    /**
     * Focus glow, honouring [org.njarasoa.fijerena.core.ui.theme.UiGridTokens.focusUsesShadow]:
     * Cupertino and BRAVIA lift focused elements with a shadow, Material and Roku do not.
     * [Glow.None] on the styles that opt out, so this can be passed unconditionally.
     */
    val focusedGlow: Glow
        @Composable @ReadOnlyComposable
        get() =
            if (LocalUiStyle.current.grid.focusUsesShadow) {
                Glow(
                    elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                    elevation = focusShadowElevation,
                )
            } else {
                Glow.None
            }

    /**
     * Weight for text that carries selection. Taken from the active style rather than a literal
     * [FontWeight.Bold] so Roku's heavier emphasis (800) and Cupertino's lighter one (500) both
     * come through — the typography scale gets the same treatment in `Type.kt`, and a raw literal
     * here would opt this text out of it.
     */

    /**
     * Ambient glow for elements that should read as raised even before focus — hero cards, not
     * list rows (those already lift on [focusedGlow] alone). Same style opt-out as [focusedGlow]
     * and a dimmer, constant elevation since it must never outshine the focus state.
     */
    val restingGlow: Glow
        @Composable @ReadOnlyComposable
        get() =
            if (LocalUiStyle.current.grid.focusUsesShadow) {
                Glow(
                    elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.ghost),
                    elevation = glowElevation,
                )
            } else {
                Glow.None
            }

    val emphasisWeight: FontWeight
        @Composable @ReadOnlyComposable
        get() = FontWeight(LocalUiStyle.current.type.weightEmphasis)

    val regularWeight: FontWeight
        @Composable @ReadOnlyComposable
        get() = FontWeight(LocalUiStyle.current.type.weightRegular)

    val borderDefault: Dp = 1.dp
    val borderThin: Dp = 0.5.dp
    val glowElevation: Dp = 8.dp
    val focusShadowElevation: Dp = 16.dp
}
