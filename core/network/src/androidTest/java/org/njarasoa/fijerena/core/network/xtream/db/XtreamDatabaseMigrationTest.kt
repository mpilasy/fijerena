package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the real [XtreamDatabase.MIGRATION_17_18] (not a re-implementation of it) against a
 * database shaped like an actual pre-migration install: built fresh at the current (v18) schema
 * via Room — guaranteeing every other table/column matches exactly what Room expects — then rolled
 * back to look like v17 by dropping just the two new indices and resetting `PRAGMA user_version`,
 * the only two things that actually differ between v17 and v18.
 *
 * Reopening through Room afterward exercises Room's own post-migration schema validation, the same
 * check that decides whether a real device's data survives this migration or falls through
 * [XtreamDatabase]'s `fallbackToDestructiveMigration(dropAllTables = true)` and gets wiped —
 * including `watch_state`/`favorite_state`, neither of which is re-fetchable from the server.
 *
 * Uses its own on-disk file (never the app's real `xtream_v2.db`), deleted before and after.
 */
@RunWith(AndroidJUnit4::class)
class XtreamDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDbName = "test_xtream_migration_17_18.db"

    @Before
    fun deleteTestDbBefore() {
        context.deleteDatabase(testDbName)
    }

    @After
    fun deleteTestDbAfter() {
        context.deleteDatabase(testDbName)
    }

    @Test
    fun migration17To18_addsIndicesWithoutLosingData() {
        // 1. Fresh install at the current (v18) schema — Room's own onCreate() builds every
        // table/index exactly as compiled, so nothing here can be hand-authored wrong.
        val seedDb =
            Room.databaseBuilder(context, XtreamDatabase::class.java, testDbName).build()
        seedDb.seriesDao().insertAll(
            listOf(
                XtreamSeriesEntity(
                    seriesId = 1,
                    providerId = 42L,
                    name = "Test Show",
                    categoryId = "cat1",
                    tmdbId = "tt123",
                ),
            ),
        )
        seedDb.episodeDao().insertAll(
            listOf(
                XtreamEpisodeEntity(
                    id = "ep1",
                    seriesId = 1,
                    providerId = 42L,
                    season = 1,
                    episodeNum = 1,
                    title = "Pilot",
                    containerExtension = "mp4",
                ),
            ),
        )

        // 2. Roll it back to look like v17: drop the two indices this migration adds, and reset
        // the version pragma Room's opener checks. Nothing else differs between v17 and v18.
        val rawDb = seedDb.openHelper.writableDatabase
        rawDb.execSQL("DROP INDEX `index_xtream_series_providerId_tmdbId`")
        rawDb.execSQL("DROP INDEX `index_xtream_episodes_providerId_season_episodeNum`")
        rawDb.execSQL("PRAGMA user_version = 17")
        seedDb.close()

        // 3. Reopen through Room with ONLY the migration under test registered, and no
        // fallbackToDestructiveMigration — a schema mismatch must throw here, not silently wipe,
        // so this test actually fails loud on a bad migration instead of passing green over data
        // loss.
        val migratedDb =
            Room.databaseBuilder(context, XtreamDatabase::class.java, testDbName)
                .addMigrations(XtreamDatabase.MIGRATION_17_18)
                .build()

        // Forces Room to actually open the file and run its post-migration validation, rather
        // than lazily deferring to the first DAO call.
        assertEquals(18, migratedDb.openHelper.writableDatabase.version)

        // 4. The two indices exist, exactly as Room expects them to be named.
        val indexNames = mutableSetOf<String>()
        migratedDb.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'index'")
            .use { cursor ->
                while (cursor.moveToNext()) indexNames.add(cursor.getString(0))
            }
        assertTrue(indexNames.contains("index_xtream_series_providerId_tmdbId"))
        assertTrue(indexNames.contains("index_xtream_episodes_providerId_season_episodeNum"))

        // 5. Seeded rows survived the migration untouched — this is a CREATE INDEX-only
        // migration, but assert it explicitly rather than assume.
        assertEquals("Test Show", migratedDb.seriesDao().getSeriesById(42L, 1)?.name)
        assertEquals(1, migratedDb.episodeDao().getEpisodes(42L, 1).size)

        migratedDb.close()
    }
}
