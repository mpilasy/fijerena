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
        private val testItems: List<MediaItem>,
    ) : BaseM3uMediaProvider() {
        override val providerId: Long = 1L
        override val capabilities: ProviderCapabilities =
            ProviderCapabilities(
                supportedContentTypes = setOf(ContentType.MOVIES),
                supportsEpg = false,
                supportsSearch = true,
                supportsAuthentication = false,
                supportsProgressSync = false,
            )

        init {
            publish(emptyList(), testItems)
        }

        var connects = 0

        override suspend fun connect(): kotlin.Result<Unit> {
            connects++
            publish(emptyList(), testItems)
            return kotlin.Result.success(Unit)
        }
    }

    @Test
    fun search_returnsMatchingItemsByQuery() =
        runTest {
            val sampleItems =
                listOf(
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

    @Test
    fun trimMemory_dropsItemsAndMarksDisconnected() =
        runTest {
            val sampleItems =
                listOf(
                    MediaItem(id = "1", name = "Dune Part Two", mediaType = MediaType.VIDEO_FILE, categoryId = "cat1"),
                )
            val provider = TestM3uProvider(sampleItems)

            provider.trimMemory()

            // Marking disconnected is what makes this transparent: every read connects again when
            // !connected, so a trimmed provider must fall back into that path on next use instead
            // of permanently looking empty.
            assertTrue(!provider.isConnected())
            assertEquals(1, provider.search("dune", ContentType.MOVIES).getOrNull()!!.size)
            assertEquals(1, provider.connects)
        }

    @Test
    fun resolvePlayableStream_afterTrimMemory_connectsAgainAndResolves() =
        runTest {
            val item =
                MediaItem(id = "1", name = "News", mediaType = MediaType.LIVE_CHANNEL, categoryId = "cat1", streamUri = "http://x/1.ts")
            val provider = TestM3uProvider(listOf(item))

            provider.trimMemory()

            assertEquals("http://x/1.ts", provider.resolvePlayableStream("1", ContentType.LIVE_TV, null, null).getOrNull()?.uri)
            assertEquals(1, provider.connects)
        }

    @Test
    fun resolvePlayableStream_ofAChannelNoLongerListed_fails() =
        runTest {
            val provider = TestM3uProvider(emptyList())

            assertTrue(provider.resolvePlayableStream("gone", ContentType.LIVE_TV, null, null).isFailure)
        }

    @Test
    fun missingItems_namesTheIdsTheListNoLongerHas() =
        runTest {
            val item = MediaItem(id = "1", name = "News", mediaType = MediaType.LIVE_CHANNEL, categoryId = "cat1")
            val provider = TestM3uProvider(listOf(item))

            assertEquals(setOf("2"), provider.missingItems(listOf("1", "2"), ContentType.LIVE_TV))
        }

    @Test
    fun missingItems_withTheListDropped_marksNothing() =
        runTest {
            val item = MediaItem(id = "1", name = "News", mediaType = MediaType.LIVE_CHANNEL, categoryId = "cat1")
            val provider = TestM3uProvider(listOf(item))

            provider.trimMemory()

            assertTrue(provider.missingItems(listOf("1", "2"), ContentType.LIVE_TV).isEmpty())
            assertEquals(0, provider.connects)
        }

    @Test
    fun getItems_afterTrimMemory_connectsAgainAndReturnsTheItems() =
        runTest {
            val item = MediaItem(id = "1", name = "Dune", mediaType = MediaType.VIDEO_FILE, categoryId = "cat1")
            val provider = TestM3uProvider(listOf(item))

            provider.trimMemory()

            assertEquals(listOf(item), provider.getItems("cat1", ContentType.MOVIES).getOrNull())
            assertEquals(1, provider.connects)
            assertTrue(provider.isConnected())
        }
}
