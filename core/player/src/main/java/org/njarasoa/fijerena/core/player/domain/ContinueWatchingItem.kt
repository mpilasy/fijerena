package org.njarasoa.fijerena.core.player.domain

import androidx.compose.runtime.Immutable

/**
 * One row of the home-screen "Jump Back In" shelf — a genuinely in-progress Movie or TV Shows
 * entry (see `WatchedItem.resumeProgress` in `MediaRepository`), never Live TV. See
 * `docs/plans/20260923_ui-ux-transitions-flow-uplift-plan.md`, Phase 3.
 */
@Immutable
data class ContinueWatchingItem(
    val id: String,
    val name: String,
    /** The episode's own title for a TV Shows card ([name] is the show); null for a Movie card. */
    val subtitle: String?,
    val contentType: String,
    val categoryId: String,
    val thumbnailUrl: String?,
    /** 0f..1f, inside the resumable band — never 1 (finished); 0 only for an [upNext] card. */
    val progress: Float,
    val remainingMs: Long,
    val target: BrowseTarget,
    /**
     * A show's next episode to start ([subtitle] is its title), not one to resume: the shelf shows
     * "Up next" instead of a progress bar and time remaining.
     */
    val upNext: Boolean = false,
)
