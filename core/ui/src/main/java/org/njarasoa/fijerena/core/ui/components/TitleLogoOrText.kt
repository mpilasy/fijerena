package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 *
 * A logo that fails to load (TMDB sometimes serves SVG, which this app has no decoder for) falls
 * back to [fallback] too — otherwise the screen had no title at all (TV UI audit, #12).
 */
@Composable
fun TitleLogoOrText(
    contentDescription: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    logoHeight: Dp = 56.dp,
    fallback: @Composable () -> Unit,
) {
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    if (logoUrl != null && !logoFailed) {
        AdaptiveLogoImage(
            logoUrl = logoUrl,
            contentDescription = contentDescription,
            modifier = modifier.height(logoHeight),
            onError = { logoFailed = true },
        )
    } else {
        fallback()
    }
}
