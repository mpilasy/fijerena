package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileDao
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.sports.FollowedSports
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.network.xtream.db.FollowedSportEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSyncTriggers
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A followed team or league travels between devices: this device sends a follow as a record and an
 * unfollow as a tombstone ([LocalRecords]); a received one is written without a provider, deleted
 * with a tombstone, never queued back, and an older record never overwrites a newer local change
 * ([SyncApplier]). Against real in-memory `xtream_v2.db`s, one per device. See the sports teams
 * plan, Phase 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncFollowedSportTest {
    private val sync = mockk<SettingsSyncDao>(relaxed = true)
    private val profiles = mockk<ProfileDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
    private lateinit var sender: XtreamDatabase
    private lateinit var receiver: XtreamDatabase

    private val prefsFiles = mutableMapOf<String, SharedPreferences>()
    private val context =
        mockk<Context>(relaxed = true).also { context ->
            every { context.getSharedPreferences(any(), any()) } answers { prefsFiles.getOrPut(firstArg()) { FakeSharedPreferences() } }
        }

    private fun database() =
        Room
            .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) = XtreamSyncTriggers.install(db)
                },
            ).build()

    @Before
    fun setup() {
        sender = database()
        receiver = database()
        every { settingsDb.settingsSyncDao() } returns sync
        every { settingsDb.profileDao() } returns profiles
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { XtreamDatabase.getInstance(any()) } returns receiver
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction(any<suspend () -> Any?>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as suspend () -> Any?).invoke()
        }
        coEvery { sync.getPending(any()) } returns emptyList()
        coEvery { sync.getTombstone(any(), any()) } returns null
        coEvery { profiles.exists(ProfileEntity.DEFAULT_ID) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
        sender.close()
        receiver.close()
    }

    private val arsenal =
        SportsTeam(SportsSource.ESPN, "359", "Arsenal", "ARS", listOf("Gunners"), "soccer", "soccer/eng.1", logoUrl = "https://a/359.png")

    private val key = SyncKey(ProfileEntity.DEFAULT_ID, "", SyncKind.FOLLOWED_SPORT, "359", "TEAM:espn")

    private fun applier() = SyncApplier(context, thisDeviceId = { "this-device" }, playingSessionId = { null })

    private fun upsert(hlc: Long) =
        SyncRecord(
            key,
            hlc,
            payload =
                SyncPayloads.encode(
                    SyncPayloads.FollowedSport("soccer/eng.1", "Arsenal", "ARS", listOf("Gunners"), "soccer", "https://a/359.png", 5),
                ),
        )

    /** This device's pending records, read from [sender]. */
    private suspend fun sent(): List<SyncRecord> {
        every { XtreamDatabase.getInstance(any()) } returns sender
        val records = LocalRecords(context).pending(100).map { it.record }
        every { XtreamDatabase.getInstance(any()) } returns receiver
        return records
    }

    @Test
    fun `a follow and an unfollow reach the other device`() =
        runBlocking {
            FollowedSports(sender, now = { 5 }).follow(ProfileEntity.DEFAULT_ID, arsenal)
            val follow = sent().single()
            assertEquals(key, follow.key)
            assertEquals(1, applier().apply(listOf(follow)).applied)
            assertEquals(listOf(arsenal), FollowedSports(receiver).get(ProfileEntity.DEFAULT_ID).teams)

            sender.openHelper.writableDatabase.execSQL("UPDATE sync_version SET pending = 0")
            FollowedSports(sender).unfollow(ProfileEntity.DEFAULT_ID, arsenal)
            val unfollow = sent().single()
            assertTrue(unfollow.deleted)
            assertEquals(1, applier().apply(listOf(unfollow)).applied)
            assertTrue(FollowedSports(receiver).get(ProfileEntity.DEFAULT_ID).isEmpty)
        }

    @Test
    fun `a received follow is stored and not queued back`() =
        runBlocking {
            assertEquals(1, applier().apply(listOf(upsert(hlc = 10))).applied)

            assertEquals(
                FollowedSportEntity(
                    ProfileEntity.DEFAULT_ID,
                    "TEAM",
                    "espn",
                    "359",
                    "soccer/eng.1",
                    "Arsenal",
                    "ARS",
                    FollowedSportEntity.encodeAltNames(listOf("Gunners")),
                    "soccer",
                    "https://a/359.png",
                    5,
                ),
                receiver.followedSportDao().get(ProfileEntity.DEFAULT_ID, "TEAM", "espn", "359"),
            )
            val version =
                receiver.syncVersionDao().get(
                    SyncKind.FOLLOWED_SPORT_PROVIDER_ID,
                    ProfileEntity.DEFAULT_ID,
                    SyncKind.FOLLOWED_SPORT,
                    "359",
                    "TEAM:espn",
                )
            assertEquals(10L, version!!.hlc)
            assertEquals(false, version.pending)
        }

    @Test
    fun `a received unfollow deletes the item and keeps a tombstone`() =
        runBlocking {
            applier().apply(listOf(upsert(hlc = 10)))

            applier().apply(listOf(SyncRecord(key, 20, deleted = true)))

            assertNull(receiver.followedSportDao().get(ProfileEntity.DEFAULT_ID, "TEAM", "espn", "359"))
            val tombstone =
                receiver.syncTombstoneDao().get(
                    SyncKind.FOLLOWED_SPORT_PROVIDER_ID,
                    ProfileEntity.DEFAULT_ID,
                    SyncKind.FOLLOWED_SPORT,
                    "359",
                    "TEAM:espn",
                )
            assertNotNull(tombstone)
            assertEquals(20L, tombstone!!.deletedAt)
        }

    @Test
    fun `an older record does not overwrite a newer one`() =
        runBlocking {
            applier().apply(listOf(upsert(hlc = 10)))

            assertEquals(0, applier().apply(listOf(SyncRecord(key, 9, deleted = true))).applied)

            assertNotNull(receiver.followedSportDao().get(ProfileEntity.DEFAULT_ID, "TEAM", "espn", "359"))
        }

    @Test
    fun `an item of an unknown profile waits`() =
        runBlocking {
            val other = SyncRecord(key.copy(profileKey = "later"), 10, payload = upsert(10).payload)

            assertEquals(1, applier().apply(listOf(other)).deferred.size)
        }

    @Test
    fun `a record whose content type is not kind and source is skipped`() =
        runBlocking {
            val bad = SyncRecord(key.copy(contentType = "MOVIES"), 10, payload = upsert(10).payload)

            val result = applier().apply(listOf(bad))

            assertEquals(0, result.applied)
            assertEquals(1, result.skipped)
        }
}
