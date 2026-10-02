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
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateDao
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Favourites on `favorite_state` — see `docs/plans/20260828_favorites-durable-storage-plan.md`.
 *
 * The defect these exist for: the old blob did `take(providerSettings.favoritesMaxSize)` on every
 * write, so favouriting past the cap silently evicted the oldest entry.
 */
class MediaRepositoryFavoritesTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var favoriteDao: FakeFavoriteStateDao
    private lateinit var repository: MediaRepository

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        context = mockk(relaxed = true)
        prefs = mockk<SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getString(any(), any()) } returns null
        favoriteDao = FakeFavoriteStateDao()
        repository =
            MediaRepository(
                context,
                1L,
                ProfileEntity.DEFAULT_ID,
                watchStateDao = FakeWatchStateDao(),
                favoriteStateDao = favoriteDao,
            )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `favorites past the old cap are all kept`() =
        runBlocking {
            // 150 is past the old default of 100. Under the blob the first 50 were evicted.
            repeat(150) { i ->
                repository.addFavorite("item$i", "Item $i", "cat1", ContentType.MOVIES)
            }
            repository.awaitPendingWrites()

            assertEquals(150, favoriteDao.count(1L))
            assertTrue(repository.isFavorite("item0", ContentType.MOVIES))
            assertTrue(repository.isFavorite("item149", ContentType.MOVIES))
            assertEquals(150, repository.getFavoritesForContentType(ContentType.MOVIES).size)
        }

    // docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-15: a profile or provider
    // switch closes every cached repository; close() used to cancel the write queue, dropping
    // whatever was still in it, and every later write from a screen still holding the repository.
    @Test
    fun `writes queued before close, and made after it, still land`() =
        runBlocking {
            repeat(50) { i -> repository.addFavorite("item$i", "Item $i", "cat1", ContentType.MOVIES) }
            repository.close()
            repository.addFavorite("late", "Late", "cat1", ContentType.MOVIES)
            repository.awaitPendingWrites()

            assertEquals(51, favoriteDao.count(1L))
        }

    // F-15 again, made deterministic: above, the 50 writes usually finish before close() runs, so
    // only the late write proves anything. Here the first write is held mid-flight, so the second
    // is certainly still queued — not started — when close() runs. A close() that cancels the
    // queue drops it.
    @Test
    fun `close drains a favourite write still queued behind a running one`() =
        runBlocking {
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val gated =
                object : FavoriteStateDao by favoriteDao {
                    override fun upsertClearingTombstone(entity: FavoriteStateEntity) {
                        if (entity.itemId == "first") {
                            started.countDown()
                            release.await(5, TimeUnit.SECONDS)
                        }
                        favoriteDao.upsertClearingTombstone(entity)
                    }
                }
            val gatedRepository =
                MediaRepository(context, 1L, ProfileEntity.DEFAULT_ID, watchStateDao = FakeWatchStateDao(), favoriteStateDao = gated)

            gatedRepository.addFavorite("first", "First", "cat1", ContentType.MOVIES)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            gatedRepository.addFavorite("queued", "Queued", "cat1", ContentType.MOVIES)
            gatedRepository.close()
            release.countDown()
            gatedRepository.awaitPendingWrites()

            assertEquals(setOf("first", "queued"), favoriteDao.getAll(1L, ProfileEntity.DEFAULT_ID).map { it.itemId }.toSet())
        }

    @Test
    fun `a favorite survives a new repository reading from the table`() =
        runBlocking {
            repository.addFavorite("item1", "Item 1", "cat1", ContentType.MOVIES)
            repository.awaitPendingWrites()

            // Fresh instance, empty in-memory snapshot: the answer has to come from the rows.
            val reopened =
                MediaRepository(
                    context,
                    1L,
                    ProfileEntity.DEFAULT_ID,
                    watchStateDao = FakeWatchStateDao(),
                    favoriteStateDao = favoriteDao,
                )

            assertTrue(reopened.isFavorite("item1", ContentType.MOVIES))
            assertEquals("Item 1", reopened.getFavoritesForContentType(ContentType.MOVIES).single().name)
        }

    @Test
    fun `removing a favorite deletes its row`() =
        runBlocking {
            repository.addFavorite("item1", "Item 1", "cat1", ContentType.MOVIES)
            repository.awaitPendingWrites()

            assertTrue(repository.removeFavorite("item1", ContentType.MOVIES))
            repository.awaitPendingWrites()

            assertFalse(repository.isFavorite("item1", ContentType.MOVIES))
            assertEquals(0, favoriteDao.count(1L))
        }

    @Test
    fun `favoriting the same item twice is a no-op`() =
        runBlocking {
            assertTrue(repository.addFavorite("item1", "Item 1", "cat1", ContentType.MOVIES))
            assertFalse(repository.addFavorite("item1", "Item 1", "cat1", ContentType.MOVIES))
            repository.awaitPendingWrites()

            assertEquals(1, favoriteDao.count(1L))
        }

    @Test
    fun `the same id in two content types is two favorites`() =
        runBlocking {
            // Stream ids are only unique within a content type — a movie and a channel can collide.
            repository.addFavorite("7", "A Movie", "cat1", ContentType.MOVIES)
            repository.addFavorite("7", "A Channel", "cat2", ContentType.LIVE_TV)
            repository.awaitPendingWrites()

            assertEquals(2, favoriteDao.count(1L))
            assertTrue(repository.isFavorite("7", ContentType.MOVIES))
            assertTrue(repository.isFavorite("7", ContentType.LIVE_TV))
        }

    @Test
    fun `clearing favorites leaves favorite categories alone`() =
        runBlocking {
            // Both dialogs say "all favorited streams", so categories must survive.
            repository.addFavorite("item1", "Item 1", "cat1", ContentType.MOVIES)
            repository.addFavoriteCategory("cat1", "Category 1", ContentType.MOVIES)
            repository.awaitPendingWrites()

            repository.clearFavorites()
            repository.awaitPendingWrites()

            assertFalse(repository.isFavorite("item1", ContentType.MOVIES))
            assertTrue(repository.isFavoriteCategory("cat1", ContentType.MOVIES))
            assertEquals(1, favoriteDao.getAll(1L, ProfileEntity.DEFAULT_ID).count { it.kind == FavoriteKind.CATEGORY })
        }

    @Test
    fun `favorite categories round-trip independently of streams`() =
        runBlocking {
            repository.addFavoriteCategory("cat1", "Category 1", ContentType.MOVIES)
            repository.awaitPendingWrites()

            assertTrue(repository.isFavoriteCategory("cat1", ContentType.MOVIES))
            assertFalse(repository.isFavorite("cat1", ContentType.MOVIES))
            assertEquals(
                "Category 1",
                repository.getFavoriteCategoriesForContentType(ContentType.MOVIES).single().name,
            )
        }

    @Test
    fun `favorites are listed sorted by name, not insertion order`() =
        runBlocking {
            repository.addFavorite("z", "Zebra", "cat1", ContentType.MOVIES)
            repository.addFavorite("a", "apple", "cat1", ContentType.MOVIES)
            repository.addFavorite("m", "Mango", "cat1", ContentType.MOVIES)
            repository.awaitPendingWrites()

            assertEquals(
                listOf("a", "m", "z"),
                repository.getFavoritesForContentType(ContentType.MOVIES).map { it.id },
            )
        }

    @Test
    fun `favorite categories are listed sorted by name, not insertion order`() =
        runBlocking {
            repository.addFavoriteCategory("z", "Zebra Category", ContentType.MOVIES)
            repository.addFavoriteCategory("a", "apple Category", ContentType.MOVIES)
            repository.awaitPendingWrites()

            assertEquals(
                listOf("a", "z"),
                repository.getFavoriteCategoriesForContentType(ContentType.MOVIES).map { it.id },
            )
        }

    /**
     * The migration path. A pre-table install has both blobs in prefs; the first `setProvider()`
     * must copy them into rows and remove the keys, once per provider.
     */
    @Test
    fun `legacy blobs are backfilled into rows and purged`() =
        runBlocking {
            val favJson =
                """[{"itemId":"m1","itemName":"Movie 1","categoryId":"c1","contentType":"MOVIES","timestamp":111}]"""
            val catJson =
                """[{"categoryId":"c9","categoryName":"Cat 9","contentType":"MOVIES","timestamp":222}]"""
            every { prefs.getString("favorites_v2", null) } returns favJson
            every { prefs.getString("favorite_categories", null) } returns catJson
            every { prefs.getBoolean("favorites_migrated_v1", false) } returns false
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            every { prefs.edit() } returns editor
            every { editor.putBoolean(any(), any()) } returns editor
            every { editor.remove(any()) } returns editor

            val repo =
                MediaRepository(
                    context,
                    1L,
                    ProfileEntity.DEFAULT_ID,
                    watchStateDao = FakeWatchStateDao(),
                    favoriteStateDao = favoriteDao,
                )
            repo.setProvider(mockk(relaxed = true))
            repo.awaitPendingWrites()

            assertEquals(2, favoriteDao.count(1L))
            val stream = favoriteDao.getAll(1L, ProfileEntity.DEFAULT_ID).single { it.kind == FavoriteKind.STREAM }
            assertEquals("m1", stream.itemId)
            assertEquals("Movie 1", stream.name)
            assertEquals("c1", stream.parentCategoryId)
            assertEquals(111L, stream.createdAt)
            val category = favoriteDao.getAll(1L, ProfileEntity.DEFAULT_ID).single { it.kind == FavoriteKind.CATEGORY }
            assertEquals("c9", category.itemId)
            assertEquals("Cat 9", category.name)

            verify { editor.putBoolean("favorites_migrated_v1", true) }
            verify { editor.remove("favorites_v2") }
            verify { editor.remove("favorite_categories") }
        }

    /** Already-migrated providers must not re-read the blob — the keys are gone by then anyway. */
    @Test
    fun `backfill does not run twice`() =
        runBlocking {
            every { prefs.getBoolean("favorites_migrated_v1", false) } returns true
            every { prefs.getString("favorites_v2", null) } returns
                """[{"itemId":"m1","itemName":"Movie 1","categoryId":"c1","contentType":"MOVIES","timestamp":111}]"""

            val repo =
                MediaRepository(
                    context,
                    1L,
                    ProfileEntity.DEFAULT_ID,
                    watchStateDao = FakeWatchStateDao(),
                    favoriteStateDao = favoriteDao,
                )
            repo.setProvider(mockk(relaxed = true))
            repo.awaitPendingWrites()

            assertEquals(0, favoriteDao.count(1L))
        }
}
