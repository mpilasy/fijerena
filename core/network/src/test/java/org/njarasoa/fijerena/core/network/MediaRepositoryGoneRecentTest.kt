package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeFavoriteStateDao
import org.njarasoa.fijerena.core.network.fixtures.FakeWatchStateDao
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.EpisodeId
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities

/**
 * A Recent row whose channel left the M3U list is found gone, so the lists mark it Gone from
 * source instead of trying to play it (UX audit fixes plan, finding 3).
 */
class MediaRepositoryGoneRecentTest {
    private lateinit var repo: MediaRepository

    private class Playlist(
        items: List<MediaItem>,
    ) : BaseM3uMediaProvider() {
        override val providerId: Long = PROVIDER
        override val capabilities: ProviderCapabilities =
            ProviderCapabilities(
                supportedContentTypes = setOf(ContentType.LIVE_TV),
                supportsEpg = false,
                supportsSearch = true,
                supportsAuthentication = false,
                supportsProgressSync = false,
            )

        init {
            publish(emptyList(), items)
        }

        override suspend fun connect(): kotlin.Result<Unit> = kotlin.Result.success(Unit)
    }

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getString(any(), any()) } returns null
        repo = MediaRepository(context, PROVIDER, PROFILE, watchStateDao = FakeWatchStateDao(), favoriteStateDao = FakeFavoriteStateDao())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a Recent channel the playlist no longer lists is gone`() =
        runBlocking {
            repo.setProvider(Playlist(listOf(channel("kept"))))

            val gone = repo.goneFromSource(listOf(channel("kept"), channel("left")), ContentType.LIVE_TV)

            assertEquals(setOf("left"), gone)
        }

    @Test
    fun `an episode card is never checked`() =
        runBlocking {
            repo.setProvider(Playlist(listOf(channel("kept"))))
            val episode = channel("e1").copy(target = BrowseTarget.Episode(episodeId = EpisodeId("e1")))

            assertTrue(repo.goneFromSource(listOf(episode), ContentType.TV_SHOWS).isEmpty())
        }

    @Test
    fun `a failed lookup marks nothing`() =
        runBlocking {
            val source = mockk<MediaProvider>(relaxed = true)
            coEvery { source.missingItems(any(), any()) } throws IllegalStateException("closed")
            repo.setProvider(source)

            assertTrue(repo.goneFromSource(listOf(channel("left")), ContentType.LIVE_TV).isEmpty())
        }

    private fun channel(id: String) = MediaItem(id = id, name = "Channel $id", mediaType = MediaType.LIVE_CHANNEL, categoryId = "c1")

    private companion object {
        const val PROVIDER = 3L
        const val PROFILE = "p1"
    }
}
