package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.ButtonStyle
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * How buttons look on TV: [ButtonStyle]'s shared look with the TV's sizes, its focus and the
 * button area. Every TV button — text ([CinemaButton] and its kinds, [CinemaButtonArea]) and icon
 * ([CinemaIconButton], [CinemaDangerIconButton], [TvIconAction]) — reads this and nothing else.
 */
object TvButtonStyle : ButtonStyle() {
    override val horizontalPadding: Dp = 24.dp
    override val verticalPadding: Dp = 6.dp
    override val minHeight: Dp = 36.dp
    override val iconButtonSize: Dp = 48.dp

    /**
     * A stack of buttons ([CinemaButtonArea]): an invisible area this share of the page wide,
     * against its right edge. Every button in it is as wide as the widest label, centred in it.
     */
    const val AREA_WIDTH_FRACTION = 0.5f

    /**
     * At rest every text button is quiet, whatever its kind: the same faint fill and outline, so the
     * focused button is the only strong colour on the screen. The main action is the one focus
     * starts on, not a coloured fill. A deleting button keeps its warning in a danger-coloured label.
     * The phone keeps [ButtonStyle]'s coloured fills: with no focus, the fill shows the main action.
     */
    @Composable
    @ReadOnlyComposable
    override fun container(kind: ButtonKind): Color = super.container(ButtonKind.Other)

    @Composable
    @ReadOnlyComposable
    override fun content(kind: ButtonKind): Color =
        if (kind == ButtonKind.Danger) CinemaTheme.colors.danger else CinemaTheme.colors.textPrimary

    // The faint outline on every kind, so a button never reads as a settings row.
    @Composable
    @ReadOnlyComposable
    override fun outline(kind: ButtonKind): BorderStroke? = super.outline(ButtonKind.Other)

    /**
     * Focus (TV visual refresh): every focused button turns solid [TvFocusTokens.focusFill] (white
     * unless the theme sets its own) with the Display style's focus scale and no ring; the label goes
     * dark, or the danger colour on a deleting button. On a theme's own fill a deleting button's label
     * stays dark: red does not read on Indigo's yellow.
     */
    val focusedContainer: Color
        @Composable @ReadOnlyComposable
        get() = TvFocusTokens.focusFill

    @Composable
    @ReadOnlyComposable
    fun focusedContent(kind: ButtonKind): Color =
        if (kind == ButtonKind.Danger && focusedContainer == CinemaTheme.colors.textPrimary) {
            CinemaTheme.colors.danger
        } else {
            TvFocusTokens.onFocusFill
        }

    /**
     * A focused disabled button keeps its dim fill and label, with a ring in the focus colour
     * around it: focus never goes out of sight, and the button still reads as disabled.
     */
    @Composable
    @ReadOnlyComposable
    fun focusedDisabledBorder(shape: Shape): Border = Border(BorderStroke(TvFocusTokens.focusBorderWidth, focusedContainer), shape = shape)

    // Icon buttons: a resting disc that turns white with a dark glyph when focused.
    override val iconContainer: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.surfaceRaised
    val iconFocusedContainer: Color
        @Composable @ReadOnlyComposable
        get() = focusedContainer
    val iconFocusedContent: Color
        @Composable @ReadOnlyComposable
        get() = TvFocusTokens.onFocusFill
    val iconOutline: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.textPrimary.copy(alpha = CinemaAlpha.textLow)

    // A deleting icon button: outlined in the danger colour; white with a danger glyph when focused.
    val dangerIconOutline: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.danger
    val dangerIconFocusedContainer: Color
        @Composable @ReadOnlyComposable
        get() = focusedContainer
    val dangerIconFocusedContent: Color
        @Composable @ReadOnlyComposable
        get() = focusedContent(ButtonKind.Danger)
}
