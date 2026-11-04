package org.njarasoa.fijerena.core.network.provider

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
 * [SettingsDatabase.MIGRATION_18_19] adds the file's offset (not known yet: null) and the offset
 * the guide index's times were read with: the source's own offset when it has one, else null (the
 * file's, not known yet). Nothing is queued for live sync.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDatabaseMigration18To19Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SettingsDatabase::class.java)

    private fun source(
        id: Int,
        offsetHours: Int,
    ) = "INSERT INTO `epg_source` (`id`, `url`, `label`, `timezone_offset_hours`, `added_at_ms`, `last_ingested_at_ms`, " +
        "`enabled`, `provider_id`, `source_key`) " +
        "VALUES ($id, 'http://epg.test/$id.xml', 'G$id', $offsetHours, $id, 500, 1, 1, 'src-$id')"

    @Test
    fun `18 to 19 keeps a source's own offset as the one its times were read with`() {
        helper.createDatabase(TEST_DB, 18).apply {
            execSQL(source(1, 8))
            execSQL(source(2, 0))
            execSQL(source(3, -5))
            execSQL("INSERT OR IGNORE INTO `sync_clock` (`id`, `hlc`, `applying`) VALUES (1, 0, 0)")
            SettingsSyncTriggers.install(this)
            execSQL("DELETE FROM `sync_version`")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 19, true, SettingsDatabase.MIGRATION_18_19)

        db.query("SELECT `file_utc_offset_minutes`, `applied_utc_offset_minutes` FROM `epg_source` ORDER BY `id`").use {
            it.moveToNext()
            assertTrue(it.isNull(0))
            assertEquals(480, it.getInt(1))
            it.moveToNext()
            assertTrue(it.isNull(0))
            assertTrue(it.isNull(1))
            it.moveToNext()
            assertEquals(-300, it.getInt(1))
        }
        db.query("SELECT COUNT(*) FROM `sync_version`").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "settings_migration_18_19_test.db"
    }
}
