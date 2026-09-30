package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeFavoriteStateDao
import org.njarasoa.fijerena.core.network.fixtures.FakeWatchStateDao
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.player.domain.ContentType

/**
 * Two profiles on the same provider share the tables but never each other's rows — see
 * `docs/plans/20260929_live-sync-plan.md` → User profiles, Phase 1.
 */
class MediaRepositoryProfileTest {
    private lateinit var context: Context
    private lateinit var watchStateDao: FakeWatchStateDao
    private lateinit var favoriteDao: FakeFavoriteStateDao
    private lateinit var defaultRepo: MediaRepository
    private lateinit var otherRepo: MediaRepository

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        context = mockk(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getString(any(), any()) } returns null
        watchStateDao = FakeWatchStateDao()
        favoriteDao = FakeFavoriteStateDao()
        defaultRepo = repo(ProfileEntity.DEFAULT_ID)
        otherRepo = repo(OTHER)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun repo(profileId: String) =
        MediaRepository(context, 1L, profileId, watchStateDao = watchStateDao, favoriteStateDao = favoriteDao)

    @Test
    fun `a favorite belongs to the profile that added it`() =
        runBlocking {
            defaultRepo.addFavorite("m1", "Film", "cat1", ContentType.MOVIES)
            defaultRepo.awaitPendingWrites()

            assertTrue(repo(ProfileEntity.DEFAULT_ID).isFavorite("m1", ContentType.MOVIES))
            assertFalse(repo(OTHER).isFavorite("m1", ContentType.MOVIES))
        }

    @Test
    fun `clearing favorites leaves the other profile's alone`() =
        runBlocking {
            defaultRepo.addFavorite("m1", "Film", "cat1", ContentType.MOVIES)
            otherRepo.addFavorite("m2", "Other film", "cat1", ContentType.MOVIES)
            defaultRepo.awaitPendingWrites()
            otherRepo.awaitPendingWrites()

            otherRepo.clearFavorites()
            otherRepo.awaitPendingWrites()

            assertTrue(repo(ProfileEntity.DEFAULT_ID).isFavorite("m1", ContentType.MOVIES))
            assertFalse(repo(OTHER).isFavorite("m2", ContentType.MOVIES))
        }

    @Test
    fun `watch progress belongs to the profile that watched`() =
        runBlocking {
            defaultRepo.savePlaybackPosition("m1", "Film", "cat1", ContentType.MOVIES, 5_000L, 100_000L)
            defaultRepo.awaitPendingWrites()

            assertEquals(listOf("m1"), defaultRepo.getWatchHistory().map { it.itemId })
            assertTrue(otherRepo.getWatchHistory().isEmpty())
        }

    @Test
    fun `clearing watch history leaves the other profile's alone`() =
        runBlocking {
            defaultRepo.savePlaybackPosition("m1", "Film", "cat1", ContentType.MOVIES, 5_000L, 100_000L)
            otherRepo.savePlaybackPosition("m1", "Film", "cat1", ContentType.MOVIES, 9_000L, 100_000L)
            defaultRepo.awaitPendingWrites()
            otherRepo.awaitPendingWrites()

            otherRepo.clearWatchHistory()

            assertEquals(5_000L, defaultRepo.getWatchHistory().single().playbackPosition)
            assertTrue(otherRepo.getWatchHistory().isEmpty())
        }

    @Test
    fun `only a non-Default profile gets its own prefs file for Recent Categories`() {
        defaultRepo.addToCategoryHistory("cat1", "News", ContentType.LIVE_TV)
        verify(exactly = 0) { context.getSharedPreferences(MediaRepository.profileCacheName(1L, ProfileEntity.DEFAULT_ID), any()) }

        otherRepo.addToCategoryHistory("cat1", "News", ContentType.LIVE_TV)
        verify { context.getSharedPreferences(MediaRepository.profileCacheName(1L, OTHER), any()) }
    }

    private companion object {
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
