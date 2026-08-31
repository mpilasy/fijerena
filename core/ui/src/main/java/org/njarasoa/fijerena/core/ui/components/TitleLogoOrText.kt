package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A detail screen's headline: TMDB's transparent-PNG wordmark art when it has one for this
 * title, otherwise [fallback] — same treatment the player OSD uses. Used on both mobile and TV
 * movie/series detail screens so a title's branded logo shows consistently everywhere it
 * appears, not just during playback.
 *
 * [fallback] is a caller-supplied `Text` rather than a `text`+`style` pair here, because mobile
 * and TV draw from different `Text` composables (`androidx.compose.material3.Text` vs
 * `androidx.tv.material3.Text`, each reading its own `MaterialTheme` composition local) — this
 * component has no correct single choice between them.
 */
@Composable
fun TitleLogoOrText(
    contentDescription: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    logoHeight: Dp = 56.dp,
    fallback: @Composable () -> Unit,
) {
    if (logoUrl != null) {
        AdaptiveLogoImage(
            logoUrl = logoUrl,
            contentDescription = contentDescription,
            modifier = modifier.height(logoHeight),
        )
    } else {
        fallback()
    }
}
