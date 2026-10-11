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
 * [SettingsDatabase.MIGRATION_16_17] drops the login from each Xtream source's automatic guide
 * source and leaves every other URL as it is, queueing nothing for live sync. On the JVM under
 * Robolectric, against the exported schemas (`core/network/schemas`). See
 * `docs/plans/20261011_public-release-fdroid-readiness-plan.md` → R1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDatabaseMigration16To17Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SettingsDatabase::class.java)

    private fun provider(
        id: Int,
        url: String,
        type: String,
    ) = "INSERT INTO `providers` (`id`, `name`, `url`, `username`, `type`, `config`, `providerSettings`, `createdAt`, " +
        "`lastUsedAt`, `isActive`, `lastSyncedAtMs`, `lastSyncDurationMs`, `lastSyncInserted`, `lastSyncUpdated`, " +
        "`lastSyncDeleted`, `providerKey`) VALUES ($id, 'P$id', '$url', 'me', '$type', '', '{}', 0, 0, 0, 0, 0, 0, 0, 0, 'prov-$id')"

    private fun source(
        id: Int,
        url: String,
        label: String,
        providerId: Int,
    ) = "INSERT INTO `epg_source` (`id`, `url`, `label`, `timezone_offset_hours`, `added_at_ms`, `last_ingested_at_ms`, " +
        "`enabled`, `provider_id`, `source_key`, `last_channels`, `etag`) " +
        "VALUES ($id, '$url', '$label', 0, $id, 500, 1, $providerId, 'src-$id', 120, 'e$id')"

    @Test
    fun `16 to 17 drops the login from automatic guide sources only`() {
        helper.createDatabase(TEST_DB, 16).apply {
            execSQL(provider(1, "http://iptv.test:8080/", "XTREAM"))
            execSQL(provider(2, "http://jelly.test", "JELLYFIN"))
            execSQL("INSERT OR IGNORE INTO `sync_clock` (`id`, `hlc`, `applying`) VALUES (1, 0, 0)")
            // The live sync trigger an earlier open left: it must not fire for the migration.
            SettingsSyncTriggers.install(this)
            execSQL(source(1, "http://iptv.test:8080/xmltv.php?username=me&password=p#%z w", "iptv.test (Bulk)", 1))
            // Renamed by the viewer: hand-added, kept.
            execSQL(source(2, "http://iptv.test:8080/xmltv.php?username=me&password=pw", "My guide", 1))
            execSQL(source(3, "http://epg.example/guide.xml?token=abc", "guide", 1))
            execSQL(source(4, "http://jelly.test/xmltv.php?username=me&password=pw", "jelly.test (Bulk)", 2))
            execSQL("DELETE FROM `sync_version`")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true, SettingsDatabase.MIGRATION_16_17)

        val rows =
            db.query("SELECT `id`, `url`, `last_channels`, `etag` FROM `epg_source` ORDER BY `id`").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(listOf(cursor.getLong(0), cursor.getString(1), cursor.getInt(2), cursor.getString(3)))
                }
            }
        assertEquals(
            listOf(
                listOf(1L, "http://iptv.test:8080/xmltv.php", 120, "e1"),
                listOf(2L, "http://iptv.test:8080/xmltv.php?username=me&password=pw", 120, "e2"),
                listOf(3L, "http://epg.example/guide.xml?token=abc", 120, "e3"),
                listOf(4L, "http://jelly.test/xmltv.php?username=me&password=pw", 120, "e4"),
            ),
            rows,
        )
        db.query("SELECT COUNT(*) FROM `sync_version`").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT `applying` FROM `sync_clock`").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "settings_migration_16_17_test.db"
    }
}
