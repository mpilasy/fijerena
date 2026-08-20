package org.njarasoa.fijerena.core.network.jellyfin

import org.njarasoa.fijerena.core.player.domain.SeriesId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinMediaProviderTest {
    private val api = mockk<JellyfinApiService>(relaxed = true)
    private val provider =
        JellyfinMediaProvider(
            providerId = 1L,
            serverUrl = "http://localhost",
            username = "user",
            password = "pass",
            deviceId = "device",
            injectedApi = api,
        )

    @Test
    fun `getSeriesDetail fetches all episodes in one call (optimized behavior)`() =
        runTest {
            // Setup mocks
            val seriesId = "series1"
            val season1Id = "season1"
            val season2Id = "season2"

            val seriesItem = JellyfinItem(id = seriesId, name = "Test Series", type = "Series")
            val season1Item = JellyfinItem(id = season1Id, name = "Season 1", type = "Season", indexNumber = 1)
            val season2Item = JellyfinItem(id = season2Id, name = "Season 2", type = "Season", indexNumber = 2)

            val ep1 =
                JellyfinItem(id = "ep1", name = "Ep 1", type = "Episode", indexNumber = 1, parentIndexNumber = 1, parentId = season1Id)
            val ep2 =
                JellyfinItem(id = "ep2", name = "Ep 2", type = "Episode", indexNumber = 1, parentIndexNumber = 2, parentId = season2Id)

            coEvery { api.getItemById(seriesId) } returns Result.success(seriesItem)
            coEvery { api.getSeasons(seriesId) } returns Result.success(listOf(season1Item, season2Item))

            // Mock getItems for episodes
            // api.getItems(parentId = seriesId, includeItemTypes = "Episode", ...)
            coEvery {
                api.getItems(
                    parentId = seriesId,
                    includeItemTypes = "Episode",
                    sortBy = any(),
                    sortOrder = any(),
                )
            } returns Result.success(listOf(ep1, ep2))

            // Assume connected
            every { api.isAuthenticated() } returns true

            // Execute
            val result = provider.getSeriesDetail(SeriesId(seriesId))

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
                    sortOrder = any(),
                )
            }
        }

    @Test
    fun `getSeriesDetail all three API calls are made in parallel`() =
        runTest {
            val seriesId = "series1"

            val seriesItem = JellyfinItem(id = seriesId, name = "Test Series", type = "Series")
            val season1 = JellyfinItem(id = "s1", name = "Season 1", type = "Season", indexNumber = 1)
            val ep1 = JellyfinItem(id = "ep1", name = "Ep 1", type = "Episode", indexNumber = 1, parentIndexNumber = 1)

            coEvery { api.getItemById(seriesId) } returns Result.success(seriesItem)
            coEvery { api.getSeasons(seriesId) } returns Result.success(listOf(season1))
            coEvery {
                api.getItems(parentId = seriesId, includeItemTypes = "Episode", sortBy = any(), sortOrder = any())
            } returns Result.success(listOf(ep1))
            every { api.isAuthenticated() } returns true

            val result = provider.getSeriesDetail(SeriesId(seriesId))

            assertTrue(result.isSuccess)

            // All three calls should be made exactly once (launched in parallel)
            coVerify(exactly = 1) { api.getItemById(seriesId) }
            coVerify(exactly = 1) { api.getSeasons(seriesId) }
            coVerify(exactly = 1) {
                api.getItems(parentId = seriesId, includeItemTypes = "Episode", sortBy = any(), sortOrder = any())
            }
        }

    @Test
    fun `getSeriesDetail returns failure when series item fetch fails`() =
        runTest {
            val seriesId = "series1"

            coEvery { api.getItemById(seriesId) } returns Result.failure(Exception("Not found"))
            coEvery { api.getSeasons(seriesId) } returns Result.success(emptyList())
            coEvery {
                api.getItems(parentId = seriesId, includeItemTypes = "Episode", sortBy = any(), sortOrder = any())
            } returns Result.success(emptyList())
            every { api.isAuthenticated() } returns true

            val result = provider.getSeriesDetail(SeriesId(seriesId))

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }

    @Test
    fun `getSeriesDetail returns failure when seasons fetch fails`() =
        runTest {
            val seriesId = "series1"
            val seriesItem = JellyfinItem(id = seriesId, name = "Test Series", type = "Series")

            coEvery { api.getItemById(seriesId) } returns Result.success(seriesItem)
            coEvery { api.getSeasons(seriesId) } returns Result.failure(Exception("Server error"))
            coEvery {
                api.getItems(parentId = seriesId, includeItemTypes = "Episode", sortBy = any(), sortOrder = any())
            } returns Result.success(emptyList())
            every { api.isAuthenticated() } returns true

            val result = provider.getSeriesDetail(SeriesId(seriesId))

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }

    @Test
    fun `getSeriesDetail succeeds with empty episodes when episode fetch fails`() =
        runTest {
            val seriesId = "series1"
            val seriesItem = JellyfinItem(id = seriesId, name = "Test Series", type = "Series")
            val season1 = JellyfinItem(id = "s1", name = "Season 1", type = "Season", indexNumber = 1)

            coEvery { api.getItemById(seriesId) } returns Result.success(seriesItem)
            coEvery { api.getSeasons(seriesId) } returns Result.success(listOf(season1))
            coEvery {
                api.getItems(parentId = seriesId, includeItemTypes = "Episode", sortBy = any(), sortOrder = any())
            } returns Result.failure(Exception("Timeout"))
            every { api.isAuthenticated() } returns true

            val result = provider.getSeriesDetail(SeriesId(seriesId))

            // Should still succeed — episode failure is gracefully handled
            assertTrue(result.isSuccess)
            val detail = result.getOrThrow()
            assertEquals(1, detail.seasons.size)
            assertTrue(detail.episodes.isEmpty())
        }
}
