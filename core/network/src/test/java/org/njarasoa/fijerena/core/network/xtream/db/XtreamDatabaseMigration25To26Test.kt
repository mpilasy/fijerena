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
 * [XtreamDatabase.MIGRATION_25_26] adds `favorite_state.tmdbId` (null on every row) and an empty
 * `wanted_title` table, keeping every favourite. On the JVM under Robolectric, against the
 * exported schemas. See the wanted titles plan, Phase 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XtreamDatabaseMigration25To26Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), XtreamDatabase::class.java)

    @Test
    fun `25 to 26 adds the favourite TMDB id and the Wanted table`() {
        helper.createDatabase(TEST_DB, 25).apply {
            execSQL(
                "INSERT INTO `favorite_state` (`providerId`, `profileId`, `itemId`, `contentType`, `kind`, `name`, `createdAt`) " +
                    "VALUES (42, 'default', '7', 'MOVIES', 'STREAM', 'Dune', 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 26, true, XtreamDatabase.MIGRATION_25_26)

        db.query("SELECT `name`, `tmdbId` FROM `favorite_state`").use {
            it.moveToFirst()
            assertEquals("Dune", it.getString(0))
            assertTrue(it.isNull(1))
        }
        db.query("SELECT COUNT(*) FROM `wanted_title`").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "xtream-migration-25-26-test"
    }
}
