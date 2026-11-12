package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle

/** TV corner radii, from the active look-and-feel style. */
object CornerRadius {
    val small: Dp
        @Composable @ReadOnlyComposable
        get() = LocalUiStyle.current.shapes.chip // Buttons, small cards, chips

    /**
     * Focusable rows (settings, toggle and input rows): the chip corner, but not more than
     * [ROW_MAX]. A pill Look's one-line row stays a pill. A tall row (a source's state and its
     * reason) keeps its first and last lines inside the corner, which clips the row's content.
     */
    val row: Dp
        @Composable @ReadOnlyComposable
        get() = minOf(LocalUiStyle.current.shapes.chip, ROW_MAX)

    val medium: Dp
        @Composable @ReadOnlyComposable
        get() = LocalUiStyle.current.shapes.card // Standard cards, list items

    val large: Dp
        @Composable @ReadOnlyComposable
        get() = LocalUiStyle.current.shapes.dialog // Dialogs, large surfaces, modals

    val xLarge: Dp
        @Composable @ReadOnlyComposable
        get() = LocalUiStyle.current.shapes.card + 4.dp // Poster cards, glass panels

    /** Half the height of a one-line row: on such a row a larger corner is still a pill. */
    private val ROW_MAX = 28.dp
}
