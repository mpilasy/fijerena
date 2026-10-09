package org.njarasoa.fijerena.core.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

class MergeLiveRowTest {
    private fun channel(id: String) = MediaItem(id = id, name = "ch$id", mediaType = MediaType.LIVE_CHANNEL, categoryId = "c")

    private val separator = channel("sep").copy(name = "####### ETHOPIA VIP #######")

    @Test
    fun lastWatchedFirstThenRecentInItsOwnOrder() {
        val rows = mergeLiveRow(lastItemId = "r2", recent = listOf(channel("r1"), channel("r2"), channel("r3")))

        assertEquals(listOf("r2", "r1", "r3"), rows.map { it.item.id })
        assertEquals(listOf(true, false, false), rows.map { it.lastWatched })
        assertEquals(listOf(false, false, false), rows.map { it.fromFavorites })
    }

    @Test
    fun withoutALastWatchedTheRowIsRecentAsIs() {
        val rows = mergeLiveRow(lastItemId = null, recent = listOf(channel("a"), channel("b")))

        assertEquals(listOf("a", "b"), rows.map { it.item.id })
        assertEquals(listOf(false, false), rows.map { it.lastWatched })
    }

    @Test
    fun aChannelListedTwiceShowsOnce() {
        val rows = mergeLiveRow(lastItemId = "a", recent = listOf(channel("b"), channel("a"), channel("b")))

        assertEquals(listOf("a", "b"), rows.map { it.item.id })
    }

    @Test
    fun lastWatchedNoLongerInRecentIsSkippedAndTheRowIsCapped() {
        val rows = mergeLiveRow(lastItemId = "gone", recent = (1..30).map { channel("r$it") }, max = 20)

        assertEquals(20, rows.size)
        assertEquals("r1", rows.first().item.id)
        assertEquals(false, rows.first().lastWatched)
    }

    @Test
    fun separatorRowsNeverShow() {
        val rows = mergeLiveRow(lastItemId = "sep", recent = listOf(separator, channel("r1")))

        assertEquals(listOf("r1"), rows.map { it.item.id })
        assertEquals(false, rows.first().lastWatched)
    }

    @Test
    fun favouriteChannelsKeepTheirOwnOrderAndZapThroughFavorites() {
        val rows = favoriteChannelsRow(listOf(channel("f2"), channel("f1"), channel("f3")))

        assertEquals(listOf("f2", "f1", "f3"), rows.map { it.item.id })
        assertEquals(listOf(true, true, true), rows.map { it.fromFavorites })
        assertEquals(listOf(false, false, false), rows.map { it.lastWatched })
    }

    @Test
    fun favouriteChannelsLeaveSeparatorRowsOut() {
        val rows = favoriteChannelsRow(listOf(separator, channel("f1")))

        assertEquals(listOf("f1"), rows.map { it.item.id })
    }

    @Test
    fun aRecentFavouriteIsInBothRowsEachZappingThroughItsOwnList() {
        val both = channel("b")
        val channels = mergeLiveRow(lastItemId = "b", recent = listOf(both, channel("a")))
        val favorites = favoriteChannelsRow(listOf(both))

        assertEquals(listOf("b", "a"), channels.map { it.item.id })
        assertEquals(false, channels.first().fromFavorites)
        assertEquals(listOf("b"), favorites.map { it.item.id })
        assertEquals(true, favorites.first().fromFavorites)
    }
}
