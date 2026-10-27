package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [XtreamDatabase.MIGRATION_27_28] adds the title facts column to films and shows, and clears
 * their detail stamps so the next open fetches the facts, keeping every row and its rating.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XtreamDatabaseMigration27To28Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), XtreamDatabase::class.java)

    @Test
    fun `27 to 28 adds the facts and clears the detail stamps`() {
        helper.createDatabase(TEST_DB, 27).apply {
            execSQL(
                "INSERT INTO `xtream_streams` (`streamId`, `providerId`, `type`, `num`, `name`, `streamType`, `categoryId`, " +
                    "`tvArchive`, `tvArchiveDuration`, `contentHash`, `excluded`, `contentRating`, `detailFetchedAt`) " +
                    "VALUES (1, 1, 'VOD', 1, 'Dune', 'movie', '1', 0, 0, 0, 0, 'PG-13', 100)",
            )
            execSQL(
                "INSERT INTO `xtream_series` (`seriesId`, `providerId`, `name`, `categoryId`, `contentHash`, `excluded`, " +
                    "`contentRating`, `detailFetchedAt`, `episodesFetchedAt`) VALUES (2, 1, 'Dark', '1', 0, 0, 'TV-MA', 100, 100)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 28, true, XtreamDatabase.MIGRATION_27_28)

        db.query("SELECT `name`, `contentRating`, `detailFetchedAt`, `facts` FROM `xtream_streams`").use {
            it.moveToFirst()
            assertEquals("Dune", it.getString(0))
            assertEquals("PG-13", it.getString(1))
            assertTrue(it.isNull(2))
            assertTrue(it.isNull(3))
        }
        db.query("SELECT `name`, `contentRating`, `detailFetchedAt`, `episodesFetchedAt`, `facts` FROM `xtream_series`").use {
            it.moveToFirst()
            assertEquals("Dark", it.getString(0))
            assertEquals("TV-MA", it.getString(1))
            assertTrue(it.isNull(2))
            assertTrue(it.isNull(3))
            assertTrue(it.isNull(4))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "xtream-migration-27-28-test"
    }
}
