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
 * [XtreamDatabase.MIGRATION_31_32] adds an empty `new_episode` table, keeping the favourites. On
 * the JVM under Robolectric, against the exported schemas. See the new episode alerts plan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XtreamDatabaseMigration31To32Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), XtreamDatabase::class.java)

    @Test
    fun `31 to 32 adds the new episodes table`() {
        helper.createDatabase(TEST_DB, 31).apply {
            execSQL(
                "INSERT INTO `favorite_state` (`providerId`, `profileId`, `itemId`, `contentType`, `kind`, `name`, `createdAt`) " +
                    "VALUES (1, 'default', '42', 'TV_SHOWS', 'STREAM', 'Show', 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 32, true, XtreamDatabase.MIGRATION_31_32)

        db.query("SELECT `name` FROM `favorite_state`").use {
            it.moveToFirst()
            assertEquals("Show", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM `new_episode`").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "xtream-migration-31-32-test"
    }
}
