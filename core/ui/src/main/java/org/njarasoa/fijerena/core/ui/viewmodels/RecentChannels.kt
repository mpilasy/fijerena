package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.njarasoa.fijerena.core.player.domain.MediaItem

/** How a Recent channel list treats the channel that is currently previewing or playing. */
enum class CurrentChannelPolicy {
    /**
     * Keep it reachable: a freshly-picked channel (from search, browse or the EPG) may not have
     * reached watch history yet — the write is delayed, and the preview path doesn't write at
     * all — so prepend it when absent. For the preview panel, where it is also the highlighted
     * row and the one an OK-press promotes to full screen.
     */
    INCLUDE,

    /**
     * Filter it out: for the "switch to something else" flyout, where offering the channel
     * already playing is just a dead row.
     */
    EXCLUDE,
}

/**
 * This list re-ordered to match what is already on screen: rows the viewer can see keep their
 * relative positions, and only genuinely new entries appear (at the top, newest-first).
 *
 * Recent is ordered by recency, so recording a watch promotes that entry to the front. Doing
 * that live would slide rows around under a viewer who is mid-scroll — the channel they were
 * about to press jumps somewhere else. Freezing the order while the list is displayed keeps it
 * predictable; it is re-sorted the next time the list is opened or explicitly refreshed.
 */
fun List<MediaItem>.inDisplayOrderOf(displayed: List<MediaItem>): List<MediaItem> {
    val positions = displayed.withIndex().associate { (index, item) -> item.id to index }
    val (known, fresh) = partition { it.id in positions }
    return fresh + known.sortedBy { positions.getValue(it.id) }
}

/**
 * [items] held in a stable display order for as long as this surface is showing them — see
 * [inDisplayOrderOf]. Changing [resetKey] (the list being reopened, or an explicit refresh)
 * adopts the incoming order as-is.
 */
@Composable
fun rememberStableRecentOrder(
    items: List<MediaItem>,
    resetKey: Any? = Unit,
): List<MediaItem> {
    var displayed by remember(resetKey) { mutableStateOf(items) }
    LaunchedEffect(items, resetKey) {
        displayed = items.inDisplayOrderOf(displayed)
    }
    return displayed
}

/** Applies [policy] to this Recent list. A null [current] leaves the list untouched. */
fun List<MediaItem>.withCurrentChannel(
    current: MediaItem?,
    policy: CurrentChannelPolicy,
): List<MediaItem> =
    when {
        current == null -> this
        policy == CurrentChannelPolicy.EXCLUDE -> filter { it.id != current.id }
        any { it.id == current.id } -> this
        else -> listOf(current) + this
    }
