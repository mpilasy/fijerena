package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

class RecentChannelsTest {
    private fun channel(id: String) =
        MediaItem(
            id = id,
            name = id,
            mediaType = MediaType.LIVE_CHANNEL,
            categoryId = "cat1",
        )

    private val recent = listOf(channel("bbc"), channel("cnn"))

    @Test
    fun includePrependsAChannelThatIsNotInTheListYet() {
        assertEquals(
            listOf("arte", "bbc", "cnn"),
            recent.withCurrentChannel(channel("arte"), CurrentChannelPolicy.INCLUDE).map { it.id },
        )
    }

    @Test
    fun includeLeavesTheListAloneWhenTheChannelIsAlreadyThere() {
        assertEquals(
            listOf("bbc", "cnn"),
            recent.withCurrentChannel(channel("cnn"), CurrentChannelPolicy.INCLUDE).map { it.id },
        )
    }

    @Test
    fun excludeFiltersTheCurrentChannelOut() {
        assertEquals(
            listOf("cnn"),
            recent.withCurrentChannel(channel("bbc"), CurrentChannelPolicy.EXCLUDE).map { it.id },
        )
    }

    @Test
    fun displayOrderKeepsVisibleRowsWhereTheyAre() {
        val displayed = listOf(channel("bbc"), channel("cnn"), channel("arte"))
        // cnn was just watched, so the store now lists it first.
        val republished = listOf(channel("cnn"), channel("bbc"), channel("arte"))

        assertEquals(
            listOf("bbc", "cnn", "arte"),
            republished.inDisplayOrderOf(displayed).map { it.id },
        )
    }

    @Test
    fun displayOrderPutsGenuinelyNewEntriesFirst() {
        val displayed = listOf(channel("bbc"), channel("cnn"))
        val republished = listOf(channel("arte"), channel("cnn"), channel("bbc"))

        assertEquals(
            listOf("arte", "bbc", "cnn"),
            republished.inDisplayOrderOf(displayed).map { it.id },
        )
    }

    @Test
    fun displayOrderDropsEntriesThatAreGone() {
        val displayed = listOf(channel("bbc"), channel("cnn"))

        assertEquals(
            listOf("bbc"),
            listOf(channel("bbc")).inDisplayOrderOf(displayed).map { it.id },
        )
    }

    @Test
    fun displayOrderOfNothingDisplayedAdoptsTheIncomingOrder() {
        assertEquals(recent, recent.inDisplayOrderOf(emptyList()))
    }

    @Test
    fun noCurrentChannelLeavesTheListAlone() {
        assertEquals(recent, recent.withCurrentChannel(null, CurrentChannelPolicy.INCLUDE))
        assertEquals(recent, recent.withCurrentChannel(null, CurrentChannelPolicy.EXCLUDE))
    }
}
