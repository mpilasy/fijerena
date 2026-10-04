package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.EpgRefreshSchedule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SettingsDatabase.MIGRATION_15_16] adds `epg_source.refresh_interval_hours` as a nullable column
 * (null = not set), and the one-time copy of the retired device-wide interval fills only the rows
 * still unset, sending nothing to live sync. On the JVM under Robolectric, against the exported
 * schemas (`core/network/schemas`). See `docs/plans/archive/20261003_sources-guide-profiles-plan.md` → P5.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDatabaseMigration15To16Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SettingsDatabase::class.java)

    private fun createV15WithOneSource() {
        helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                "INSERT INTO `providers` (`id`, `name`, `url`, `username`, `type`, `config`, `providerSettings`, `createdAt`, " +
                    "`lastUsedAt`, `isActive`, `lastSyncedAtMs`, `lastSyncDurationMs`, `lastSyncInserted`, `lastSyncUpdated`, " +
                    "`lastSyncDeleted`, `providerKey`) VALUES (1, 'IPTV', 'http://iptv.test', 'me', 'XTREAM', '', '{}', 0, 0, 1, 0, 0, 0, 0, 0, 'prov-1')",
            )
            execSQL(
                "INSERT INTO `epg_source` (`id`, `url`, `label`, `timezone_offset_hours`, `added_at_ms`, `last_ingested_at_ms`, " +
                    "`enabled`, `provider_id`, `source_key`) VALUES (1, 'http://epg.test/old.xml', 'Old', 2, 100, 200, 1, 1, 'src-old')",
            )
            close()
        }
    }

    @Test
    fun `15 to 16 adds the interval as not set and keeps every row`() {
        createV15WithOneSource()

        val db = helper.runMigrationsAndValidate(TEST_DB, 16, true, SettingsDatabase.MIGRATION_15_16)

        db.query("SELECT `url`, `label`, `timezone_offset_hours`, `source_key`, `refresh_interval_hours` FROM `epg_source`").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("http://epg.test/old.xml", it.getString(0))
            assertEquals("Old", it.getString(1))
            assertEquals(2, it.getInt(2))
            assertEquals("src-old", it.getString(3))
            assertEquals(true, it.isNull(4))
        }
        db.close()
    }

    @Test
    fun `the copy fills only rows without an interval of their own, once, and queues nothing for sync`() {
        createV15WithOneSource()
        helper.runMigrationsAndValidate(TEST_DB, 16, true, SettingsDatabase.MIGRATION_15_16).close()
        val db =
            Room
                .databaseBuilder(context, SettingsDatabase::class.java, TEST_DB)
                .addMigrations(SettingsDatabase.MIGRATION_15_16)
                .allowMainThreadQueries()
                .build()
        SettingsSyncTriggers.install(db.openHelper.writableDatabase)
        // A row a device on the new version already gave its own interval (received by sync).
        val ownId =
            runBlocking {
                db.epgSourceDao().insertSource(
                    EpgSourceEntity(url = "http://epg.test/own.xml", providerId = 1, refreshIntervalHours = 6),
                )
            }
        val pendingBefore = runBlocking { db.settingsSyncDao().getPending(100).size }
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs
            .edit()
            .putBoolean("epg_auto_refresh", true)
            .putInt("epg_refresh_interval", 12)
            .commit()
        val settings = AppSettings(context)

        runBlocking { EpgRefreshSchedule.copyLegacyIntervalOnce(settings) { EpgRefreshSchedule.fillUnsetIntervals(db, it) } }

        runBlocking {
            assertEquals(12, db.epgSourceDao().getSourceById(1)!!.refreshIntervalHours)
            assertEquals(6, db.epgSourceDao().getSourceById(ownId)!!.refreshIntervalHours)
            assertEquals(pendingBefore, db.settingsSyncDao().getPending(100).size)
        }

        // Once only: a later start with another device-wide value and an unset row changes nothing.
        prefs.edit().putInt("epg_refresh_interval", 48).commit()
        val unsetId =
            runBlocking {
                db.epgSourceDao().insertSource(
                    EpgSourceEntity(url = "http://epg.test/unset.xml", providerId = 1, refreshIntervalHours = null),
                )
            }
        runBlocking { EpgRefreshSchedule.copyLegacyIntervalOnce(settings) { EpgRefreshSchedule.fillUnsetIntervals(db, it) } }
        runBlocking {
            assertEquals(12, db.epgSourceDao().getSourceById(1)!!.refreshIntervalHours)
            assertNull(db.epgSourceDao().getSourceById(unsetId)!!.refreshIntervalHours)
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "settings_migration_15_16_test.db"
    }
}
