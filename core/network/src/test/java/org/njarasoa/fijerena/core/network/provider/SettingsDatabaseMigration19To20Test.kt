package org.njarasoa.fijerena.core.network.provider

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SettingsDatabase.MIGRATION_19_20] makes one guide source of the copies one URL had, linked to
 * their sources, with the other copies' keys as aliases. Automatic guides and a gone source's row
 * stay apart. Nothing is queued for live sync. On the JVM under Robolectric, against the exported
 * schemas (`core/network/schemas`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDatabaseMigration19To20Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SettingsDatabase::class.java)

    private fun provider(
        id: Int,
        type: String = "M3U",
        url: String = "http://p$id.test",
    ) = "INSERT INTO `providers` (`id`, `name`, `url`, `username`, `type`, `config`, `providerSettings`, `createdAt`, " +
        "`lastUsedAt`, `isActive`, `lastSyncedAtMs`, `lastSyncDurationMs`, `lastSyncError`, `lastSyncInserted`, " +
        "`lastSyncUpdated`, `lastSyncDeleted`, `providerKey`) VALUES ($id, 'P$id', '$url', 'me', '$type', '', " +
        "'{}', 0, 0, 0, 0, 0, NULL, 0, 0, 0, 'prov-$id')"

    private fun source(
        id: Int,
        providerId: Int,
        key: String,
        url: String = "http://epg.test/guide.xml",
        label: String = "G$id",
        offset: Int = 0,
        ingested: Long = 0,
        etag: String = "etag-$id",
    ) = "INSERT INTO `epg_source` (`id`, `url`, `label`, `timezone_offset_hours`, `added_at_ms`, `last_ingested_at_ms`, " +
        "`enabled`, `provider_id`, `source_key`, `etag`, `refresh_interval_hours`) " +
        "VALUES ($id, '$url', '$label', $offset, $id, $ingested, 1, $providerId, '$key', '$etag', 24)"

    @Test
    fun `19 to 20 merges the copies of one guide and keeps the others apart`() {
        helper.createDatabase(TEST_DB, 19).apply {
            execSQL(provider(1))
            execSQL(provider(2))
            execSQL(provider(3, type = "XTREAM", url = "http://iptv.test"))
            execSQL(provider(4, type = "XTREAM", url = "http://iptv.test"))
            // One guide added for sources 1 and 2: row 11 has the newer import, row 10 the lower key.
            execSQL(source(10, 1, key = "a-key", ingested = 100))
            execSQL(source(11, 2, key = "b-key", url = "HTTP://EPG.TEST/guide.xml", ingested = 200))
            // Two accounts on one server: two automatic guides, never merged.
            execSQL(source(20, 3, key = "c-key", url = "http://iptv.test/xmltv.php", label = "iptv.test (Bulk)"))
            execSQL(source(21, 4, key = "d-key", url = "http://iptv.test/xmltv.php", label = "iptv.test (Bulk)"))
            // The guide of a source that is gone.
            execSQL(source(30, 9, key = "e-key"))
            execSQL("INSERT OR IGNORE INTO `sync_clock` (`id`, `hlc`, `applying`) VALUES (1, 0, 0)")
            // The live sync triggers an earlier open left: they must not fire for the migration.
            SettingsSyncTriggers.install(this)
            execSQL("DELETE FROM `sync_version`")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 20, true, SettingsDatabase.MIGRATION_19_20)

        db.query("SELECT `id`, `source_key`, `label`, `etag`, `last_ingested_at_ms` FROM `epg_source` ORDER BY `id`").use {
            val rows =
                generateSequence {
                    if (it.moveToNext()) listOf(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getLong(4)) else null
                }.toList()
            assertEquals(
                listOf(
                    // Row 11 keeps its id, stats and validators; the guide takes the lowest key and its row's label.
                    listOf(11L, "a-key", "G10", "etag-11", 200L),
                    listOf(20L, "c-key", "iptv.test (Bulk)", "etag-20", 0L),
                    listOf(21L, "d-key", "iptv.test (Bulk)", "etag-21", 0L),
                    listOf(30L, "e-key", "G30", "etag-30", 0L),
                ),
                rows,
            )
        }
        db.query("SELECT `source_id`, `provider_id` FROM `epg_source_provider` ORDER BY `source_id`, `linked_at_ms`").use {
            val links = generateSequence { if (it.moveToNext()) it.getLong(0) to it.getLong(1) else null }.toList()
            // The guide-key row's source (1) first; the gone source's guide has no link.
            assertEquals(listOf(11L to 1L, 11L to 2L, 20L to 3L, 21L to 4L), links)
        }
        db.query("SELECT `alias_key`, `source_id`, `provider_id` FROM `epg_source_alias`").use {
            it.moveToFirst()
            assertEquals(listOf<Any>("b-key", 11L, 2L), listOf(it.getString(0), it.getLong(1), it.getLong(2)))
            assertFalse(it.moveToNext())
        }
        db.query("SELECT COUNT(*) FROM `sync_version`").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM `sync_tombstone`").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "settings_migration_19_20_test.db"
    }
}
