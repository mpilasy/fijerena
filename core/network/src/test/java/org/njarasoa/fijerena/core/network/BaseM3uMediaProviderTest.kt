package org.njarasoa.fijerena.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities

class BaseM3uMediaProviderTest {
    private class TestM3uProvider(
        testItems: List<MediaItem>,
    ) : BaseM3uMediaProvider() {
        override val providerId: Long = 1L
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportedContentTypes = setOf(ContentType.MOVIES),
            supportsEpg = false,
            supportsSearch = true,
            supportsAuthentication = false,
            supportsProgressSync = false,
        )

        init {
            items = testItems
            connected = true
        }

        override suspend fun connect(): kotlin.Result<Unit> = kotlin.Result.success(Unit)
    }

    @Test
    fun search_returnsMatchingItemsByQuery() = runTest {
        val sampleItems = listOf(
            MediaItem(id = "1", name = "Dune Part Two", mediaType = MediaType.VIDEO_FILE, categoryId = "cat1"),
            MediaItem(id = "2", name = "Interstellar", mediaType = MediaType.VIDEO_FILE, categoryId = "cat1"),
            MediaItem(id = "3", name = "Dune", mediaType = MediaType.VIDEO_FILE, categoryId = "cat1"),
        )
        val provider = TestM3uProvider(sampleItems)

        val result = provider.search("dune", ContentType.MOVIES)

        assertTrue(result.isSuccess)
        val matches = result.getOrNull()!!
        assertEquals(2, matches.size)
        assertEquals("Dune Part Two", matches[0].name)
        assertEquals("Dune", matches[1].name)
    }
}
