package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the Material 3 controls look in both apps — text fields, checkboxes, switches, radio
 * buttons, dividers and panels — read by the components in core/ui `CinemaControls.kt`. Colours
 * and corners come from [CinemaTheme]; only the sizes below are decided here. The same pattern as
 * [ButtonStyle]: change a control's look here and every screen follows.
 */
object ControlStyle {
    /** A panel's (card's) inner padding. */
    val panelPadding: Dp = CinemaSpacing.md

    /** Space between a control and its label in a labelled row. */
    val labelGap: Dp = CinemaSpacing.sm

    /** A field's outline when it is not focused, and focused. */
    val fieldOutline: Dp = 1.dp
    val fieldOutlineFocused: Dp = 2.dp

    /** A progress bar's thickness, and how strongly its track shows. */
    val progressThickness: Dp = 3.dp
    const val PROGRESS_TRACK_ALPHA = 0.25f
}
