package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What a button does, which decides how it looks ([ButtonStyle]). Call sites pick a kind, never
 * colours, shapes or borders.
 */
enum class ButtonKind {
    /** The one action the screen or dialog is for: Save, Add, Play, OK. */
    Main,

    /** Every other action: Cancel, Back, Retry, Switch to this profile. */
    Other,

    /** Deletes or wipes something: Delete profile, Clear data. */
    Danger,
}

/**
 * How every button in the app looks (docs/plans/20261016_one-button-look-plan.md). This base holds
 * what both apps share — the shape (from the Display style), the three kinds' colours, the outline,
 * the icon buttons' colours — and each app has one subclass with its own sizes: `TvButtonStyle`
 * (tv module, adds focus) and `PhoneButtonStyle` (mobile module). Every button component reads its
 * app's subclass and nothing else, so changing how buttons look is an edit here or there.
 */
abstract class ButtonStyle {
    /** Space between the label and the button's edges, and between an icon and the label. */
    abstract val horizontalPadding: Dp
    abstract val verticalPadding: Dp
    abstract val minHeight: Dp

    /** Side of a round icon-only button. */
    abstract val iconButtonSize: Dp

    val contentPadding: PaddingValues get() = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding)

    val shape: Shape
        @Composable @ReadOnlyComposable
        get() = cornerShape(LocalUiStyle.current.shapes.button)

    @Composable
    @ReadOnlyComposable
    open fun container(kind: ButtonKind): Color =
        when (kind) {
            ButtonKind.Main -> CinemaTheme.colors.accent
            ButtonKind.Other -> CinemaTheme.colors.textPrimary.copy(alpha = OTHER_FILL_ALPHA)
            ButtonKind.Danger -> CinemaTheme.colors.danger
        }

    /** The label on [kind]'s fill: readable on it in every theme (white on a dark accent, black on a light one). */
    @Composable
    @ReadOnlyComposable
    open fun content(kind: ButtonKind): Color =
        when (kind) {
            ButtonKind.Main -> contentOn(CinemaTheme.colors.accent)
            ButtonKind.Other -> CinemaTheme.colors.textPrimary
            ButtonKind.Danger -> contentOn(CinemaTheme.colors.danger)
        }

    /** The outline at rest: only [ButtonKind.Other] has one, so it reads as a button, not a row. */
    @Composable
    @ReadOnlyComposable
    open fun outline(kind: ButtonKind): BorderStroke? =
        if (kind == ButtonKind.Other) BorderStroke(1.dp, CinemaTheme.colors.textPrimary.copy(alpha = CinemaAlpha.textFaint)) else null

    /** An icon-only button at rest: a faint round fill behind the glyph. */
    open val iconContainer: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.textPrimary.copy(alpha = OTHER_FILL_ALPHA)
    open val iconContent: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.textPrimary

    /** An icon-only button that deletes: [ButtonKind.Danger]'s colour, faint at rest. */
    open val dangerIconContainer: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.danger.copy(alpha = DANGER_ICON_FILL_ALPHA)

    open val disabledContainer: Color

        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.surfaceRaised.copy(alpha = CinemaAlpha.scrim)
    open val disabledContent: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.textPrimary.copy(alpha = CinemaAlpha.textFaint)

    private companion object {
        const val OTHER_FILL_ALPHA = 0.15f
        const val DANGER_ICON_FILL_ALPHA = 0.4f
    }
}
