package org.njarasoa.fijerena.core.ui.newepisodes

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.NewEpisodeFacts
import org.njarasoa.fijerena.core.network.xtream.db.NewEpisodeEntity
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.ui.history.MergedLists

/** [NewEpisodes.forProfile]: the shows that count for the profile, from each source's facts. */
class NewEpisodesTest {
    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun show(
        id: Int,
        source: Long,
        since: Long? = now - 2 * day,
        newSeasons: Set<Int?> = emptySet(),
    ) = NewEpisodeFacts.Show(
        MediaItem(id = id.toString(), name = "Show $id", mediaType = MediaType.SERIES, categoryId = "c", providerId = source),
        since,
        newSeasons,
    )

    private fun row(
        source: Long,
        series: Int,
        episode: String,
        foundAt: Long,
        season: Int? = 1,
        number: Int = 1,
    ) = NewEpisodeEntity(source, series, episode, season, number, foundAt)

    private fun repo(
        source: Long,
        facts: NewEpisodeFacts?,
    ): MediaRepository =
        mockk<MediaRepository>(relaxed = true).also {
            every { it.getProviderId() } returns source
            coEvery { it.getNewEpisodeFacts(any()) } returns facts
        }

    private fun lists(vararg repos: MediaRepository) =
        MergedLists(repos.map { MergedLists.Source(it.getProviderId(), "Source ${it.getProviderId()}", it) })

    @Test
    fun `watching an episode clears it, the show goes with its last new episode`() =
        runBlocking {
            val facts =
                NewEpisodeFacts(
                    listOf(row(1, 7, "e1", now - day), row(1, 7, "e2", now - day, number = 2)),
                    listOf(show(7, 1)),
                    seenEpisodeIds = setOf("e1"),
                )

            val shows = NewEpisodes.forProfile(lists(repo(1, facts)), now)

            assertEquals(listOf("e2"), shows.single().episodes.map { it.episodeId })
            assertEquals(
                emptyList<NewEpisodeShow>(),
                NewEpisodes.forProfile(lists(repo(1, facts.copy(seenEpisodeIds = setOf("e1", "e2")))), now),
            )
        }

    @Test
    fun `a find older than 30 days does not count`() =
        runBlocking {
            val facts = NewEpisodeFacts(listOf(row(1, 7, "old", now - 31 * day)), listOf(show(7, 1)), emptySet())

            assertTrue(NewEpisodes.forProfile(lists(repo(1, facts)), now).isEmpty())
        }

    @Test
    fun `only a find after the profile followed the show counts`() =
        runBlocking {
            val facts =
                NewEpisodeFacts(
                    listOf(row(1, 7, "before", now - 3 * day), row(1, 8, "after", now - day)),
                    listOf(show(7, 1, since = now - 2 * day), show(8, 1, since = now - 2 * day)),
                    emptySet(),
                )

            assertEquals(listOf("8"), NewEpisodes.forProfile(lists(repo(1, facts)), now).map { it.item.id })
        }

    @Test
    fun `a show the profile does not follow gives no card`() =
        runBlocking {
            val facts = NewEpisodeFacts(listOf(row(1, 7, "e", now - day)), listOf(show(7, 1, since = null)), emptySet())

            assertTrue(NewEpisodes.forProfile(lists(repo(1, facts)), now).isEmpty())
        }

    @Test
    fun `a show hidden by a filter is not in the facts and gives no card`() =
        runBlocking {
            val facts = NewEpisodeFacts(listOf(row(1, 7, "e", now - day)), shows = emptyList(), seenEpisodeIds = emptySet())

            assertTrue(NewEpisodes.forProfile(lists(repo(1, facts)), now).isEmpty())
        }

    @Test
    fun `two sources merge, newest find first, each on its own source`() =
        runBlocking {
            val first = NewEpisodeFacts(listOf(row(1, 7, "a", now - 2 * day)), listOf(show(7, 1)), emptySet())
            val second = NewEpisodeFacts(listOf(row(2, 9, "b", now - day)), listOf(show(9, 2)), emptySet())

            val shows = NewEpisodes.forProfile(lists(repo(1, first), repo(2, second)), now)

            assertEquals(listOf("2:9", "1:7"), shows.map { "${it.item.providerId}:${it.item.id}" })
        }

    @Test
    fun `a source with nothing to say is left out`() =
        runBlocking {
            val shows = NewEpisodes.forProfile(lists(repo(1, null)), now)

            assertTrue(shows.isEmpty())
        }

    @Test
    fun `the card knows its first episode and whether a season is new`() =
        runBlocking {
            val facts =
                NewEpisodeFacts(
                    listOf(row(1, 7, "s2e2", now - day, season = 2, number = 2), row(1, 7, "s2e1", now - day, season = 2, number = 1)),
                    listOf(show(7, 1, newSeasons = setOf(2))),
                    emptySet(),
                )

            val show = NewEpisodes.forProfile(lists(repo(1, facts)), now).single()

            assertEquals("s2e1", show.episodes.first().episodeId)
            assertEquals(2, show.season)
            assertTrue(show.newSeason)
            assertFalse(NewEpisodes.forProfile(lists(repo(1, facts.copy(shows = listOf(show(7, 1))))), now).single().newSeason)
        }
}
