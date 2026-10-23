package org.njarasoa.fijerena.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.cornerShape

/**
 * How the phone's bottom bar ([MobileBottomBar]) looks, in one place: a floating bar lifted off the
 * screen's edges, its corners from the Display style, the tab in use in a lighter pill with its icon and name in the
 * accent (under Console a solid accent block), the others white.
 */
object MobileDockStyle {
    /** Space between the bar and the screen's sides, and under it (above the system bar). */
    val sideMargin: Dp = 12.dp
    val bottomMargin: Dp = 8.dp

    /** The Display style's button corner, as every button: a pill on Rounded, square on Sharp. */
    val barShape: Shape
        @Composable @ReadOnlyComposable
        get() = cornerShape(LocalUiStyle.current.shapes.button)
    val barContainer: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.surfaceRaised.copy(alpha = BAR_ALPHA)
    val barOutline: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.textPrimary.copy(alpha = OUTLINE_ALPHA)
    val barOutlineWidth: Dp = 1.dp

    /** Space between the bar's edge and the tabs. */
    val barPadding: Dp = 6.dp

    /** The tab in use's pill: the bar's corner less the space between them, so the two curves run parallel. */
    val tabShape: Shape
        @Composable @ReadOnlyComposable
        get() = cornerShape((LocalUiStyle.current.shapes.button - barPadding).coerceAtLeast(0.dp))
    val tabVerticalPadding: Dp = 8.dp

    /** Under a Look with a frame (Console) the tab in use is a solid accent block, its content dark. */
    val selectedTabContainer: Color
        @Composable @ReadOnlyComposable
        get() =
            if (LocalUiStyle.current.frame != null) {
                CinemaTheme.colors.accent
            } else {
                CinemaTheme.colors.textPrimary.copy(alpha = SELECTED_FILL_ALPHA)
            }
    val selectedContent: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalUiStyle.current.frame != null) CinemaTheme.colors.onAccent else CinemaTheme.colors.accent
    val content: Color
        @Composable @ReadOnlyComposable
        get() = CinemaTheme.colors.textPrimary
    val iconSize: Dp = 26.dp
    val iconToLabel: Dp = 2.dp

    private const val BAR_ALPHA = 0.88f
    private const val OUTLINE_ALPHA = 0.08f
    private const val SELECTED_FILL_ALPHA = 0.10f
}

/**
 * Space the floating bar covers at the bottom of the screen (its height with its margins and the
 * system bar), 0 while it is hidden. A screen under the bar ends its scrolling content with it —
 * `contentPadding` bottom, or a spacer at the end of a scrolled column — so its last row can scroll
 * clear of the bar while the rest runs behind it.
 */
val LocalDockInset = compositionLocalOf { 0.dp }
