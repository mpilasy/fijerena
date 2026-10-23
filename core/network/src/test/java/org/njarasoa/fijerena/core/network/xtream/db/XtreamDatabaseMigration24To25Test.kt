package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [XtreamDatabase.MIGRATION_24_25] adds the `allowed` flag (0 on every row) and its index to
 * streams and series, keeping every row. On the JVM under Robolectric, against the exported schemas
 * (`core/network/schemas`). See docs/plans/20261020_parental-controls-plan.md, Phase 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XtreamDatabaseMigration24To25Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), XtreamDatabase::class.java)

    @Test
    fun `24 to 25 adds the allowed flag to streams and series`() {
        helper.createDatabase(TEST_DB, 24).apply {
            execSQL(
                "INSERT INTO `xtream_streams` (`streamId`, `providerId`, `type`, `num`, `name`, `streamType`, `categoryId`, " +
                    "`tvArchive`, `tvArchiveDuration`, `contentHash`, `excluded`) VALUES (1, 42, 'VOD', 1, 'Naruto', 'movie', 'c1', 0, 0, 0, 1)",
            )
            execSQL(
                "INSERT INTO `xtream_series` (`seriesId`, `providerId`, `name`, `categoryId`, `contentHash`, `excluded`) " +
                    "VALUES (2, 42, 'One Piece', 'c2', 0, 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 25, true, XtreamDatabase.MIGRATION_24_25)

        db.query("SELECT `name`, `allowed` FROM `xtream_streams`").use {
            it.moveToFirst()
            assertEquals("Naruto", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
        db.query("SELECT `name`, `allowed` FROM `xtream_series`").use {
            it.moveToFirst()
            assertEquals("One Piece", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "xtream-migration-24-25-test"
    }
}
