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
 * [XtreamDatabase.MIGRATION_26_27] adds the ask-once answer and the release columns to
 * `wanted_title`, keeping every title. See the wishlist ux plan, Phase 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XtreamDatabaseMigration26To27Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), XtreamDatabase::class.java)

    @Test
    fun `26 to 27 adds the ask-once and release columns`() {
        helper.createDatabase(TEST_DB, 26).apply {
            execSQL(
                "INSERT INTO `wanted_title` (`profileId`, `contentType`, `tmdbId`, `title`, `addedAt`) " +
                    "VALUES ('default', 'MOVIES', '1', 'Dune', 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 27, true, XtreamDatabase.MIGRATION_26_27)

        db.query("SELECT `title`, `askedAt`, `releaseDate`, `inCinemas` FROM `wanted_title`").use {
            it.moveToFirst()
            assertEquals("Dune", it.getString(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
            assertEquals(0, it.getInt(3))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "xtream-migration-26-27-test"
    }
}
