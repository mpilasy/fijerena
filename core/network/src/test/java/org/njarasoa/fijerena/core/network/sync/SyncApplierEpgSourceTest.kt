package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * A guide source's own refresh interval travels with its record; a record without one (an older
 * version) keeps this device's value. See the sources guide profiles plan → P5. The on/off state
 * stays each device's own.
 */
class SyncApplierEpgSourceTest {
    private val sync = mockk<SettingsSyncDao>(relaxed = true)
    private val sources = mockk<EpgSourceDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
    private val provider = ProviderEntity(id = 7, name = "IPTV", url = "http://iptv.test", username = "me", providerKey = "prov-1")
    private val local =
        EpgSourceEntity(id = 3, url = "http://epg.test/a.xml", label = "A", sourceKey = "src-1", refreshIntervalHours = 6)

    @Before
    fun setup() {
        every { settingsDb.settingsSyncDao() } returns sync
        every { settingsDb.epgSourceDao() } returns sources
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { XtreamDatabase.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction(any<suspend () -> Any?>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as suspend () -> Any?).invoke()
        }
        coEvery { sync.get(any(), any(), any()) } returns null
        coEvery { sync.getTombstone(any(), any()) } returns null
        coEvery { sync.providerByKey("prov-1") } returns provider
        coEvery { sync.sourceByKey("src-1") } returns local
        coEvery { sync.providerById(7) } returns provider
        coEvery { sync.autoGuideOf(any()) } returns null
        coEvery { sources.getAlias(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun applier() = SyncApplier(mockk<Context>(relaxed = true), thisDeviceId = { "this-device" }, playingSessionId = { null })

    private fun record(payload: String) =
        SyncRecord(SyncKey(SyncKind.SHARED, "", SyncKind.EPG_SOURCE, "src-1"), hlc = 10, payload = payload)

    private fun applyAndCaptureUpdate(payload: String): EpgSourceEntity {
        val updated = slot<EpgSourceEntity>()
        coEvery { sources.updateSource(capture(updated)) } returns Unit
        runBlocking { assertEquals(1, applier().apply(listOf(record(payload))).applied) }
        return updated.captured
    }

    @Test
    fun `a record carrying an interval sets it`() {
        val updated = applyAndCaptureUpdate(SyncPayloads.encode(SyncPayloads.EpgSource("prov-1", local.url, "B", 0, true, 12)))

        assertEquals(12, updated.refreshIntervalHours)
        assertEquals("B", updated.label)
    }

    @Test
    fun `a record from an older version, without the field, keeps the local interval`() {
        val legacy = """{"providerKey":"prov-1","url":"http://epg.test/a.xml","label":"B","timezoneOffsetHours":0,"enabled":false}"""

        val updated = applyAndCaptureUpdate(legacy)

        assertEquals(6, updated.refreshIntervalHours)
    }

    @Test
    fun `a received record keeps this device's on or off state`() {
        coEvery { sync.sourceByKey("src-1") } returns local.copy(enabled = false)
        val turnedOn = applyAndCaptureUpdate(SyncPayloads.encode(SyncPayloads.EpgSource("prov-1", local.url, "B", 0, true, 12)))
        assertFalse(turnedOn.enabled)
        assertEquals("B", turnedOn.label)

        coEvery { sync.sourceByKey("src-1") } returns local.copy(enabled = true)
        val turnedOff = applyAndCaptureUpdate(SyncPayloads.encode(SyncPayloads.EpgSource("prov-1", local.url, "B", 0, false, 12)))
        assertTrue(turnedOff.enabled)
    }

    @Test
    fun `a new source arrives on or off as the sender has it`() {
        coEvery { sync.sourceByKey("src-1") } returns null
        coEvery { sync.guidesAtUrl(any()) } returns emptyList()
        val inserted = slot<EpgSourceEntity>()
        coEvery { sources.insertGuide(capture(inserted), listOf(7L)) } returns 1L

        val turnedOff = SyncPayloads.encode(SyncPayloads.EpgSource("prov-1", "http://epg.test/new.xml", "N", 0, false))

        runBlocking { applier().apply(listOf(record(turnedOff))) }

        assertFalse(inserted.captured.enabled)
    }

    @Test
    fun `an interval this version can't use keeps the local one`() {
        val updated = applyAndCaptureUpdate(SyncPayloads.encode(SyncPayloads.EpgSource("prov-1", local.url, "A", 0, true, 0)))

        assertEquals(6, updated.refreshIntervalHours)
    }

    @Test
    fun `a new source received without an interval is stored as not set`() {
        coEvery { sync.sourceByKey("src-1") } returns null
        coEvery { sync.guidesAtUrl(any()) } returns emptyList()
        val inserted = slot<EpgSourceEntity>()
        coEvery { sources.insertGuide(capture(inserted), listOf(7L)) } returns 1L
        val legacy = """{"providerKey":"prov-1","url":"http://epg.test/new.xml","label":"N","timezoneOffsetHours":0,"enabled":true}"""

        runBlocking { applier().apply(listOf(record(legacy))) }

        coVerify { sources.insertGuide(any(), listOf(7L)) }
        assertNull(inserted.captured.refreshIntervalHours)
    }

    @Test
    fun `the payload decoder takes the new field and ignores fields it doesn't know`() {
        val newer =
            """{"providerKey":"prov-1","url":"u","label":"l","timezoneOffsetHours":1,"enabled":true,"refreshIntervalHours":168,"someFutureField":"x"}"""

        val decoded = SyncPayloads.decode<SyncPayloads.EpgSource>(newer)

        assertEquals(168, decoded.refreshIntervalHours)
        assertEquals(1, decoded.timezoneOffsetHours)
    }

    @Test
    fun `the payload decoder reads an older record without the field as not set`() {
        val older = """{"providerKey":"prov-1","url":"u","label":"l","timezoneOffsetHours":1,"enabled":true}"""

        assertNull(SyncPayloads.decode<SyncPayloads.EpgSource>(older).refreshIntervalHours)
    }

    @Test
    fun `an automatic guide source received with its login is stored without it`() {
        val withLogin = "http://iptv.test/xmltv.php?username=me&password=secret"

        val updated = applyAndCaptureUpdate(SyncPayloads.encode(SyncPayloads.EpgSource("prov-1", withLogin, "iptv.test (Bulk)", 0, true)))

        assertEquals("http://iptv.test/xmltv.php", updated.url)
    }

    @Test
    fun `a new automatic guide source is matched and inserted without its login`() {
        coEvery { sync.sourceByKey("src-1") } returns null
        val inserted = slot<EpgSourceEntity>()
        coEvery { sources.insertGuide(capture(inserted), listOf(7L)) } returns 1L
        val withLogin = "http://iptv.test/xmltv.php?username=me&password=secret"

        runBlocking {
            applier().apply(listOf(record(SyncPayloads.encode(SyncPayloads.EpgSource("prov-1", withLogin, "iptv.test (Bulk)", 0, true)))))
        }

        coVerify { sync.autoGuideOf(7) }
        assertEquals("http://iptv.test/xmltv.php", inserted.captured.url)
    }

    @Test
    fun `a hand-added source with a login in its URL is stored as received`() {
        val handAdded = "http://iptv.test/xmltv.php?username=me&password=secret"

        val updated = applyAndCaptureUpdate(SyncPayloads.encode(SyncPayloads.EpgSource("prov-1", handAdded, "My guide", 0, true)))

        assertEquals(handAdded, updated.url)
    }

    @Test
    fun `a source's interval is sent, and a source without one sends none`() {
        assertTrue(SyncPayloads.encode(SyncPayloads.EpgSource.of(local, listOf("prov-1"))).contains("\"refreshIntervalHours\":6"))
        assertFalse(
            SyncPayloads
                .encode(
                    SyncPayloads.EpgSource.of(local.copy(refreshIntervalHours = null), listOf("prov-1")),
                ).contains("refreshIntervalHours"),
        )
    }
}
