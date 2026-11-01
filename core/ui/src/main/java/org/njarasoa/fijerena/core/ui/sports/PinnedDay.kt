package org.njarasoa.fijerena.core.ui.sports

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.panel

// A games list's day headings stay pinned at the top (sticky headers) until the next day's
// heading takes their place (user, 2026-11-01), on TV and phone.

/** Content type of a day heading in a games list: [rememberPinnedDayKey] looks for it. */
const val DAY_HEADING = "day"

/** The key of the day heading pinned at the top of [state]'s list; null while the list is at its top. */
@Composable
fun rememberPinnedDayKey(state: LazyListState): Any? {
    val key by remember(state) {
        derivedStateOf {
            if (!state.canScrollBackward) {
                null
            } else {
                state.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.contentType == DAY_HEADING && it.offset <= 0 }
                    ?.key
            }
        }
    }
    return key
}

/** A day heading's band: the ground's colour while it is [pinned], so the games scroll under it; none elsewhere. */
@Composable
fun Modifier.dayHeadingBand(pinned: Boolean): Modifier = fillMaxWidth().then(if (pinned) Modifier.panel(PanelFill.Ground) else Modifier)
