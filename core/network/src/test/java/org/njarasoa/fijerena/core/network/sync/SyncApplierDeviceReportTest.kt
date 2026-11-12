package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/** A device report another device sent arrives as a shared setting and is kept here (the device diagnostics sync plan). */
class SyncApplierDeviceReportTest {
    private val sync = mockk<SettingsSyncDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
    private val prefsFiles = mutableMapOf<String, SharedPreferences>()
    private val context =
        mockk<Context>(relaxed = true).also { context ->
            every { context.getSharedPreferences(any(), any()) } answers { prefsFiles.getOrPut(firstArg()) { FakeSharedPreferences() } }
        }

    @Before
    fun setup() {
        every { settingsDb.settingsSyncDao() } returns sync
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
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a device report record is applied to the settings`() =
        runBlocking {
            val report =
                DeviceReport(
                    sentAt = 1_800_000_000_000L,
                    versionName = "0.9.20261111000000",
                    counts7d = DeviceReport.Counts(anrs = 1),
                    entries =
                        listOf(
                            DeviceReport.Entry(1_799_999_000_000L, DeviceReport.KIND_ANR, "exit ANR", "Input dispatching timed out"),
                        ),
                )
            val record =
                SyncRecord(
                    SyncKey(SyncKind.SHARED, "", SyncKind.SETTING, AppSettings.deviceReportSettingKey(DEVICE)),
                    hlc = 5,
                    payload = SyncPayloads.encode(SyncPayloads.Setting(JsonPrimitive(DeviceReportBuilder.encode(report)))),
                )

            val result = SyncApplier(context, thisDeviceId = { "this-device" }, playingSessionId = { null }).apply(listOf(record))

            assertEquals(1, result.applied)
            assertEquals(mapOf(DEVICE to report), AppSettings(context).deviceReports())
        }

    private companion object {
        const val DEVICE = "fedcba9876543210fedcba9876543210"
    }
}
