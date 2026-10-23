package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.ButtonStyle

/**
 * How buttons look on the phone: [ButtonStyle]'s shared look with the phone's sizes. Every phone
 * button — text ([CinemaDialogActionButton] and the [CinemaButton] family) and icon
 * ([CinemaIconButton], [DetailIconAction], [CinemaDangerIconButton]) — reads this and nothing else.
 */
object PhoneButtonStyle : ButtonStyle() {
    override val horizontalPadding: Dp = 20.dp
    override val verticalPadding: Dp = 10.dp
    override val minHeight: Dp = 40.dp
    override val iconButtonSize: Dp = 40.dp
}
