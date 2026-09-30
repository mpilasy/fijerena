package org.njarasoa.fijerena.core.network.provider

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/**
 * [SettingsDatabase.MIGRATION_10_11] (the `profiles` table, seeded with `default`),
 * [SettingsDatabase.MIGRATION_11_12] (`profiles.colorIndex`) and [SettingsDatabase.MIGRATION_12_13]
 * (`providers.providerKey`, `sync_tombstone`), run against a database rolled back to v10: built
 * fresh at the current schema via Room, then what v11–v13 added removed — `profiles` and
 * `sync_tombstone` dropped, `providers` rebuilt with its exact v12 DDL. Reopening through Room with
 * only those migrations exercises Room's own schema validation; this database has no destructive
 * fallback, so a mismatch throws.
 *
 * Uses its own on-disk file (never the app's real `providers.db`), deleted before and after.
 */
@RunWith(AndroidJUnit4::class)
class SettingsDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDbName = "test_settings_migration_10_13.db"

    @Before
    fun deleteTestDbBefore() {
        context.deleteDatabase(testDbName)
    }

    @After
    fun deleteTestDbAfter() {
        context.deleteDatabase(testDbName)
    }

    @Test
    fun migration10To13_createsDefaultProfileKeysProvidersAndKeepsThem() {
        val seedDb = Room.databaseBuilder(context, SettingsDatabase::class.java, testDbName).build()
        val providerId =
            runBlocking {
                seedDb.providerDao().insertProvider(
                    ProviderEntity(name = "Test provider", url = "http://example.test", username = "user"),
                )
            }
        val otherProviderId =
            runBlocking {
                seedDb.providerDao().insertProvider(
                    ProviderEntity(name = "Other provider", url = "http://other.test", username = "user"),
                )
            }
        val rawDb = seedDb.openHelper.writableDatabase
        rawDb.execSQL("DROP TABLE `profiles`")
        rawDb.execSQL("DROP TABLE `sync_tombstone`")
        rawDb.execSQL("DROP INDEX `index_providers_providerKey`")
        rawDb.execSQL("ALTER TABLE `providers` RENAME TO `providers_v13`")
        rawDb.execSQL(
            "CREATE TABLE `providers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`url` TEXT NOT NULL, `username` TEXT NOT NULL, `type` TEXT NOT NULL, `config` TEXT NOT NULL, " +
                "`providerSettings` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, " +
                "`isActive` INTEGER NOT NULL, `lastSyncedAtMs` INTEGER NOT NULL, `lastSyncDurationMs` INTEGER NOT NULL, " +
                "`lastSyncError` TEXT, `lastSyncInserted` INTEGER NOT NULL, `lastSyncUpdated` INTEGER NOT NULL, " +
                "`lastSyncDeleted` INTEGER NOT NULL)",
        )
        val v12Columns =
            "`id`, `name`, `url`, `username`, `type`, `config`, `providerSettings`, `createdAt`, `lastUsedAt`, `isActive`, " +
                "`lastSyncedAtMs`, `lastSyncDurationMs`, `lastSyncError`, `lastSyncInserted`, `lastSyncUpdated`, `lastSyncDeleted`"
        rawDb.execSQL("INSERT INTO `providers` ($v12Columns) SELECT $v12Columns FROM `providers_v13`")
        rawDb.execSQL("DROP TABLE `providers_v13`")
        rawDb.execSQL("PRAGMA user_version = 10")
        seedDb.close()

        val migratedDb =
            Room.databaseBuilder(context, SettingsDatabase::class.java, testDbName)
                .addMigrations(SettingsDatabase.MIGRATION_10_11, SettingsDatabase.MIGRATION_11_12, SettingsDatabase.MIGRATION_12_13)
                .build()
        assertEquals(13, migratedDb.openHelper.writableDatabase.version)

        runBlocking {
            val profiles = migratedDb.profileDao().getAll()
            assertEquals(listOf(ProfileEntity.DEFAULT_ID), profiles.map { it.id })
            assertEquals(ProfileEntity.DEFAULT_NAME, profiles.single().name)
            assertEquals(0, profiles.single().colorIndex)
            val provider = migratedDb.providerDao().getProviderById(providerId)!!
            assertEquals("Test provider", provider.name)
            // Every existing provider got its own random providerKey.
            val other = migratedDb.providerDao().getProviderById(otherProviderId)!!
            assertEquals(36, provider.providerKey.length)
            assertNotEquals(provider.providerKey, other.providerKey)
            assertTrue(migratedDb.providerDao().getAllTombstones().isEmpty())
        }
        migratedDb.close()
    }
}
