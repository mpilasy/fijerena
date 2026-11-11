package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.ControlStyle

/**
 * A detail screen's headline: TMDB's transparent-PNG wordmark when it has one for this title, else
 * [fallback], as the player OSD does. Used on the mobile and TV movie/series detail screens.
 *
 * [fallback] is a caller's `Text`, not a `text`+`style` pair: mobile and TV use different `Text`
 * composables (`androidx.compose.material3.Text` vs `androidx.tv.material3.Text`), each reading
 * its own `MaterialTheme`.
 *
 * A logo that fails to load shows [fallback] too, so the screen always has a title.
 */
@Composable
fun TitleLogoOrText(
    contentDescription: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    logoHeight: Dp = ControlStyle.titleLogoHeight,
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
        // The caller's placement holds for the text too (the phone backdrop puts it at the foot).
        Box(modifier) { fallback() }
    }
}
