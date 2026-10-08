package org.njarasoa.fijerena.core.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

class MergeLiveRowTest {
    private fun channel(id: String) = MediaItem(id = id, name = "ch$id", mediaType = MediaType.LIVE_CHANNEL, categoryId = "c")

    @Test
    fun lastWatchedFirstThenFavouritesThenRecent() {
        val rows = mergeLiveRow(lastItemId = "r2", recent = listOf(channel("r1"), channel("r2")), favorites = listOf(channel("f1")))

        assertEquals(listOf("r2", "f1", "r1"), rows.map { it.item.id })
        assertEquals(listOf(true, false, false), rows.map { it.lastWatched })
        assertEquals(listOf(false, true, false), rows.map { it.fromFavorites })
    }

    @Test
    fun aChannelBothFavouriteAndRecentShowsOnceAsAFavourite() {
        val rows = mergeLiveRow(lastItemId = null, recent = listOf(channel("a"), channel("b")), favorites = listOf(channel("b")))

        assertEquals(listOf("b", "a"), rows.map { it.item.id })
        assertEquals(true, rows.first().fromFavorites)
    }

    @Test
    fun lastWatchedFoundOnlyAmongFavouritesStillComesFirstAsRecent() {
        val rows = mergeLiveRow(lastItemId = "f2", recent = emptyList(), favorites = listOf(channel("f1"), channel("f2")))

        assertEquals(listOf("f2", "f1"), rows.map { it.item.id })
        assertEquals(listOf(false, true), rows.map { it.fromFavorites })
    }

    @Test
    fun lastWatchedNoLongerAvailableIsSkippedAndTheRowIsCapped() {
        val rows = mergeLiveRow(lastItemId = "gone", recent = (1..30).map { channel("r$it") }, favorites = emptyList(), max = 20)

        assertEquals(20, rows.size)
        assertEquals("r1", rows.first().item.id)
        assertEquals(false, rows.first().lastWatched)
    }

    @Test
    fun separatorRowsNeverShow() {
        val separator = channel("sep").copy(name = "####### ETHOPIA VIP #######")
        val rows = mergeLiveRow(lastItemId = "sep", recent = listOf(separator, channel("r1")), favorites = emptyList())

        assertEquals(listOf("r1"), rows.map { it.item.id })
        assertEquals(false, rows.first().lastWatched)
    }
}
