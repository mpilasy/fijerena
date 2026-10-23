package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeFavoriteStateDao
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateDao
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity

/** [FavoriteCategoryRowCleanup]: only bogus `fav_cat_` stream favourites go, each with a tombstone. */
class FavoriteCategoryRowCleanupTest {
    private lateinit var dao: FakeFavoriteStateDao
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        dao = FakeFavoriteStateDao()
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.commit() } returns true
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        settings = AppSettings(context)
    }

    private fun row(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String = "TV_SHOWS",
        kind: String = FavoriteKind.STREAM,
    ) = FavoriteStateEntity(providerId, profileId, itemId, contentType, kind, "name", null, 1L)

    @Test
    fun `purge removes bogus stream rows on every provider and profile, and nothing else`() {
        dao.upsert(row(1, "default", "fav_cat_12"))
        dao.upsert(row(1, "kid", "fav_cat_12", contentType = "MOVIES"))
        dao.upsert(row(2, "default", "fav_cat_local_cat_99", contentType = "LIVE_TV"))
        // Kept: a real stream, a category favourite with such an id, and look-alikes `LIKE` would match.
        dao.upsert(row(1, "default", "12"))
        dao.upsert(row(1, "default", "fav_cat_12", kind = FavoriteKind.CATEGORY))
        dao.upsert(row(1, "default", "favXcat_12"))
        dao.upsert(row(1, "default", "smb_file_fav_cat_1"))

        val providers = FavoriteCategoryRowCleanup.purge(dao)

        assertEquals(setOf(1L, 2L), providers)
        assertNull(dao.get(1, "default", "fav_cat_12", "TV_SHOWS", FavoriteKind.STREAM))
        assertNull(dao.get(1, "kid", "fav_cat_12", "MOVIES", FavoriteKind.STREAM))
        assertNull(dao.get(2, "default", "fav_cat_local_cat_99", "LIVE_TV", FavoriteKind.STREAM))
        assertNotNull(dao.get(1, "default", "12", "TV_SHOWS", FavoriteKind.STREAM))
        assertNotNull(dao.get(1, "default", "fav_cat_12", "TV_SHOWS", FavoriteKind.CATEGORY))
        assertNotNull(dao.get(1, "default", "favXcat_12", "TV_SHOWS", FavoriteKind.STREAM))
        assertNotNull(dao.get(1, "default", "smb_file_fav_cat_1", "TV_SHOWS", FavoriteKind.STREAM))
        // Live sync: one favourite-stream tombstone per removed row, exactly like an unfavourite.
        assertEquals(3, dao.tombstones.size)
        assertTrue(dao.tombstones.values.all { it.kind == SyncKind.FAVORITE_STREAM && it.itemId.startsWith("fav_cat_") })
        assertNotNull(dao.tombstones[listOf(1L, "kid", SyncKind.FAVORITE_STREAM, "fav_cat_12", "MOVIES")])
    }

    @Test
    fun `runOnce purges, sets the flag and reports the providers`() =
        runBlocking {
            every { prefs.getBoolean("favorite_category_rows_purged_v1", false) } returns false
            dao.upsert(row(3, "default", "fav_cat_7"))
            var reported: Set<Long>? = null

            FavoriteCategoryRowCleanup.runOnce(settings, dao) { reported = it }

            assertNull(dao.get(3, "default", "fav_cat_7", "TV_SHOWS", FavoriteKind.STREAM))
            assertEquals(setOf(3L), reported)
            verify { editor.putBoolean("favorite_category_rows_purged_v1", true) }
        }

    @Test
    fun `runOnce does nothing once the flag is set`() =
        runBlocking {
            every { prefs.getBoolean("favorite_category_rows_purged_v1", false) } returns true
            dao.upsert(row(3, "default", "fav_cat_7"))
            var called = false

            FavoriteCategoryRowCleanup.runOnce(settings, dao) { called = true }

            assertNotNull(dao.get(3, "default", "fav_cat_7", "TV_SHOWS", FavoriteKind.STREAM))
            assertEquals(false, called)
            verify(exactly = 0) { editor.putBoolean("favorite_category_rows_purged_v1", any()) }
        }

    @Test
    fun `runOnce leaves the flag unset when the purge fails, so it runs again`() =
        runBlocking {
            every { prefs.getBoolean("favorite_category_rows_purged_v1", false) } returns false
            val failing = mockk<FavoriteStateDao>()
            every { failing.getAllOfKindWithIdPrefix(any(), any()) } throws IllegalStateException("db closed")

            val result = runCatching { FavoriteCategoryRowCleanup.runOnce(settings, failing) {} }

            assertTrue(result.isFailure)
            verify(exactly = 0) { editor.putBoolean("favorite_category_rows_purged_v1", any()) }
        }
}
