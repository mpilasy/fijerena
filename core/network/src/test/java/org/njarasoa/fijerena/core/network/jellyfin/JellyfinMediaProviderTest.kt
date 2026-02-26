package org.njarasoa.fijerena.core.network.jellyfin

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinMediaProviderTest {

    private val api = mockk<JellyfinApiService>(relaxed = true)
    private val provider = JellyfinMediaProvider(
        providerId = 1L,
        serverUrl = "http://localhost",
        username = "user",
        password = "pass",
        deviceId = "device",
        injectedApi = api
    )

    @Test
    fun `getSeriesDetail fetches all episodes in one call (optimized behavior)`() = runTest {
        // Setup mocks
        val seriesId = "series1"
        val season1Id = "season1"
        val season2Id = "season2"

        val seriesItem = JellyfinItem(id = seriesId, name = "Test Series", type = "Series")
        val season1Item = JellyfinItem(id = season1Id, name = "Season 1", type = "Season", indexNumber = 1)
        val season2Item = JellyfinItem(id = season2Id, name = "Season 2", type = "Season", indexNumber = 2)

        val ep1 = JellyfinItem(id = "ep1", name = "Ep 1", type = "Episode", indexNumber = 1, parentIndexNumber = 1, parentId = season1Id)
        val ep2 = JellyfinItem(id = "ep2", name = "Ep 2", type = "Episode", indexNumber = 1, parentIndexNumber = 2, parentId = season2Id)

        coEvery { api.getItemById(seriesId) } returns Result.success(seriesItem)
        coEvery { api.getSeasons(seriesId) } returns Result.success(listOf(season1Item, season2Item))

        // Mock getItems for episodes
        // api.getItems(parentId = seriesId, includeItemTypes = "Episode", ...)
        coEvery {
            api.getItems(
                parentId = seriesId,
                includeItemTypes = "Episode",
                sortBy = any(),
                sortOrder = any()
            )
        } returns Result.success(listOf(ep1, ep2))

        // Assume connected
        every { api.isAuthenticated() } returns true

        // Execute
        val result = provider.getSeriesDetail(seriesId)

        // Verify
        assertTrue(result.isSuccess)
        val detail = result.getOrThrow()
        assertEquals(2, detail.seasons.size)

        // Verify episodes are grouped correctly
        // seasonKey is indexNumber.toString()
        assertEquals(1, detail.episodes["1"]?.size)
        assertEquals(1, detail.episodes["2"]?.size)

        // Verify optimization: NO calls to getEpisodes
        coVerify(exactly = 0) { api.getEpisodes(any(), any()) }

        // Verify single call to getItems
        coVerify(exactly = 1) {
             api.getItems(
                parentId = seriesId,
                includeItemTypes = "Episode",
                sortBy = any(),
                sortOrder = any()
            )
        }
    }
}
