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
    /** 0f..1f, always inside the resumable band — never 0 (not started) or 1 (finished). */
    val progress: Float,
    val remainingMs: Long,
    val target: BrowseTarget,
)
