package org.njarasoa.fijerena.core.ui.viewmodels

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
