package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.xtream.db.SyncVersionDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * F-08: one record that throws while being applied — a payload shape from another app version, a
 * provider deleted here mid-apply — used to abort the whole pull before the page's cursor was
 * saved, so every later pass refetched it and failed again: sync stuck for good, no error shown.
 * Now such a record is deferred (retried every pass, so a newer app version can still apply it)
 * and the rest of the batch goes on. See
 * docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-08.
 */
class SyncApplierPoisonPillTest {
    private val sync = mockk<SettingsSyncDao>(relaxed = true)
    private val versions = mockk<SyncVersionDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
    private val xtreamDb = mockk<XtreamDatabase>(relaxed = true)
    private val provider = ProviderEntity(id = 7, name = "IPTV", url = "http://iptv.test", username = "me", providerKey = "prov-1")

    @Before
    fun setup() {
        every { settingsDb.settingsSyncDao() } returns sync
        every { xtreamDb.syncVersionDao() } returns versions
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { XtreamDatabase.getInstance(any()) } returns xtreamDb
        coEvery { sync.get(any(), any(), any()) } returns null
        coEvery { sync.getTombstone(any(), any()) } returns null
        NowPlayingStore.clear()
    }

    @After
    fun tearDown() {
        NowPlayingStore.clear()
        unmockkAll()
    }

    private fun applier() = SyncApplier(mockk<Context>(relaxed = true), thisDeviceId = { "this-device" }, playingSessionId = { null })

    private val nowPlaying =
        SyncRecord(
            SyncKey(SyncKind.SHARED, "", SyncKind.NOW_PLAYING, "tv-1"),
            hlc = 40,
            payload =
                SyncPayloads.encode(
                    SyncPayloads.NowPlaying(state = SyncPayloads.NowPlaying.PLAYING, title = "News", profileName = "Kid", sentAt = 1),
                ),
        )

    @Test
    fun `an undecodable payload waits, and the rest of the batch still applies`() =
        runBlocking {
            // The shape the bug was reproduced with: a setting record whose payload is `{}`.
            val unreadable = SyncRecord(SyncKey(SyncKind.SHARED, "", SyncKind.SETTING, "theme"), hlc = 50, payload = "{}")

            val result = applier().apply(listOf(unreadable, nowPlaying))

            assertEquals(listOf(unreadable), result.deferred)
            assertEquals(1, result.applied)
            assertEquals(setOf("tv-1"), NowPlayingStore.devices.value.keys)
            // The pass went on to the end: the clock still took the newest received value.
            coVerify { sync.receive(50L) }
        }

    @Test
    fun `a provider deleted here between the presence check and the write defers the record`() =
        runBlocking {
            // Present for the presence check, gone by the time the record is written.
            coEvery { sync.providerByKey("prov-1") } returnsMany listOf(provider, null)
            val filters = SyncRecord(SyncKey(SyncKind.SHARED, "prov-1", SyncKind.CATEGORY_FILTERS), hlc = 60, payload = "{}")

            val result = applier().apply(listOf(filters))

            assertEquals(listOf(filters), result.deferred)
            assertEquals(0, result.applied)
        }

    @Test
    fun `an EPG source whose provider vanished mid-apply is deferred, not thrown`() =
        runBlocking {
            coEvery { sync.providerByKey("prov-1") } returnsMany listOf(provider, null)
            val payload =
                SyncPayloads.EpgSource(
                    providerKey = "prov-1",
                    url = "http://epg.test/guide.xml",
                    label = "Guide",
                    timezoneOffsetHours = 0,
                    enabled = true,
                )
            val source =
                SyncRecord(SyncKey(SyncKind.SHARED, "", SyncKind.EPG_SOURCE, "src-1"), hlc = 70, payload = SyncPayloads.encode(payload))

            val result = applier().apply(listOf(source, nowPlaying))

            assertEquals(listOf(source), result.deferred)
            assertEquals(1, result.applied)
        }

    @Test
    fun `a database failure on one record defers only that record`() =
        runBlocking {
            coEvery { sync.providerByKey("prov-1") } throws IllegalStateException("database closed")
            val favorite =
                SyncRecord(
                    SyncKey("profile", "prov-1", SyncKind.FAVORITE_STREAM, "m1", "MOVIES"),
                    hlc = 80,
                    payload = """{"name":"Film","createdAt":1}""",
                )

            val result = applier().apply(listOf(favorite, nowPlaying))

            assertEquals(listOf(favorite), result.deferred)
            assertEquals(1, result.applied)
        }
}
