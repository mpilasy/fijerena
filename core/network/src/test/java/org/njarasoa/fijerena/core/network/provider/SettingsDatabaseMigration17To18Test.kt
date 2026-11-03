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
 * [SettingsDatabase.MIGRATION_17_18] adds the failure time and the failure count to sources and
 * guide sources. A row with an error gets a count of 1 and a failure time (a source's last sync
 * time, a guide source's migration time); a row without one gets null and 0. A source's
 * `lastSyncedAtMs` stays as it was, and nothing is queued for live sync. On the JVM under
 * Robolectric, against the exported schemas (`core/network/schemas`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDatabaseMigration17To18Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SettingsDatabase::class.java)

    private fun provider(
        id: Int,
        lastSyncedAtMs: Long,
        error: String?,
    ) = "INSERT INTO `providers` (`id`, `name`, `url`, `username`, `type`, `config`, `providerSettings`, `createdAt`, " +
        "`lastUsedAt`, `isActive`, `lastSyncedAtMs`, `lastSyncDurationMs`, `lastSyncError`, `lastSyncInserted`, " +
        "`lastSyncUpdated`, `lastSyncDeleted`, `providerKey`) VALUES ($id, 'P$id', 'http://p$id.test', 'me', 'XTREAM', '', " +
        "'{}', 0, 0, 0, $lastSyncedAtMs, 7, ${error?.let { "'$it'" } ?: "NULL"}, 0, 0, 0, 'prov-$id')"

    private fun source(
        id: Int,
        error: String?,
    ) = "INSERT INTO `epg_source` (`id`, `url`, `label`, `timezone_offset_hours`, `added_at_ms`, `last_ingested_at_ms`, " +
        "`last_error`, `enabled`, `provider_id`, `source_key`) " +
        "VALUES ($id, 'http://epg.test/$id.xml', 'G$id', 0, $id, 500, ${error?.let { "'$it'" } ?: "NULL"}, 1, 1, 'src-$id')"

    @Test
    fun `17 to 18 counts one failure on rows with an error and keeps the last sync time`() {
        helper.createDatabase(TEST_DB, 17).apply {
            execSQL(provider(1, 1_000, "boom"))
            execSQL(provider(2, 2_000, null))
            execSQL(source(1, "gone"))
            execSQL(source(2, null))
            execSQL("INSERT OR IGNORE INTO `sync_clock` (`id`, `hlc`, `applying`) VALUES (1, 0, 0)")
            // The live sync triggers an earlier open left: they must not fire for the migration.
            SettingsSyncTriggers.install(this)
            execSQL("DELETE FROM `sync_version`")
            close()
        }
        val before = System.currentTimeMillis()

        val db = helper.runMigrationsAndValidate(TEST_DB, 18, true, SettingsDatabase.MIGRATION_17_18)

        db
            .query(
                "SELECT `id`, `lastSyncedAtMs`, `lastSyncDurationMs`, `lastFailedAtMs`, `failuresSinceSuccess` FROM `providers` ORDER BY `id`",
            ).use { cursor ->
                cursor.moveToNext()
                assertEquals(1_000L, cursor.getLong(1))
                assertEquals(7L, cursor.getLong(2))
                assertEquals(1_000L, cursor.getLong(3))
                assertEquals(1, cursor.getInt(4))
                cursor.moveToNext()
                assertEquals(2_000L, cursor.getLong(1))
                assertTrue(cursor.isNull(3))
                assertEquals(0, cursor.getInt(4))
            }
        db.query("SELECT `id`, `last_ingested_at_ms`, `last_failed_at_ms`, `failures_since_success` FROM `epg_source` ORDER BY `id`").use {
            it.moveToNext()
            assertEquals(500L, it.getLong(1))
            assertTrue(it.getLong(2) >= before)
            assertEquals(1, it.getInt(3))
            it.moveToNext()
            assertTrue(it.isNull(2))
            assertEquals(0, it.getInt(3))
        }
        db.query("SELECT COUNT(*) FROM `sync_version`").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "settings_migration_17_18_test.db"
    }
}
