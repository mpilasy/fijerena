package org.njarasoa.fijerena.core.ui.history

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.FavoriteCategoryItem
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.RecentCategory
import org.njarasoa.fijerena.core.network.RecentRemoval
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

/**
 * Recent, Continue Watching, favourites and Recent Categories across the profile's sources
 * (docs/plans/20261012_multi-source-search-history-plan.md → Phase 4).
 */
class MergedListsTest {
    private fun row(
        id: String,
        source: Long,
        playedAt: Long,
        name: String = "n$id",
    ) = MediaItem(
        id = id,
        name = name,
        mediaType = MediaType.MOVIE,
        categoryId = "c",
        providerId = source,
        lastPlayedAt = playedAt,
    )

    private fun repo(source: Long): MediaRepository =
        mockk<MediaRepository>(relaxed = true).also { every { it.getProviderId() } returns source }

    private fun lists(vararg repos: MediaRepository) =
        MergedLists(
            repos.map {
                MergedLists.Source(it.getProviderId(), "S${it.getProviderId()}", it)
            },
        )

    @Test
    fun `recent is every source's own list, newest first`() {
        val a = repo(A)
        val b = repo(B)
        // Each source has already taken its own Recent size: A three rows, B two.
        coEvery { a.getRecentItemsSuspend(ContentType.MOVIES) } returns
            listOf(row("a1", A, 50), row("a2", A, 40), row("a3", A, 10))
        coEvery { b.getRecentItemsSuspend(ContentType.MOVIES) } returns
            listOf(row("b2", B, 45), row("b1", B, 30))

        val merged = runBlocking { lists(a, b).recent(ContentType.MOVIES) }

        assertEquals(listOf("a1", "b2", "a2", "b1", "a3"), merged.map { it.id })
        assertEquals(listOf(A, B, A, B, A), merged.map { it.providerId })
    }

    @Test
    fun `one source's recent is shown as it comes`() {
        val a = repo(A)
        val ownOrder = listOf(row("x", A, 1), row("y", A, 99))
        coEvery { a.getRecentItemsSuspend(ContentType.LIVE_TV) } returns ownOrder

        assertEquals(ownOrder, runBlocking { lists(a).recent(ContentType.LIVE_TV) })
    }

    @Test
    fun `the published recent waits for every source, then follows each of them`() {
        val a = repo(A)
        val b = repo(B)
        val aRecent = MutableStateFlow<List<MediaItem>?>(listOf(row("a1", A, 10)))
        val bRecent = MutableStateFlow<List<MediaItem>?>(null)
        every { a.recentItems(ContentType.LIVE_TV) } returns aRecent
        every { b.recentItems(ContentType.LIVE_TV) } returns bRecent
        val merged = lists(a, b)

        runBlocking {
            assertNull(merged.recentItems(ContentType.LIVE_TV).first())
            // A change on B (a play, a live sync) shows in the merged list.
            bRecent.value = listOf(row("b1", B, 20))
            assertEquals(listOf("b1", "a1"), merged.recentItems(ContentType.LIVE_TV).first()?.map { it.id })
        }
    }

    @Test
    fun `a removal and its undo go to the row's own source`() {
        val a = repo(A)
        val b = repo(B)
        val removal = RecentRemoval(ContentType.MOVIES, emptyList(), B)
        coEvery { b.removeFromRecent("x", ContentType.MOVIES, null) } returns removal
        val merged = lists(a, b)

        runBlocking {
            assertEquals(removal, merged.removeFromRecent(B, "x", ContentType.MOVIES))
            merged.restoreRecent(removal)
        }

        coVerify(exactly = 1) { b.removeFromRecent("x", ContentType.MOVIES, null) }
        coVerify(exactly = 1) { b.restoreRecent(removal) }
        coVerify(exactly = 0) { a.removeFromRecent(any(), any(), any()) }
        coVerify(exactly = 0) { a.restoreRecent(any()) }
    }

