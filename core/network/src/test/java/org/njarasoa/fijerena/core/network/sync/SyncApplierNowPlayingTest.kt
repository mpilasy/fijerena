package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.xtream.db.SyncVersionDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * A now-playing record goes to [NowPlayingStore] and nowhere else: no version, no tombstone, no
 * step of either sync clock. See docs/plans/20261001_live-sync-now-playing-plan.md → Phase 2.1.
 */
class SyncApplierNowPlayingTest {
    private val settingsSync = mockk<SettingsSyncDao>(relaxed = true)
    private val versions = mockk<SyncVersionDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
    private val xtreamDb = mockk<XtreamDatabase>(relaxed = true)

    @Before
    fun setup() {
        every { settingsDb.settingsSyncDao() } returns settingsSync
        every { xtreamDb.syncVersionDao() } returns versions
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { XtreamDatabase.getInstance(any()) } returns xtreamDb
        NowPlayingStore.clear()
    }

    @After
    fun tearDown() {
        NowPlayingStore.clear()
        unmockkAll()
    }

    @Test
    fun `a now-playing record writes to neither database`() =
        runBlocking {
            val payload =
                SyncPayloads.NowPlaying(
                    state = SyncPayloads.NowPlaying.PLAYING,
                    title = "Malcolm X",
                    profileName = "Kid",
                    sentAt = 1_000,
                )
            val record =
                SyncRecord(SyncKey(SyncKind.SHARED, "", SyncKind.NOW_PLAYING, "tv-1"), hlc = 42, payload = SyncPayloads.encode(payload))

            val result = SyncApplier(mockk<Context>(relaxed = true)).apply(listOf(record))

            assertEquals(1, result.applied)
            assertEquals(
                payload,
                NowPlayingStore.devices.value
                    .getValue("tv-1")
                    .nowPlaying,
            )
            verify { settingsSync wasNot io.mockk.Called }
            verify { versions wasNot io.mockk.Called }
        }
}
