package org.njarasoa.fijerena.core.player.domain

import androidx.compose.runtime.Immutable

/**
 * One row of the home-screen "Jump Back In" shelf — a genuinely in-progress Movie or TV Shows
 * entry (see `WatchedItem.resumeProgress` in `MediaRepository`), never Live TV. See
 * the ui ux transitions flow uplift plan, Phase 3.
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
    /** The source the card is on, so it opens there. */
    val providerId: Long = 0L,
    /** When it was last played (epoch ms): the shelf is merged across sources newest first. */
    val lastPlayedAt: Long = 0L,
)

/** The card's key across sources, as `MediaItem.sourceKey`. */
val ContinueWatchingItem.sourceKey: String
    get() = "$providerId/$id"
