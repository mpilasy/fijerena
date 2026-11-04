package org.njarasoa.fijerena.core.network.provider

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SettingsDatabase.MIGRATION_20_21] adds the index time of the last import, 0 until the next
 * import. Nothing is queued for live sync.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDatabaseMigration20To21Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SettingsDatabase::class.java)

    @Test
    fun `20 to 21 adds an index time of 0 and queues nothing`() {
        helper.createDatabase(TEST_DB, 20).apply {
            execSQL(
                "INSERT INTO `epg_source` (`id`, `url`, `label`, `timezone_offset_hours`, `added_at_ms`, `last_ingested_at_ms`, " +
                    "`enabled`, `last_ingestion_duration_ms`, `source_key`) " +
                    "VALUES (1, 'http://epg.test/1.xml', 'G1', 0, 1, 500, 1, 7000, 'src-1')",
            )
            execSQL("INSERT OR IGNORE INTO `sync_clock` (`id`, `hlc`, `applying`) VALUES (1, 0, 0)")
            SettingsSyncTriggers.install(this)
            execSQL("DELETE FROM `sync_version`")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 21, true, SettingsDatabase.MIGRATION_20_21)

        db.query("SELECT `last_ingestion_duration_ms`, `last_index_duration_ms` FROM `epg_source`").use {
            it.moveToFirst()
            assertEquals(7000, it.getInt(0))
            assertEquals(0, it.getInt(1))
        }
        db.query("SELECT COUNT(*) FROM `sync_version`").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "settings_migration_20_21_test.db"
    }
}
