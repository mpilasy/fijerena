package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Immutable
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram

/**
 * @Immutable wrappers for collection types passed to Compose functions.
 *
 * Compose treats raw List/Map/Set as unstable — every emission causes full recomposition
 * of all children that receive them. Wrapping in @Immutable tells the compiler the
 * data is effectively immutable and safe to skip when the reference hasn't changed.
 */

@Immutable
data class ImmutableNowPlaying(
    val map: Map<String, EpgProgram> = emptyMap()
) {
    operator fun get(key: String): EpgProgram? = map[key]
    val isEmpty: Boolean get() = map.isEmpty()
}

@Immutable
data class ImmutableCategoryList(
    private val items: List<MediaCategory> = emptyList()
) : List<MediaCategory> by items

@Immutable
data class ImmutableMediaList(
    private val items: List<MediaItem> = emptyList()
) : List<MediaItem> by items

@Immutable
data class ImmutableStringSet(
    private val items: Set<String> = emptySet()
) : Set<String> by items

@Immutable
data class ImmutableWatchProgress(
    private val map: Map<String, Float> = emptyMap()
) : Map<String, Float> by map
