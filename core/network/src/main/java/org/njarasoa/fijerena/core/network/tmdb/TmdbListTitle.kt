package org.njarasoa.fijerena.core.network.tmdb

/**
 * The name a TMDB-based list (More Like This, the charts, an actor page's rows, the Wishlist)
 * shows for a title: its [original] name when it is in Latin letters ("Le Fabuleux Destin
 * d'Amélie Poulain", "La casa de papel"), else the [english] name (a Korean, Japanese or Russian
 * title). Digits, punctuation and spaces do not count. The original name when there is no other,
 * null when both are blank.
 */
fun tmdbListTitle(
    original: String?,
    english: String?,
): String? {
    val kept = original?.trim()?.takeIf { it.isNotEmpty() }
    if (kept != null && isLatin(kept)) return kept
    return english?.trim()?.takeIf { it.isNotEmpty() } ?: kept
}

/** [tmdbListTitle] of this TMDB title: the list calls ask TMDB in English. */
val TmdbRecommendation.listTitle: String?
    get() = tmdbListTitle(originalTitle ?: originalName, displayTitle)

private fun isLatin(text: String): Boolean =
    text.codePoints().allMatch { !Character.isLetter(it) || Character.UnicodeScript.of(it) == Character.UnicodeScript.LATIN }
