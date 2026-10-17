package org.njarasoa.fijerena.ui.components.text

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.colorOf
import org.njarasoa.fijerena.core.ui.components.textStyleOf

/**
 * Machine text — log lines, stack traces, raw details — in a fixed-width font, otherwise as
 * [org.njarasoa.fijerena.core.ui.components.CinemaText]: [role] and [tone] from the theme.
 */
@Composable
fun CinemaCodeText(
    text: String,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.BodySmall,
    tone: TextTone = TextTone.Primary,
) {
    Text(
        text = text,
        modifier = modifier,
        style = textStyleOf(role).copy(fontFamily = FontFamily.Monospace),
        color = colorOf(tone),
    )
}