    @Test
    fun `a row with no source acts on the screen's own`() {
        val a = repo(A)
        val b = repo(B)

        runBlocking { lists(a, b).removeFromRecent(0L, "x", ContentType.LIVE_TV) }

        coVerify(exactly = 1) { a.removeFromRecent("x", ContentType.LIVE_TV, null) }
        coVerify(exactly = 0) { b.removeFromRecent(any(), any(), any()) }
    }

    @Test
    fun `clear recent clears every source of the profile`() {
        val a = repo(A)
        val b = repo(B)
        coEvery { a.clearRecent(ContentType.TV_SHOWS) } returns false
        coEvery { b.clearRecent(ContentType.TV_SHOWS) } returns true

        assertTrue(runBlocking { lists(a, b).clearRecent(ContentType.TV_SHOWS) })

        coVerify(exactly = 1) { a.clearRecent(ContentType.TV_SHOWS) }
        coVerify(exactly = 1) { b.clearRecent(ContentType.TV_SHOWS) }
    }

    @Test
    fun `a source whose read fails is left out, the others still show`() {
        val a = repo(A)
        val b = repo(B)
        coEvery { a.getFavoritesForContentTypeSuspend(ContentType.MOVIES) } returns listOf(row("a1", A, 0, name = "Zulu"))
        coEvery { b.getFavoritesForContentTypeSuspend(ContentType.MOVIES) } throws IllegalStateException("closed")

        assertEquals(listOf("a1"), runBlocking { lists(a, b).favorites(ContentType.MOVIES) }.map { it.id })
    }

    @Test
    fun `favourites are by name across sources, the same id on both kept twice`() {
        val a = repo(A)
        val b = repo(B)
        coEvery { a.getFavoritesForContentTypeSuspend(ContentType.LIVE_TV) } returns
            listOf(row("1", A, 0, name = "alpha"), row("2", A, 0, name = "Charlie"))
        coEvery { b.getFavoritesForContentTypeSuspend(ContentType.LIVE_TV) } returns
            listOf(row("1", B, 0, name = "Bravo"))

        val merged = runBlocking { lists(a, b).favorites(ContentType.LIVE_TV) }

        assertEquals(listOf("alpha", "Bravo", "Charlie"), merged.map { it.name })
        assertEquals(listOf(A, B, A), merged.map { it.providerId })
    }

    @Test
    fun `continue watching is newest first across sources, at most the limit`() {
        fun card(
            id: String,
            source: Long,
            playedAt: Long,
        ) = ContinueWatchingItem(
            id,
            id,
            null,
            ContentType.MOVIES,
            "c",
            null,
            0.5f,
            0L,
            BrowseTarget.Movie(id),
            providerId = source,
            lastPlayedAt = playedAt,
        )

        val merged =
            MergedLists.mergeContinueWatching(
                listOf(listOf(card("a1", A, 5), card("a2", A, 1)), listOf(card("b1", B, 9), card("b2", B, 3))),
                limit = 3,
            )

        assertEquals(listOf("b1", "a1", "b2"), merged.map { it.id })
    }

    @Test
    fun `favourite categories go by section, then by name, each with its source`() {
        val merged =
            MergedLists.mergeFavoriteCategories(
                listOf(ContentType.LIVE_TV, ContentType.MOVIES),
                listOf(
                    A to
                        listOf(
                            FavoriteCategoryItem("1", "horror", ContentType.MOVIES),
                            FavoriteCategoryItem("2", "Sport", ContentType.LIVE_TV),
                            FavoriteCategoryItem("3", "Drama", ContentType.TV_SHOWS),
                        ),
                    B to
                        listOf(
                            FavoriteCategoryItem("1", "Action", ContentType.MOVIES),
                            FavoriteCategoryItem("4", "News", ContentType.LIVE_TV),
                        ),
                ),
            )

        assertEquals(listOf("News", "Sport", "Action", "horror"), merged.map { it.category.categoryName })
        assertEquals(listOf(B, A, B, A), merged.map { it.providerId })
    }

