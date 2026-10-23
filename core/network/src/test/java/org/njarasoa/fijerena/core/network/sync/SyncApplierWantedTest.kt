package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileDao
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.xtream.db.WantedTitleEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Wanted title received from another device is written without a provider, deleted with a
 * tombstone, never queued back, and an older record never overwrites a newer local change. Against
 * a real in-memory `xtream_v2.db`. See the wanted titles plan, Phase 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncApplierWantedTest {
    private val sync = mockk<SettingsSyncDao>(relaxed = true)
    private val profiles = mockk<ProfileDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
    private lateinit var xtreamDb: XtreamDatabase

    private val prefsFiles = mutableMapOf<String, SharedPreferences>()
    private val context =
        mockk<Context>(relaxed = true).also { context ->
            every { context.getSharedPreferences(any(), any()) } answers { prefsFiles.getOrPut(firstArg()) { FakeSharedPreferences() } }
        }

    @Before
    fun setup() {
        xtreamDb =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        every { settingsDb.settingsSyncDao() } returns sync
        every { settingsDb.profileDao() } returns profiles
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { XtreamDatabase.getInstance(any()) } returns xtreamDb
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction(any<suspend () -> Any?>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as suspend () -> Any?).invoke()
        }
        coEvery { sync.getTombstone(any(), any()) } returns null
        coEvery { profiles.exists(ProfileEntity.DEFAULT_ID) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
        xtreamDb.close()
    }

    private fun applier() = SyncApplier(context, thisDeviceId = { "this-device" }, playingSessionId = { null })

    private val key = SyncKey(ProfileEntity.DEFAULT_ID, "", SyncKind.WANTED, "438631", ContentType.MOVIES)

    private fun upsert(hlc: Long) =
        SyncRecord(key, hlc, payload = SyncPayloads.encode(SyncPayloads.Wanted("Dune", "Dune", 2021, "/p.jpg", addedAt = 5, seenAt = 7)))

    @Test
    fun `a received title is stored and not queued back`() =
        runBlocking {
            assertEquals(1, applier().apply(listOf(upsert(hlc = 10))).applied)

            val row = xtreamDb.wantedTitleDao().get(ProfileEntity.DEFAULT_ID, ContentType.MOVIES, "438631")
            assertEquals(
                WantedTitleEntity(ProfileEntity.DEFAULT_ID, ContentType.MOVIES, "438631", "Dune", "Dune", 2021, "/p.jpg", 5, null, 7),
                row,
            )
            val version =
                xtreamDb.syncVersionDao().get(
                    SyncKind.WANTED_PROVIDER_ID,
                    ProfileEntity.DEFAULT_ID,
                    SyncKind.WANTED,
                    "438631",
                    ContentType.MOVIES,
                )
            assertEquals(10L, version!!.hlc)
            assertEquals(false, version.pending)
        }

    @Test
    fun `a received removal deletes the title and keeps a tombstone`() =
        runBlocking {
            applier().apply(listOf(upsert(hlc = 10)))

            applier().apply(listOf(SyncRecord(key, 20, deleted = true)))

            assertNull(xtreamDb.wantedTitleDao().get(ProfileEntity.DEFAULT_ID, ContentType.MOVIES, "438631"))
            val tombstone =
                xtreamDb.syncTombstoneDao().get(
                    SyncKind.WANTED_PROVIDER_ID,
                    ProfileEntity.DEFAULT_ID,
                    SyncKind.WANTED,
                    "438631",
                    ContentType.MOVIES,
                )
            assertNotNull(tombstone)
            assertEquals(20L, tombstone!!.deletedAt)
        }

    @Test
    fun `an older record does not overwrite a newer one`() =
        runBlocking {
            applier().apply(listOf(upsert(hlc = 10)))

            assertEquals(0, applier().apply(listOf(SyncRecord(key, 9, deleted = true))).applied)

            assertNotNull(xtreamDb.wantedTitleDao().get(ProfileEntity.DEFAULT_ID, ContentType.MOVIES, "438631"))
        }

    @Test
    fun `a received title keeps this device's release data`() =
        runBlocking {
            applier().apply(listOf(upsert(hlc = 10)))
            val stored = xtreamDb.wantedTitleDao().get(ProfileEntity.DEFAULT_ID, ContentType.MOVIES, "438631")!!
            xtreamDb.wantedTitleDao().upsert(stored.copy(releaseDate = "2021-09-15", releaseCheckedAt = 99))

            applier().apply(listOf(upsert(hlc = 20)))

            val row = xtreamDb.wantedTitleDao().get(ProfileEntity.DEFAULT_ID, ContentType.MOVIES, "438631")!!
            assertEquals("2021-09-15", row.releaseDate)
            assertEquals(99L, row.releaseCheckedAt)
        }

    @Test
    fun `a title of an unknown profile waits`() =
        runBlocking {
            val other = SyncRecord(key.copy(profileKey = "later"), 10, payload = upsert(10).payload)

            assertEquals(1, applier().apply(listOf(other)).deferred.size)
        }
}
