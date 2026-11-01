package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * The TV's one place for the app's plain messages ([AppNotices]), in place of a `Toast`: a bar as
 * [TvUndoBar]'s, at the bottom centre of the screen, clear of the overscan edge, for
 * [CinemaAnimation.toastDismissMs]. A new message takes the place of the one shown. Not focusable:
 * focus stays where it is. Put it once, over the screens.
 */
@Composable
fun TvNoticeHost(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { AppNotices.messages.collect { text = it } }
    val shown = text ?: return
    LaunchedEffect(shown) {
        delay(CinemaAnimation.toastDismissMs)
        text = null
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        TvMessageBar(icon = CinemaIcons.Info, text = shown, modifier = Modifier.padding(bottom = Spacing.tvSafeMarginVertical))
    }
}
