package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.tmdb.TmdbChart
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.feature.category.components.rowAfterChange

/**
 * Home's rows, top to bottom (user, 2026-10-29): Continue Watching alone, then a group per section,
 * Channels, TV Shows, Movies, each with its favourites first.
 */
internal enum class HomeShelf {
    CONTINUE_WATCHING,

    /** Recent channels, alone under Continue Watching (user, 2026-10-30). */
    CHANNELS,

    FAVORITE_CHANNELS,

    FAVORITE_SHOWS,

    /** The Wishlist's shows (wanted titles plan, Phase 5). */
    WANTED_SHOWS,

    /** The TMDB chart rows (TMDB charts plan, Phase 3). */
    CHART_TRENDING_SHOWS,
    CHART_POPULAR_SHOWS,
    CHART_TOP_RATED_SHOWS,

    FAVORITE_MOVIES,
    WANTED_MOVIES,
    CHART_TRENDING_MOVIES,
    CHART_POPULAR_MOVIES,
    CHART_TOP_RATED_MOVIES,
}

/** The chart of a chart row; null for the other rows. */
internal val HomeShelf.chart: TmdbChart?
    get() =
        when (this) {
            HomeShelf.CHART_TRENDING_MOVIES -> TmdbChart.TRENDING_MOVIES
            HomeShelf.CHART_POPULAR_MOVIES -> TmdbChart.POPULAR_MOVIES
            HomeShelf.CHART_TOP_RATED_MOVIES -> TmdbChart.TOP_RATED_MOVIES
            HomeShelf.CHART_TRENDING_SHOWS -> TmdbChart.TRENDING_SHOWS
            HomeShelf.CHART_POPULAR_SHOWS -> TmdbChart.POPULAR_SHOWS
            HomeShelf.CHART_TOP_RATED_SHOWS -> TmdbChart.TOP_RATED_SHOWS
            else -> null
        }

/** The section of a Wishlist row; null for the other rows. */
internal val HomeShelf.wantedContentType: String?
    get() =
        when (this) {
            HomeShelf.WANTED_SHOWS -> ContentType.TV_SHOWS
            HomeShelf.WANTED_MOVIES -> ContentType.MOVIES
            else -> null
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
 * (the tv recents favorites plan → A, "Home's cards"): [key] itself when it is
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

/**
 * Shows the item at [index] of a Home row when Back returns to it. A card already on screen stays
 * where it is: scrolled to the row's start, the cards before it (and the row's first-card
 * requester) left the screen, and focus went with them. A card off screen comes in at the row's
 * end, so the cards before it stay in view.
 */
internal suspend fun LazyListState.showReturnCard(index: Int) {
    // The row lays out its cards a frame after they arrive: until then nothing reads as on screen.
    withTimeoutOrNull(ROW_LAYOUT_WAIT_MS) { snapshotFlow { layoutInfo.totalItemsCount > index }.first { it } }
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        scrollToItem(index)
        layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let { item ->
            val back = backToRowEnd(layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding, item.offset, item.size)
            if (back > 0) scrollBy(-back.toFloat())
        }
    }
}

/**
 * How far to scroll a row back so that a card at [itemOffset], [itemSize] long, ends at [rowEnd]:
 * zero when it already ends there or is longer than the row.
 */
internal fun backToRowEnd(
    rowEnd: Int,
    itemOffset: Int,
    itemSize: Int,
): Int = (rowEnd - itemOffset - itemSize).coerceAtLeast(0)

private const val ROW_LAYOUT_WAIT_MS = 1_000L
