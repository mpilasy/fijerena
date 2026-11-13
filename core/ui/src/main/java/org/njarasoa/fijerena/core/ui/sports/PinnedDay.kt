package org.njarasoa.fijerena.core.ui.sports

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.panel

// A games list's day headings stay pinned at the top (sticky headers) until the next day's
// heading takes their place, on TV and phone.

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

/** Content type of the item that ends a games list's days ([endOfDays]). */
const val END_OF_DAYS = "end_of_days"

/** Content type of the rows after a games list's days (a league's teams, a team's results, their headings). */
const val AFTER_DAYS = "after_days"

/**
 * Marks the end of the games' days, before the rows after them (a league's teams, a team's
 * results): an item of no height that [rememberEndedDayKey] looks for.
 */
fun LazyListScope.endOfDays(key: String) = item(key = key, contentType = END_OF_DAYS) { Spacer(Modifier) }

/**
 * The key of the pinned day heading whose day has ended: the rows after the games ([AFTER_DAYS],
 * [endOfDays]) are at the top under it, or begin just below it. That heading is hidden (it keeps
 * its size, so nothing moves): no later day heading comes to push it away, and it would stay over
 * rows that are not of its day. Null otherwise.
 */
@Composable
fun rememberEndedDayKey(state: LazyListState): Any? {
    val key by remember(state) {
        derivedStateOf {
            val items = state.layoutInfo.visibleItemsInfo
            val pinned = items.firstOrNull { it.contentType == DAY_HEADING && it.offset <= 0 }
            val under = pinned?.let { day -> items.filter { it.index > day.index } }.orEmpty()
            val atTop = under.firstOrNull()?.contentType.let { it == AFTER_DAYS || it == END_OF_DAYS }
            val endNear = under.any { it.contentType == END_OF_DAYS && it.offset < pinned!!.size * 3 / 2 }
            if (state.canScrollBackward && pinned != null && (atTop || endNear)) pinned.key else null
        }
    }
    return key
}
