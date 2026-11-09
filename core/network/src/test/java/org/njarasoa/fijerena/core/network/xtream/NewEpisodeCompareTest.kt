package org.njarasoa.fijerena.core.network.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.xtream.db.NewEpisodeEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity

class NewEpisodeCompareTest {
    private val now = 5_000L

    private fun episode(
        id: String,
        season: Int?,
        number: Int,
    ) = XtreamEpisodeEntity(
        id = id,
        seriesId = 7,
        providerId = 1L,
        season = season,
        episodeNum = number,
        title = id,
        containerExtension = "mp4",
    )

    private fun compare(
        stored: List<XtreamEpisodeEntity>,
        incoming: List<XtreamEpisodeEntity>,
        pending: List<NewEpisodeEntity> = emptyList(),
    ) = NewEpisodeCompare.compare(1L, 7, stored, incoming, pending, now)

    @Test
    fun `a new id with a new pair is new`() {
        val change = compare(listOf(episode("a", 1, 1)), listOf(episode("a", 1, 1), episode("b", 1, 2)))

        assertEquals(listOf(NewEpisodeEntity(1L, 7, "b", 1, 2, now)), change.added)
    }

    @Test
    fun `a new season is new`() {
        val change =
            compare(
                listOf(episode("a", 1, 1)),
                listOf(episode("a", 1, 1), episode("c", 2, 1), episode("d", 2, 2)),
            )

        assertEquals(listOf("c", "d"), change.added.map { it.episodeId })
        assertTrue(change.added.all { it.season == 2 })
    }

    @Test
    fun `a show with no stored list gets its first list as the baseline`() {
        val change = compare(emptyList(), listOf(episode("a", 1, 1), episode("b", 1, 2)))

        assertTrue(change.added.isEmpty())
    }

    @Test
    fun `an episode that left the list is no alert and its new row goes`() {
        val pending = listOf(NewEpisodeEntity(1L, 7, "gone", 1, 3, 1L))

        val change = compare(listOf(episode("a", 1, 1), episode("gone", 1, 3)), listOf(episode("a", 1, 1)), pending)

        assertTrue(change.added.isEmpty())
        assertEquals(listOf("gone"), change.removedIds)
    }

    @Test
    fun `an old pair with a new id is no alert`() {
        val change = compare(listOf(episode("a", 1, 1)), listOf(episode("reuploaded", 1, 1)))

        assertTrue(change.added.isEmpty())
    }

    @Test
    fun `an old id with a new pair is no alert`() {
        val change = compare(listOf(episode("a", 1, 1)), listOf(episode("a", 1, 2)))

        assertTrue(change.added.isEmpty())
    }

    @Test
    fun `an episode listed twice counts once`() {
        val change = compare(listOf(episode("a", 1, 1)), listOf(episode("b", 1, 2), episode("c", 1, 2)))

        assertEquals(listOf("b"), change.added.map { it.episodeId })
    }

    @Test
    fun `a row stays while its episode is in the list`() {
        val pending = listOf(NewEpisodeEntity(1L, 7, "b", 1, 2, 1L))

        val change = compare(listOf(episode("a", 1, 1), episode("b", 1, 2)), listOf(episode("a", 1, 1), episode("b", 1, 2)), pending)

        assertTrue(change.removedIds.isEmpty())
    }
}
