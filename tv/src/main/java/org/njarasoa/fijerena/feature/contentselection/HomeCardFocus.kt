package org.njarasoa.fijerena.feature.contentselection

import org.njarasoa.fijerena.feature.category.components.rowAfterChange

/** Home's rows, top to bottom. */
internal enum class HomeShelf {
    CONTINUE_WATCHING,
    CHANNELS,
    FAVORITE_CHANNELS,
    FAVORITE_MOVIES,
    FAVORITE_SHOWS,
}

/** A favourites row: its menu opens on Remove from Favorites; the others are Recent rows. */
internal val HomeShelf.isFavorites: Boolean
    get() = this == HomeShelf.FAVORITE_CHANNELS || this == HomeShelf.FAVORITE_MOVIES || this == HomeShelf.FAVORITE_SHOWS

/** One card on Home: its row and its key in that row. */
internal data class HomeCard(
    val shelf: HomeShelf,
    val key: String,
)

/**
 * The card that takes focus once [key] left [shelf] (or came back to it with an Undo), the shelf's
 * keys being [oldKeys] at the time and every shelf's keys being [rows] now
 * (docs/plans/archive/20261009_tv-recents-favorites-plan.md → A, "Home's cards"): [key] itself when it is
 * there, else the card that took its place in the same row ([rowAfterChange]); when the row is
 * empty (and gone from Home), the first card of the next row that has one, else of the nearest row
 * above; null when Home has no card left (the navigation rail takes focus).
 */
internal fun cardAfterChange(
    shelf: HomeShelf,
    oldKeys: List<String>,
    key: String,
    rows: Map<HomeShelf, List<String>>,
): HomeCard? {
    val below = HomeShelf.entries.filter { it > shelf }
    val above = HomeShelf.entries.filter { it < shelf }.asReversed()
    return rowAfterChange(oldKeys, key, rows[shelf].orEmpty())?.let { HomeCard(shelf, it) }
        ?: (below + above).firstNotNullOfOrNull { other -> rows[other]?.firstOrNull()?.let { HomeCard(other, it) } }
}

/**
 * This row with the card [key] of [before] (the row as it was when the card left) back where it
 * was — the Undo of a removal. Unchanged when the card is already there.
 */
internal fun <T> List<T>.withCardPutBack(
    before: List<T>,
    key: String,
    keyOf: (T) -> String,
): List<T> {
    val index = before.indexOfFirst { keyOf(it) == key }
    val card = before.getOrNull(index)
    return if (card == null || any { keyOf(it) == key }) this else toMutableList().apply { add(index.coerceAtMost(size), card) }
}