    @Test
    fun `recent categories are newest first, each with its source`() {
        val merged =
            MergedLists.mergeRecentCategories(
                listOf(
                    A to
                        listOf(
                            RecentCategory("10", "News", ContentType.LIVE_TV, 100),
                            RecentCategory("11", "Kids", ContentType.LIVE_TV, 20),
                        ),
                    B to listOf(RecentCategory("10", "Sport", ContentType.LIVE_TV, 50)),
                ),
            )

        assertEquals(listOf("News", "Sport", "Kids"), merged.map { it.category.categoryName })
        assertEquals(listOf(A, B, A), merged.map { it.providerId })
    }

    @Test
    fun `the last channel watched is the newest of the sources' own, on its source`() {
        // Phase 5: each source remembers its own last channel; the one played last wins.
        val a = repo(A)
        val b = repo(B)
        every { a.getLastItemId(ContentType.LIVE_TV) } returns "7"
        every { b.getLastItemId(ContentType.LIVE_TV) } returns "7"
        coEvery { a.getRecentItemsSuspend(ContentType.LIVE_TV) } returns listOf(row("7", A, 10), row("9", A, 5))
        coEvery { b.getRecentItemsSuspend(ContentType.LIVE_TV) } returns listOf(row("7", B, 40))

        val last = runBlocking { lists(a, b).lastWatchedChannel() }

        assertEquals("7", last?.id)
        assertEquals(B, last?.providerId)
    }

    @Test
    fun `a last channel that left its Recent or is a separator doesn't count`() {
        val separator = row("s", B, 99, name = "#### NEWS ####")

        val last =
            MergedLists.newestLastWatched(
                listOf("gone" to listOf(row("1", A, 50)), "s" to listOf(separator), "2" to listOf(row("2", C, 20))),
            )

        assertEquals(C, last?.providerId)
        assertNull(MergedLists.newestLastWatched(listOf(null to listOf(row("1", A, 50)))))
    }

    @Test
    fun `source names are given only when the lists mix sources`() {
        assertEquals(mapOf(A to "S$A", B to "S$B"), lists(repo(A), repo(B)).sourceNames)
        assertTrue(lists(repo(A)).sourceNames.isEmpty())
        assertFalse(lists(repo(A)).sourceNames.containsKey(A))
    }

    @Test
    fun `other copies of a title come from every source, each with its source, only the item itself left out`() {
        val a = repo(A)
        val b = repo(B)
        // The screen's own source leaves the item out; another source keeps its copy under the same id.
        coEvery { a.getAlternateStreams("7", TMDB, ContentType.MOVIES) } returns listOf(row("9", 0L, 0))
        coEvery { b.getAlternateStreams("", TMDB, ContentType.MOVIES) } returns listOf(row("7", 0L, 0), row("8", 0L, 0))

        val copies = runBlocking { lists(a, b).alternateStreams("7", TMDB, ContentType.MOVIES) }

        assertEquals(listOf(A to "9", B to "7", B to "8"), copies.map { it.providerId to it.id })
        coVerify(exactly = 0) { a.getAlternateStreams("", any(), any()) }
        coVerify(exactly = 0) { b.getAlternateStreams("7", any(), any()) }
    }

    @Test
    fun `a source whose other copies can't be read is left out`() {
        val a = repo(A)
        val b = repo(B)
        coEvery { a.getAlternateStreams(any(), any(), any()) } throws IllegalStateException("offline")
        coEvery { b.getAlternateStreams(any(), any(), any()) } returns listOf(row("4", 0L, 0))

        val copies = runBlocking { lists(a, b).alternateStreams("7", TMDB, ContentType.TV_SHOWS) }

        assertEquals(listOf(B to "4"), copies.map { it.providerId to it.id })
    }

    private companion object {
        const val TMDB = "603"
        const val A = 3L
        const val B = 8L
        const val C = 11L
    }
}
