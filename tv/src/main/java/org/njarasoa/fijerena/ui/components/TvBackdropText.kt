package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.colorOf
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme

/** Blur of the halo behind [TvBackdropText], in px. */
private const val BACKDROP_TEXT_SHADOW_BLUR = 8f

/**
 * Text drawn straight on a detail page's backdrop art, with no panel behind it: a soft halo in the
 * theme's background colour keeps it legible whatever the frame shows (dark halo on a dark theme,
 * light on a light one). [role] and [tone] as in `CinemaText` — use an explicit tone on TV.
 */
@Composable
fun TvBackdropText(
    text: String,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.Body,
    tone: TextTone = TextTone.Primary,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val haloColor = CinemaTheme.colors.background
    val halo = remember(haloColor) { Shadow(color = haloColor.copy(alpha = CinemaAlpha.textHigh), blurRadius = BACKDROP_TEXT_SHADOW_BLUR) }
    val base = textStyleOf(role)
    val style = remember(base, halo) { base.copy(shadow = halo) }
    // look-ok: the component that wraps Material's Text
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = colorOf(tone),
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}
