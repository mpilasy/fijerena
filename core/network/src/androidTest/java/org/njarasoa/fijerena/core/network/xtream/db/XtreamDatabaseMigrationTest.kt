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
 * Verifies real migrations (not re-implementations of them) against databases shaped like actual
 * pre-migration installs: built fresh at the current schema via Room — guaranteeing every other
 * table/column matches exactly what Room expects — then rolled back to the old shape of just what
 * the migration under test changes.
 *
 * Reopening through Room afterward exercises Room's own post-migration schema validation, the same
 * check that decides whether a real device's data survives a migration or falls through
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

    /**
     * [XtreamDatabase.MIGRATION_17_18] only creates two indices. The current schema is several
     * versions past v18, so a fresh database can't be rolled back to a true v17 and reopened
     * through Room. Instead the migration runs directly against a fresh database with those two
     * indices dropped, and the indices it creates are compared with the ones Room itself creates
     * — the same names and columns Room's post-migration validation checks.
     */
    @Test
    fun migration17To18_addsIndicesWithoutLosingData() {
        val indexNames = listOf("index_xtream_series_providerId_tmdbId", "index_xtream_episodes_providerId_season_episodeNum")
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
        val rawDb = seedDb.openHelper.writableDatabase
        val expected = indexNames.associateWith { indexSql(rawDb, it) }
        indexNames.forEach { rawDb.execSQL("DROP INDEX `$it`") }

        XtreamDatabase.MIGRATION_17_18.migrate(rawDb)

        indexNames.forEach { name ->
            assertTrue("$name missing after migration", expected[name] != null)
            assertEquals(expected[name], indexSql(rawDb, name))
        }
        assertEquals("Test Show", seedDb.seriesDao().getSeriesById(42L, 1)?.name)
        assertEquals(1, seedDb.episodeDao().getEpisodes(42L, 1).size)
        seedDb.close()
    }

    private fun indexSql(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        name: String,
    ): String? =
        db.query("SELECT sql FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(name)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
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
        rawDb.execSQL("DROP TABLE `sync_tombstone`") // v21, recreated by MIGRATION_20_21
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
                .addMigrations(XtreamDatabase.MIGRATION_19_20, XtreamDatabase.MIGRATION_20_21)
                .build()
        assertEquals(21, migratedDb.openHelper.writableDatabase.version)

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

    /** [XtreamDatabase.MIGRATION_20_21] adds `sync_tombstone`; a v20 install is the current schema without it. */
    @Test
    fun migration20To21_addsTombstoneTableWithoutLosingData() {
        val seedDb = Room.databaseBuilder(context, XtreamDatabase::class.java, testDbName).build()
        seedDb.favoriteStateDao().upsert(
            FavoriteStateEntity(42L, ProfileEntity.DEFAULT_ID, "m1", "MOVIES", FavoriteKind.STREAM, "Film", "c1", 1L),
        )
        val rawDb = seedDb.openHelper.writableDatabase
        rawDb.execSQL("DROP TABLE `sync_tombstone`")
        rawDb.execSQL("PRAGMA user_version = 20")
        seedDb.close()

        val migratedDb =
            Room.databaseBuilder(context, XtreamDatabase::class.java, testDbName)
                .addMigrations(XtreamDatabase.MIGRATION_20_21)
                .build()
        assertEquals(21, migratedDb.openHelper.writableDatabase.version)

        val dao = migratedDb.favoriteStateDao()
        assertEquals(listOf("m1"), dao.getAll(42L, ProfileEntity.DEFAULT_ID).map { it.itemId })
        dao.deleteRecordingTombstone(42L, ProfileEntity.DEFAULT_ID, "m1", "MOVIES", FavoriteKind.STREAM, 99L)
        runBlocking {
            val tombstone = migratedDb.syncTombstoneDao().getAll(42L).single()
            assertEquals("m1", tombstone.itemId)
            assertEquals(99L, tombstone.deletedAt)
        }
        migratedDb.close()
    }
}
