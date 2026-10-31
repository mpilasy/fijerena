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
 * [XtreamDatabase.MIGRATION_28_29] adds an empty `followed_sport` table, keeping the Wanted list.
 * On the JVM under Robolectric, against the exported schemas. See the sports teams plan, Phase 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XtreamDatabaseMigration28To29Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), XtreamDatabase::class.java)

    @Test
    fun `28 to 29 adds the followed teams and leagues table`() {
        helper.createDatabase(TEST_DB, 28).apply {
            execSQL(
                "INSERT INTO `wanted_title` (`profileId`, `contentType`, `tmdbId`, `title`, `addedAt`, `inCinemas`) " +
                    "VALUES ('default', 'MOVIES', '438631', 'Dune', 1, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 29, true, XtreamDatabase.MIGRATION_28_29)

        db.query("SELECT `title` FROM `wanted_title`").use {
            it.moveToFirst()
            assertEquals("Dune", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM `followed_sport`").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "xtream-migration-28-29-test"
    }
}
