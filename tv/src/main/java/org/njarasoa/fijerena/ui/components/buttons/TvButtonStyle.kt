package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.ButtonStyle
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme

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

    /** Focus: the button keeps its look and gains this ring and the Display style's focus scale. */
    val focusRing: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.textPrimary

    // Icon buttons: a resting disc that lifts to a lighter one with an accent ring when focused.
    override val iconContainer: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.surfaceRaised
    val iconFocusedContainer: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.surfaceHigh
    val iconFocusedContent: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.accentSoft
    val iconOutline: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.textPrimary.copy(alpha = CinemaAlpha.textLow)
    val iconFocusRing: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.accentSoft

    // A deleting icon button: outlined in the danger colour, filled with it when focused.
    val dangerIconOutline: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.danger
    val dangerIconFocusedContainer: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.danger
}
