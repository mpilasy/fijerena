package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone

/**
 * [CinemaText] in the content colour of the TV surface around it — a row's, list item's or card's,
 * which TV Material changes on focus, selection and disable ([org.njarasoa.fijerena.ui.theme.TvFocusTokens]).
 * TV Surfaces set TV Material's content colour, not Material 3's that [TextTone.Inherit] reads, so
 * this hands one to the other. Outside a TV surface use [CinemaText] with an explicit tone.
 */
@Composable
fun TvContentText(
    text: String,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.Body,
    emphasis: Boolean = false,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides androidx.tv.material3.LocalContentColor.current,
    ) {
        CinemaText(
            text = text,
            modifier = modifier,
            role = role,
            tone = TextTone.Inherit,
            emphasis = emphasis,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}
