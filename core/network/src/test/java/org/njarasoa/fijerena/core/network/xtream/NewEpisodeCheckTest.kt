package org.njarasoa.fijerena.core.network.xtream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.xtream.db.NewEpisodeCheckState
import org.njarasoa.fijerena.core.player.api.XtreamResponse

class NewEpisodeCheckTest {
    private val now = 100L * 24 * 60 * 60 * 1000

    private fun changed(
        id: Int,
        lastModified: String? = id.toString(),
    ) = NewEpisodeCheckState(id, lastModified, episodesFetchedAt = null, hasEpisodes = true)

    private fun checked(
        id: Int,
        at: Long,
    ) = NewEpisodeCheckState(id, null, episodesFetchedAt = at, hasEpisodes = true)

    private fun neverFetched(id: Int) = NewEpisodeCheckState(id, null, episodesFetchedAt = null, hasEpisodes = false)

    @Test
    fun `changed shows go first, oldest change first`() {
        val calls = NewEpisodeCheck.plan(listOf(changed(3, "300"), changed(1, "100"), changed(2, "200"), changed(4, null)), now)

        assertEquals(listOf(4, 1, 2, 3), calls)
    }

    @Test
    fun `at most the cap, the rest wait and nothing is left for the fallback`() {
        val states = (1..25).map { changed(it) } + checked(99, now - NewEpisodeCheck.FALLBACK_AFTER_MS - 1)

        val calls = NewEpisodeCheck.plan(states, now)

        assertEquals(NewEpisodeCheck.MAX_CALLS, calls.size)
        assertEquals((1..NewEpisodeCheck.MAX_CALLS).toList(), calls)
        assertTrue(99 !in calls)
    }

    @Test
    fun `the fallback round checks one show not checked for 7 days, the oldest`() {
        val calls =
            NewEpisodeCheck.plan(
                listOf(
                    checked(1, now - 8 * DAY),
                    checked(2, now - 10 * DAY),
                    checked(3, now - DAY),
                ),
                now,
            )

        assertEquals(listOf(2), calls)
    }

    @Test
    fun `a show checked less than 7 days ago gets no call`() {
        assertTrue(NewEpisodeCheck.plan(listOf(checked(1, now - 6 * DAY)), now).isEmpty())
    }

    @Test
    fun `a show never fetched gets the fallback call, not a changed slot`() {
        val calls = NewEpisodeCheck.plan(listOf(neverFetched(5), changed(1)), now)

        assertEquals(listOf(1, 5), calls)
    }

    @Test
    fun `calls run one at a time, stopping at the first failed one`() =
        runTest {
            val called = mutableListOf<Int>()
            val stamped = mutableListOf<Int>()

            NewEpisodeCheck.run(
                listOf(1, 2, 3, 4),
                { id ->
                    called += id
                    if (id == 3) XtreamResponse.Failed(RuntimeException("timeout")) else XtreamResponse.Ok(Unit)
                },
                { stamped += it },
            )

            assertEquals(listOf(1, 2, 3), called)
            assertEquals(listOf(1, 2), stamped)
        }

    @Test
    fun `a show the source has nothing for is not stamped and does not stop the check`() =
        runTest {
            val called = mutableListOf<Int>()
            val stamped = mutableListOf<Int>()

            NewEpisodeCheck.run(
                listOf(1, 2),
                { id ->
                    called += id
                    if (id == 1) XtreamResponse.Unavailable(1, "get_series_info") else XtreamResponse.Ok(Unit)
                },
                { stamped += it },
            )

            assertEquals(listOf(1, 2), called)
            assertEquals(listOf(2), stamped)
        }

    @Test
    fun `a failed check never fails the sync`() =
        runTest {
            NewEpisodeCheck.runSafely("panel") { throw IllegalStateException("bad row") }
        }

    @Test
    fun `cancellation is not swallowed`() {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                NewEpisodeCheck.runSafely("panel") { throw CancellationException("worker stopped") }
            }
        }
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
