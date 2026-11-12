package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.sync.DeviceReport
import org.njarasoa.fijerena.core.network.sync.DeviceReportBuilder
import org.njarasoa.fijerena.core.network.sync.SyncKind

/**
 * Live sync device reports: one shared record per device, sent by that device when it changed or
 * once a day, kept on the others with a length cap (the device diagnostics sync plan).
 */
class AppSettingsDeviceReportsTest {
    private val prefs = FakeSharedPreferences()
    private val settings = AppSettings(mockk<Context> { every { getSharedPreferences("app_settings", any()) } returns prefs })

    private val key = AppSettings.deviceReportSettingKey(DEVICE)
    private val report =
        DeviceReport(
            sentAt = NOW,
            versionName = "0.9.20261111000000",
            counts7d = DeviceReport.Counts(crashes = 1),
            entries = listOf(DeviceReport.Entry(NOW - 1_000, DeviceReport.KIND_CRASH, "uncaught on main", "IllegalStateException: boom")),
        )

    @Test
    fun `a report is sent when it changes or after 24 h, not for a new send time alone`() {
        assertTrue(settings.reportDeviceReport(DEVICE, report))
        assertFalse(settings.reportDeviceReport(DEVICE, report.copy(sentAt = NOW + 60_000)))
        assertTrue(settings.reportDeviceReport(DEVICE, report.copy(sentAt = NOW + 120_000, failedCalls24h = 3)))
        assertFalse(settings.reportDeviceReport(DEVICE, report.copy(sentAt = NOW + 180_000, failedCalls24h = 3)))
        assertTrue(
            settings.reportDeviceReport(
                DEVICE,
                report.copy(sentAt = NOW + 120_000 + AppSettings.DEVICE_REPORT_RESEND_MS, failedCalls24h = 3),
            ),
        )

        assertEquals(3, settings.deviceReports()[DEVICE]?.failedCalls24h)
        assertTrue(settings.syncedSetting(key, SyncKind.SHARED)!!.content.contains("\"failedCalls24h\":3"))
    }

    @Test
    fun `a report received from another device is applied and reads back`() {
        settings.applyRemoteSetting(key, SyncKind.SHARED, JsonPrimitive(DeviceReportBuilder.encode(report)))

        assertEquals(mapOf(DEVICE to report), settings.deviceReports())
    }

    @Test
    fun `a value over 16,000 characters, one that is not text and one that doesn't read are left out`() {
        val huge = DeviceReportBuilder.encode(report.copy(versionName = "x".repeat(AppSettings.MAX_DEVICE_REPORT_LENGTH)))
        settings.applyRemoteSetting(AppSettings.deviceReportSettingKey("huge"), SyncKind.SHARED, JsonPrimitive(huge))
        settings.applyRemoteSetting(AppSettings.deviceReportSettingKey("number"), SyncKind.SHARED, JsonPrimitive(5))
        settings.applyRemoteSetting(AppSettings.deviceReportSettingKey("bad"), SyncKind.SHARED, JsonPrimitive("not json"))

        assertFalse(prefs.contains(AppSettings.deviceReportSettingKey("huge")))
        assertFalse(prefs.contains(AppSettings.deviceReportSettingKey("number")))
        assertEquals(emptyMap<String, DeviceReport>(), settings.deviceReports())
    }

    @Test
    fun `a setting key this build does not know is ignored, as an older build ignores device_report`() {
        settings.applyRemoteSetting("device_diary:$DEVICE", SyncKind.SHARED, JsonPrimitive("{}"))

        assertTrue(prefs.all.isEmpty())
        assertNull(settings.syncedSetting("device_diary:$DEVICE", SyncKind.SHARED))
    }

    @Test
    fun `sharing logs is on until turned off, and nothing is sent for a device that never reported`() {
        assertTrue(settings.shareLogsWithGroup)
        settings.shareLogsWithGroup = false
        assertFalse(settings.shareLogsWithGroup)
        assertNull(settings.syncedSetting(key, SyncKind.SHARED))
    }

    private companion object {
        const val DEVICE = "0123456789abcdef0123456789abcdef"
        const val NOW = 1_800_000_000_000L
    }
}
