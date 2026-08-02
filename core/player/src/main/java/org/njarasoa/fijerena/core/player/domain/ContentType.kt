package org.njarasoa.fijerena.core.player.domain

import java.util.Locale

object ContentType {
    const val LIVE_TV = "LIVE_TV"
    const val MOVIES = "MOVIES"
    const val TV_SHOWS = "TV_SHOWS"
}

/** Human-readable label for a [ContentType] value, e.g. "Live TV", "Movies". */
fun String.asContentTypeLabel(): String =
    when (this) {
        ContentType.LIVE_TV -> "Live TV"
        ContentType.MOVIES -> "Movies"
        ContentType.TV_SHOWS -> "TV Shows"
        else -> lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
