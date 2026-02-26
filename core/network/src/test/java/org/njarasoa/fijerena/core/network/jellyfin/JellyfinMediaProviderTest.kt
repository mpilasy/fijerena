package org.njarasoa.fijerena.core.network.jellyfin

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.SeriesDetail

class JellyfinMediaProviderTest {

    private val api = mockk<JellyfinApiService>(relaxed = true)
    private val provider = JellyfinMediaProvider(
        providerId = 1,
        serverUrl = "http://localhost",
        username = "user",
        password = "password",
        deviceId = "device",
        injectedApi = api
    )

    @Test
    fun `getSeriesDetail fetches all episodes in one call`() = runTest {
        val seriesId = "series1"
        val seasonId1 = "season1"
        val seasonId2 = "season2"

        // Mock Series Item
        coEvery { api.getItemById(seriesId) } returns Result.success(
            JellyfinItem(id = seriesId, name = "Series 1", type = "Series")
        )

        // Mock Seasons
        coEvery { api.getSeasons(seriesId) } returns Result.success(
            listOf(
                JellyfinItem(id = seasonId1, name = "Season 1", indexNumber = 1, type = "Season"),
                JellyfinItem(id = seasonId2, name = "Season 2", indexNumber = 2, type = "Season"),
                JellyfinItem(id = "season3", name = "Season 3", indexNumber = 3, type = "Season")
            )
        )

        // Mock Episodes (Single Call Optimization)
        coEvery {
            api.getItems(
                parentId = seriesId,
                includeItemTypes = "Episode",
                sortBy = "SortName",
                sortOrder = "Ascending"
            )
        } returns Result.success(
            listOf(
                JellyfinItem(id = "ep1", name = "Ep 1", seasonId = seasonId1, indexNumber = 1, parentIndexNumber = 1, type = "Episode"),
                JellyfinItem(id = "ep2", name = "Ep 2", seasonId = seasonId2, indexNumber = 1, parentIndexNumber = 2, type = "Episode")
            )
        )

        // Mock Episodes (Legacy Call - should be called in current implementation)
        coEvery { api.getEpisodes(seriesId, seasonId1) } returns Result.success(
            listOf(JellyfinItem(id = "ep1", name = "Ep 1", seasonId = seasonId1, indexNumber = 1, parentIndexNumber = 1, type = "Episode"))
        )
        coEvery { api.getEpisodes(seriesId, seasonId2) } returns Result.success(
             listOf(JellyfinItem(id = "ep2", name = "Ep 2", seasonId = seasonId2, indexNumber = 1, parentIndexNumber = 2, type = "Episode"))
        )

        // Execute
        // We simulate connection first to pass ensureConnected check
        coEvery { api.authenticate(any(), any()) } returns Result.success(
             JellyfinAuthResponse(JellyfinUser("u1", "user"), "token")
        )
        coEvery { api.isAuthenticated() } returns true

        val result = provider.getSeriesDetail(seriesId)

        // Verify
        assert(result.isSuccess)

        // The test asserts that the optimized call happened.
        // It fails initially because current code doesn't do it.
        coVerify(exactly = 1) {
            api.getItems(
                parentId = seriesId,
                includeItemTypes = "Episode",
                sortBy = "SortName",
                sortOrder = "Ascending"
            )
        }

        // Verify Legacy Calls did NOT happen
        coVerify(exactly = 0) { api.getEpisodes(seriesId, any()) }
    }
}
