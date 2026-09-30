package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

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

    /**
     * [XtreamDatabase.MIGRATION_19_20] rebuilds `watch_state` and `favorite_state` with
     * `profileId` in the primary key. Same approach as above: fresh v20 install, both tables rolled
     * back to their exact v19 DDL (copied from MIGRATION_14_15/15_16, which is what every real
     * device has) with rows in them, then reopened through the real migration.
     */
    @Test
    fun migration19To20_assignsEveryRowToTheDefaultProfile() {
        val seedDb = Room.databaseBuilder(context, XtreamDatabase::class.java, testDbName).build()
        val rawDb = seedDb.openHelper.writableDatabase
        rawDb.execSQL("DROP TABLE `watch_state`")
        rawDb.execSQL("DROP TABLE `favorite_state`")
        rawDb.execSQL(
            "CREATE TABLE `watch_state` (" +
                "`providerId` INTEGER NOT NULL, `itemId` TEXT NOT NULL, `contentType` TEXT NOT NULL, " +
                "`itemName` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`lastPlayedAt` INTEGER, `seriesId` TEXT, `episodeId` TEXT, `seriesName` TEXT, " +
                "`episodeExtension` TEXT, `audioTrackIndex` INTEGER, `subtitleTrackIndex` INTEGER, " +
                "PRIMARY KEY(`providerId`, `itemId`, `contentType`))",
        )
        rawDb.execSQL(
            "CREATE INDEX `index_watch_state_providerId_contentType_lastPlayedAt` " +
                "ON `watch_state` (`providerId`, `contentType`, `lastPlayedAt`)",
        )
        rawDb.execSQL("CREATE INDEX `index_watch_state_providerId_seriesId` ON `watch_state` (`providerId`, `seriesId`)")
        rawDb.execSQL(
            "CREATE TABLE `favorite_state` (" +
                "`providerId` INTEGER NOT NULL, `itemId` TEXT NOT NULL, `contentType` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `name` TEXT NOT NULL, `parentCategoryId` TEXT, " +
                "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`providerId`, `itemId`, `contentType`, `kind`))",
        )
        rawDb.execSQL(
            "CREATE INDEX `index_favorite_state_providerId_kind_contentType_createdAt` " +
                "ON `favorite_state` (`providerId`, `kind`, `contentType`, `createdAt`)",
        )
        rawDb.execSQL(
            "INSERT INTO `watch_state` VALUES " +
                "(42, 'm1', 'MOVIES', 'Film', 'c1', 1000, 5000, 0, 111, 222, NULL, NULL, NULL, NULL, 1, NULL), " +
                "(42, 'e1', 'TV_SHOWS', 'Ep', 'c2', 0, 0, 1, 333, NULL, 's1', 'e1', 'Show', 'mkv', NULL, 2)",
        )
        rawDb.execSQL(
            "INSERT INTO `favorite_state` VALUES " +
                "(42, 'm1', 'MOVIES', 'STREAM', 'Film', 'c1', 444), (42, 'c9', 'LIVE_TV', 'CATEGORY', 'News', NULL, 555)",
        )
        rawDb.execSQL("PRAGMA user_version = 19")
        seedDb.close()

        // No fallbackToDestructiveMigration: a schema mismatch must throw, not silently wipe.
        val migratedDb =
            Room.databaseBuilder(context, XtreamDatabase::class.java, testDbName)
                .addMigrations(XtreamDatabase.MIGRATION_19_20)
                .build()
        assertEquals(20, migratedDb.openHelper.writableDatabase.version)

        val default = ProfileEntity.DEFAULT_ID
        runBlocking {
            val movie = migratedDb.watchStateDao().getItem(42L, default, "m1", "MOVIES")!!
            assertEquals(1000L, movie.positionMs)
            assertEquals(222L, movie.lastPlayedAt)
            assertEquals(1, movie.audioTrackIndex)
            val episode = migratedDb.watchStateDao().getItem(42L, default, "e1", "TV_SHOWS")!!
            assertTrue(episode.isCompleted)
            assertEquals("s1", episode.seriesId)
            assertEquals(2, episode.subtitleTrackIndex)
            assertTrue(migratedDb.watchStateDao().getAll(42L, "someone-else").isEmpty())
        }
        val favorites = migratedDb.favoriteStateDao().getAll(42L, default)
        assertEquals(listOf("c9", "m1"), favorites.map { it.itemId })
        assertTrue(migratedDb.favoriteStateDao().getAll(42L, "someone-else").isEmpty())

        migratedDb.close()
    }
}
