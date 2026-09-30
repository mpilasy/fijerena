package org.njarasoa.fijerena.core.network.provider

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/**
 * [SettingsDatabase.MIGRATION_10_11] (the `profiles` table, seeded with `default`) and
 * [SettingsDatabase.MIGRATION_11_12] (`profiles.colorIndex`), run against a database rolled back
 * to v10: built fresh at the current schema via Room, then the one thing v11/v12 added — the
 * `profiles` table — dropped. Reopening through Room with only those migrations exercises Room's
 * own schema validation; this database has no destructive fallback, so a mismatch throws.
 *
 * Uses its own on-disk file (never the app's real `providers.db`), deleted before and after.
 */
@RunWith(AndroidJUnit4::class)
class SettingsDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDbName = "test_settings_migration_10_12.db"

    @Before
    fun deleteTestDbBefore() {
        context.deleteDatabase(testDbName)
    }

    @After
    fun deleteTestDbAfter() {
        context.deleteDatabase(testDbName)
    }

    @Test
    fun migration10To12_createsDefaultProfileAndKeepsProviders() {
        val seedDb = Room.databaseBuilder(context, SettingsDatabase::class.java, testDbName).build()
        val providerId =
            runBlocking {
                seedDb.providerDao().insertProvider(
                    ProviderEntity(name = "Test provider", url = "http://example.test", username = "user"),
                )
            }
        val rawDb = seedDb.openHelper.writableDatabase
        rawDb.execSQL("DROP TABLE `profiles`")
        rawDb.execSQL("PRAGMA user_version = 10")
        seedDb.close()

        val migratedDb =
            Room.databaseBuilder(context, SettingsDatabase::class.java, testDbName)
                .addMigrations(SettingsDatabase.MIGRATION_10_11, SettingsDatabase.MIGRATION_11_12)
                .build()
        assertEquals(12, migratedDb.openHelper.writableDatabase.version)

        runBlocking {
            val profiles = migratedDb.profileDao().getAll()
            assertEquals(listOf(ProfileEntity.DEFAULT_ID), profiles.map { it.id })
            assertEquals(ProfileEntity.DEFAULT_NAME, profiles.single().name)
            assertEquals(0, profiles.single().colorIndex)
            assertEquals("Test provider", migratedDb.providerDao().getProviderById(providerId)?.name)
        }
        migratedDb.close()
    }
}
