package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
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
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.player.domain.ContentType

/**
 * Removing a favourite or clearing history records a tombstone for live sync, and re-adding the
 * favourite drops it — see docs/plans/archive/20260929_live-sync-plan.md → Deletions (tombstones).
 */
class MediaRepositoryTombstoneTest {
    private lateinit var watchStateDao: FakeWatchStateDao
    private lateinit var favoriteDao: FakeFavoriteStateDao
    private lateinit var repo: MediaRepository

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getString(any(), any()) } returns null
        watchStateDao = FakeWatchStateDao()
        favoriteDao = FakeFavoriteStateDao()
        repo = MediaRepository(context, PROVIDER, PROFILE, watchStateDao = watchStateDao, favoriteStateDao = favoriteDao)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `removing a favourite records it, re-adding it drops the record`() =
        runBlocking {
            repo.addFavorite("m1", "Film", "cat1", ContentType.MOVIES)
            repo.awaitPendingWrites()
            assertTrue(favoriteDao.tombstones.isEmpty())

            repo.removeFavorite("m1", ContentType.MOVIES)
            repo.awaitPendingWrites()
            val tombstone = favoriteDao.tombstones.values.single()
            assertEquals(SyncKind.FAVORITE_STREAM, tombstone.kind)
            assertEquals("m1", tombstone.itemId)
            assertEquals(PROFILE, tombstone.profileId)

            repo.addFavorite("m1", "Film", "cat1", ContentType.MOVIES)
            repo.awaitPendingWrites()
            assertTrue(favoriteDao.tombstones.isEmpty())
        }

    @Test
    fun `removing a favourite category records it as a category`() =
        runBlocking {
            repo.addFavoriteCategory("c1", "News", ContentType.LIVE_TV)
            repo.awaitPendingWrites()

            repo.removeFavoriteCategory("c1", ContentType.LIVE_TV)
            repo.awaitPendingWrites()

            assertEquals(
                SyncKind.FAVORITE_CATEGORY,
                favoriteDao.tombstones.values
                    .single()
                    .kind,
            )
        }

    @Test
    fun `clearing favourites records each one`() =
        runBlocking {
            repo.addFavorite("m1", "Film", "cat1", ContentType.MOVIES)
            repo.addFavorite("m2", "Other film", "cat1", ContentType.MOVIES)
            repo.awaitPendingWrites()

            repo.clearFavorites()
            repo.awaitPendingWrites()

            assertEquals(
                setOf("m1", "m2"),
                favoriteDao.tombstones.values
                    .map { it.itemId }
                    .toSet(),
            )
        }

    @Test
    fun `clearing watch history records one clear marker`() =
        runBlocking {
            repo.savePlaybackPosition("m1", "Film", "cat1", ContentType.MOVIES, 5_000L, 100_000L)
            repo.awaitPendingWrites()

            repo.clearWatchHistory()

            val marker = watchStateDao.tombstones.single()
            assertEquals(SyncKind.WATCH_CLEAR, marker.kind)
            assertEquals(PROVIDER, marker.providerId)
            assertEquals(PROFILE, marker.profileId)
            assertTrue(repo.getWatchHistory().isEmpty())
        }

    private companion object {
        const val PROVIDER = 1L
        const val PROFILE = ProfileEntity.DEFAULT_ID
    }
}
