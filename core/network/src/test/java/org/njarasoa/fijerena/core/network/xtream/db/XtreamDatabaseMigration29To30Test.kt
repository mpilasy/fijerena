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
 * [XtreamDatabase.MIGRATION_29_30] adds an empty `game_reminder` table, keeping the followed teams.
 * On the JVM under Robolectric, against the exported schemas. See the sports teams plan, Phase 7.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XtreamDatabaseMigration29To30Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), XtreamDatabase::class.java)

    @Test
    fun `29 to 30 adds the game reminders table`() {
        helper.createDatabase(TEST_DB, 29).apply {
            execSQL(
                "INSERT INTO `followed_sport` (`profileId`, `kind`, `source`, `itemId`, `name`, `altNames`, `sport`, `createdAt`) " +
                    "VALUES ('default', 'TEAM', 'espn', 'soccer/359', 'Arsenal', '[]', 'soccer', 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 30, true, XtreamDatabase.MIGRATION_29_30)

        db.query("SELECT `name` FROM `followed_sport`").use {
            it.moveToFirst()
            assertEquals("Arsenal", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM `game_reminder`").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "xtream-migration-29-30-test"
    }
}
