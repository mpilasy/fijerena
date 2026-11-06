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
 * [XtreamDatabase.MIGRATION_30_31] adds `favorite_state.tags` (`[]` on every row), keeping every
 * favourite. On the JVM under Robolectric, against the exported schemas. See the favourite tags
 * plan, FT1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XtreamDatabaseMigration30To31Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), XtreamDatabase::class.java)

    @Test
    fun `30 to 31 adds empty tags to every favourite`() {
        helper.createDatabase(TEST_DB, 30).apply {
            execSQL(
                "INSERT INTO `favorite_state` (`providerId`, `profileId`, `itemId`, `contentType`, `kind`, `name`, `createdAt`) " +
                    "VALUES (42, 'default', '7', 'LIVE_TV', 'STREAM', 'BBC One', 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true, XtreamDatabase.MIGRATION_30_31)

        db.query("SELECT `name`, `tags` FROM `favorite_state`").use {
            it.moveToFirst()
            assertEquals("BBC One", it.getString(0))
            assertEquals("[]", it.getString(1))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "xtream-migration-30-31-test"
    }
}
