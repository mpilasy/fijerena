package org.njarasoa.fijerena.ui.player

import androidx.compose.runtime.Immutable
import org.njarasoa.fijerena.core.player.domain.MediaItem

@Immutable
data class ImmutableMediaList(
    private val items: List<MediaItem> = emptyList()
) : List<MediaItem> by items
