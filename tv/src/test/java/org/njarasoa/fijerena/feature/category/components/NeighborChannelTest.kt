package org.njarasoa.fijerena.feature.category.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

/**
 * Up/Down in full screen zaps only through the playing channel's own source, in the list's order
 * (docs/plans/20261012_multi-source-search-history-plan.md → Q4, Phase 5).
 */
class NeighborChannelTest {
    private fun channel(
        id: String,
        source: Long = 0L,
        name: String = "ch$id",
    ) = MediaItem(id = id, name = name, mediaType = MediaType.LIVE_CHANNEL, categoryId = "c", providerId = source)

    // Recent across two sources, newest first.
    private val recent = listOf(channel("1", A), channel("2", B), channel("3", A), channel("4", B), channel("5", A))

    @Test
    fun `zapping skips the other source's channels`() {
        assertEquals("3", neighborChannel(recent, recent[0], +1)?.id)
        assertEquals("4", neighborChannel(recent, recent[1], +1)?.id)
        assertEquals("1", neighborChannel(recent, recent[2], -1)?.id)
    }

    @Test
    fun `zapping wraps within the source`() {
        assertEquals(channel("1", A), neighborChannel(recent, recent[4], +1))
        assertEquals(channel("4", B), neighborChannel(recent, recent[1], -1))
    }

    @Test
    fun `the same id on another source is not the playing channel`() {
        val list = listOf(channel("7", B), channel("7", A), channel("8", A))

        assertEquals(channel("8", A), neighborChannel(list, channel("7", A), +1))
        assertEquals(channel("7", B), neighborChannel(list, channel("7", B), +1))
    }

    @Test
    fun `a catalogue list's rows are on the list's own source`() {
        val category = listOf(channel("1"), channel("2"), channel("3"))

        assertEquals(channel("2"), neighborChannel(category, channel("1"), +1, listSource = A))
        // The playing channel picked from Recent, which names its source: the same list.
        assertEquals(channel("3"), neighborChannel(category, channel("2", A), +1, listSource = A))
    }

    @Test
    fun `separator rows are never zapped to`() {
        val list = listOf(channel("1", A), channel("s", A, name = "#### NEWS ####"), channel("2", A))

        assertEquals(channel("2", A), neighborChannel(list, list[0], +1))
    }

    @Test
    fun `nothing of the playing channel's source left gives no neighbour`() {
        assertNull(neighborChannel(listOf(channel("2", B)), channel("1", A), +1))
    }

    private companion object {
        const val A = 3L
        const val B = 8L
    }
}
