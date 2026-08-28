package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeFavoriteStateDao
import org.njarasoa.fijerena.core.network.fixtures.FakeWatchStateDao
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind

/**
 * Favourites on `favorite_state` — see `docs/plans/favorites-durable-storage-plan.md`.
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
            assertEquals(1, favoriteDao.getAll(1L).count { it.kind == FavoriteKind.CATEGORY })
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
    fun `newest favorite is listed first`() =
        runBlocking {
            repository.addFavorite("old", "Old", "cat1", ContentType.MOVIES)
            repository.addFavorite("new", "New", "cat1", ContentType.MOVIES)
            repository.awaitPendingWrites()

            assertEquals(
                listOf("new", "old"),
                repository.getFavoritesForContentType(ContentType.MOVIES).map { it.id },
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
                    watchStateDao = FakeWatchStateDao(),
                    favoriteStateDao = favoriteDao,
                )
            repo.setProvider(mockk(relaxed = true))
            repo.awaitPendingWrites()

            assertEquals(2, favoriteDao.count(1L))
            val stream = favoriteDao.getAll(1L).single { it.kind == FavoriteKind.STREAM }
            assertEquals("m1", stream.itemId)
            assertEquals("Movie 1", stream.name)
            assertEquals("c1", stream.parentCategoryId)
            assertEquals(111L, stream.createdAt)
            val category = favoriteDao.getAll(1L).single { it.kind == FavoriteKind.CATEGORY }
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
                    watchStateDao = FakeWatchStateDao(),
                    favoriteStateDao = favoriteDao,
                )
            repo.setProvider(mockk(relaxed = true))
            repo.awaitPendingWrites()

            assertEquals(0, favoriteDao.count(1L))
        }
}
