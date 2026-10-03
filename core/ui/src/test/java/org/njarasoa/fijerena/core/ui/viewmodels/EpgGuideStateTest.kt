package org.njarasoa.fijerena.core.ui.viewmodels

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

/** GD1: the guide's channel set and the reason it shows when nothing is listed. */
class EpgGuideStateTest {
    private fun channel(name: String) =
        MediaItem(
            id = name,
            name = name,
            mediaType = MediaType.LIVE_CHANNEL,
            categoryId = "cat1",
        )

    @Test
    fun guideChannelsDropsMarkerRowsAndKeepsEveryChannel() {
        val items = listOf(channel("##### 4K #####"), channel("TF1")) + (1..60).map { channel("ch$it") }

        val channels = guideChannels(items)

        // GD4: no 50-channel cap (G-1); listings are paged instead.
        assertEquals(61, channels.size)
        assertEquals("TF1", channels.first().name)
        assertEquals(emptyList<String>(), channels.filter { it.name.startsWith("#") }.map { it.name })
    }

    @Test
    fun recentResolvesThroughTheRepositoryNotTheSource() =
        runBlocking {
            val recent = listOf(channel("arte"))
            val repo = mockk<MediaRepository>()
            coEvery { repo.refreshRecentItems(ContentType.LIVE_TV) } returns recent

            assertEquals(
                recent,
                CategoryViewModel.virtualCategoryItems(repo, CategoryViewModel.RECENT_CATEGORY_ID, ContentType.LIVE_TV),
            )
        }

    @Test
    fun favouritesResolveThroughTheRepositoryNotTheSource() =
        runBlocking {
            val favourites = listOf(channel("bbc"))
            val repo = mockk<MediaRepository>()
            coEvery { repo.getFavoritesForContentTypeSuspend(ContentType.LIVE_TV) } returns favourites

            assertEquals(
                favourites,
                CategoryViewModel.virtualCategoryItems(repo, CategoryViewModel.FAVORITES_CATEGORY_ID, ContentType.LIVE_TV),
            )
        }

    @Test
    fun aRealCategoryIsNotVirtual() =
        runBlocking {
            assertNull(CategoryViewModel.virtualCategoryItems(mockk(), "42", ContentType.LIVE_TV))
        }

    @Test
    fun dataThatStopsBeforeTheDayIsStale() {
        assertEquals(EpgViewModel.NoListingsReason.STALE, noListingsReason(hasAnyListing = true, indexHasData = true))
        assertEquals(EpgViewModel.NoListingsReason.STALE, noListingsReason(hasAnyListing = true, indexHasData = false))
    }

    @Test
    fun anIndexWithNothingForTheseChannelsSaysSo() {
        assertEquals(EpgViewModel.NoListingsReason.INDEX_EMPTY, noListingsReason(hasAnyListing = false, indexHasData = true))
    }

    @Test
    fun noIndexAndNoNativeDataIsNone() {
        assertEquals(EpgViewModel.NoListingsReason.NONE, noListingsReason(hasAnyListing = false, indexHasData = false))
    }
}
