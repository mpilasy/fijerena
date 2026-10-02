package org.njarasoa.fijerena.core.network.xtream.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * `xtream_v2.db` holds watch history and favourites, so neither an upgrade nor a downgrade may
 * lose them. See docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-20, F-33.
 *
 * - **Every-version upgrade**: for each schema committed under `core/network/schemas/` (history
 *   starts at v24), a file is created at that version by [MigrationTestHelper], given user data,
 *   and opened the way the app opens it ([XtreamDatabase.build]) — the migrations to the current
 *   version run, Room validates the result, and the rows must still be there. Versions before the
 *   history starts are covered by [XtreamDatabaseMigrationTest]; the JVM gate
 *   `XtreamDatabaseMigrationChainTest` checks the migration chain and the schema files.
 * - **Downgrade**: a file from a newer build is set aside as `.bak` (the latest only), and the app
 *   opens an empty database instead of crashing every launch or dropping every table.
 *
 * Instrumented (needs a real SQLite): run by hand on an emulator, never by CI. Uses files of its
 * own, deleted before and after — never the app's real `xtream_v2.db`.
 */
@RunWith(AndroidJUnit4::class)
class XtreamDatabaseUpgradeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val testDbName = "test_xtream_upgrade.db"

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, XtreamDatabase::class.java)

    private fun cleanUp() {
        context.deleteDatabase(testDbName)
        context.getDatabasePath(testDbName).parentFile
            ?.listFiles { f -> f.name.startsWith("$testDbName.v") }
            ?.forEach { it.delete() }
    }

    @Before
    fun before() = cleanUp()

    @After
    fun after() = cleanUp()

    /** Exported schema versions, from the test APK's assets (`core/network/schemas/`). */
    private fun exportedVersions(): List<Int> =
        instrumentation.context.assets
            .list(XtreamDatabase::class.java.name)
            .orEmpty()
            .mapNotNull { it.removeSuffix(".json").toIntOrNull() }
            .sorted()

    @Test
    fun everyExportedVersionUpgradesToTheCurrentOneWithUserDataKept() {
        val versions = exportedVersions()
        assertTrue("no exported schemas in the test assets", versions.isNotEmpty())
        assertEquals(XtreamDatabase.DB_VERSION, versions.last())

        for (version in versions) {
            cleanUp()
            helper.createDatabase(testDbName, version).use { db ->
                // Columns as of v24, the first exported schema. A later version that changes these
                // tables updates this seed along with its migration.
                db.execSQL(
                    "INSERT INTO watch_state (providerId, profileId, itemId, contentType, itemName, categoryId, positionMs, " +
                        "durationMs, isCompleted, updatedAt, lastPlayedAt) VALUES (1, 'p', 'm1', 'MOVIES', 'Film', 'c', 600000, 7200000, 0, 5, 5)",
                )
                db.execSQL(
                    "INSERT INTO favorite_state (providerId, profileId, itemId, contentType, kind, name, parentCategoryId, createdAt) " +
                        "VALUES (1, 'p', 'ch1', 'LIVE_TV', 'STREAM', 'News', 'c', 7)",
                )
                db.execSQL(
                    "INSERT INTO sync_version (providerId, profileId, kind, itemId, contentType, hlc, pending) " +
                        "VALUES (1, 'p', 'favorite_stream', 'ch1', 'LIVE_TV', 9, 1)",
                )
            }

            val upgraded = XtreamDatabase.build(context, testDbName)
            try {
                val watched = runBlocking { upgraded.watchStateDao().getAll(1, "p") }
                assertEquals("watch history lost upgrading from v$version", listOf("m1"), watched.map { it.itemId })
                assertEquals(600_000L, watched.single().positionMs)
                assertEquals("favourites lost upgrading from v$version", listOf("ch1"), upgraded.favoriteStateDao().getAll(1, "p").map { it.itemId })
                assertEquals(XtreamDatabase.DB_VERSION, upgraded.openHelper.readableDatabase.version)
            } finally {
                upgraded.close()
            }
        }
    }

    @Test
    fun aFileFromANewerBuildIsSetAsideAndTheAppOpensEmpty() {
        val file = context.getDatabasePath(testDbName)
        file.parentFile?.mkdirs()
        val newer = XtreamDatabase.DB_VERSION + 1
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE watch_state (itemId TEXT PRIMARY KEY)")
            db.execSQL("INSERT INTO watch_state VALUES ('kept-in-the-backup')")
            db.version = newer
        }
        // An older backup from an earlier downgrade: only the latest is kept.
        val olderBackup = File(file.parentFile, "$testDbName.v${XtreamDatabase.DB_VERSION + 5}.bak").apply { writeText("old") }

        XtreamDatabase.setAsideIfNewer(context, testDbName)

        assertFalse(file.exists())
        assertFalse(olderBackup.exists())
        val backup = File(file.parentFile, "$testDbName.v$newer.bak")
        assertTrue(backup.exists())
        SQLiteDatabase.openDatabase(backup.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(newer, db.version)
            db.rawQuery("SELECT itemId FROM watch_state", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("kept-in-the-backup", c.getString(0))
            }
        }

        val reopened = XtreamDatabase.build(context, testDbName)
        try {
            assertEquals(XtreamDatabase.DB_VERSION, reopened.openHelper.readableDatabase.version)
            assertTrue(runBlocking { reopened.watchStateDao().getAll(1, "p") }.isEmpty())
        } finally {
            reopened.close()
        }
    }

    @Test
    fun aFileOfTheCurrentVersionIsLeftAlone() {
        val db = XtreamDatabase.build(context, testDbName)
        db.favoriteStateDao().upsert(FavoriteStateEntity(1, "p", "ch1", "LIVE_TV", FavoriteKind.STREAM, "News", "c", 7))
        db.close()

        XtreamDatabase.setAsideIfNewer(context, testDbName)

        val dir = context.getDatabasePath(testDbName).parentFile
        assertTrue(dir?.listFiles { f -> f.name.startsWith("$testDbName.v") }.isNullOrEmpty())
        val reopened = XtreamDatabase.build(context, testDbName)
        try {
            assertEquals(listOf("ch1"), reopened.favoriteStateDao().getAll(1, "p").map { it.itemId })
        } finally {
            reopened.close()
        }
    }
}
