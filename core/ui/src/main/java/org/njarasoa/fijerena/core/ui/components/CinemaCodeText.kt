package org.njarasoa.fijerena.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.ui.theme.UiFonts

/**
 * Machine text — log lines, stack traces, raw details — in a fixed-width font ([UiFonts.Code]),
 * otherwise as [CinemaText]: [role] and [tone] from the theme.
 */
@Composable
fun CinemaCodeText(
    text: String,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.BodySmall,
    tone: TextTone = TextTone.Primary,
    maxLines: Int = Int.MAX_VALUE,
) {
    // look-ok: the component that wraps Material's Text
    Text(
        text = text,
        modifier = modifier,
        style = textStyleOf(role).copy(fontFamily = UiFonts.Code),
        color = colorOf(tone),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
