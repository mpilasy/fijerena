package org.njarasoa.fijerena.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sync.DeviceBuild
import org.njarasoa.fijerena.core.network.sync.DeviceReport
import org.njarasoa.fijerena.core.ui.sync.DeviceReportSummary
import org.njarasoa.fijerena.core.ui.sync.deviceReportKindLabel
import org.njarasoa.fijerena.core.ui.sync.deviceReportSummaryText
import org.njarasoa.fijerena.core.ui.sync.deviceReportText
import org.njarasoa.fijerena.core.ui.sync.recentSafeModeAt
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Shared core/ui code (`DeviceReportLine.kt`), tested here because this module runs Robolectric with
 * the core/ui resources.
 *
 * The report line of the Live Sync devices list and the text of Copy and Share (the device
 * diagnostics sync plan), in English.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class DeviceReportLineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resources = context.resources

    private fun line(report: DeviceReport?) = deviceReportSummaryText(resources, DeviceReportSummary.of(report))

    @Test
    fun `the line counts each kind with a problem, in words`() {
        val report = DeviceReport(sentAt = NOW, versionName = "0.9.1", counts7d = DeviceReport.Counts(crashes = 2, anrs = 1))

        assertEquals("2 crashes, 1 ANR in 7 days", line(report))
        assertEquals(
            "1 crash, 3 ANRs, 1 stop by the system, 2 errors in 7 days",
            line(report.copy(counts7d = DeviceReport.Counts(crashes = 1, anrs = 3, killed = 1, errors = 2))),
        )
    }

    @Test
    fun `no problems, no report and an empty report have their own lines`() {
        assertEquals("No problems in 7 days", line(DeviceReport(sentAt = NOW, versionName = "0.9.1")))
        assertEquals("Logs not reported yet", line(null))
        assertEquals("Logs not reported yet", line(DeviceReport(sentAt = NOW)))
    }

    @Test
    fun `a kind from a later build reads as an error`() {
        assertEquals("Stopped by the system", deviceReportKindLabel(resources, DeviceReport.KIND_KILLED))
        assertEquals("Error", deviceReportKindLabel(resources, "later"))
    }

    @Test
    fun `safe mode shows only when it started in the 14 days before the report`() {
        assertEquals(NOW - DAY, DeviceReport(sentAt = NOW, safeModeAt = NOW - DAY).recentSafeModeAt())
        assertNull(DeviceReport(sentAt = NOW, safeModeAt = NOW - 15 * DAY).recentSafeModeAt())
        assertNull(DeviceReport(sentAt = NOW).recentSafeModeAt())
    }

    @Test
    fun `the shared text has the header and every entry`() {
        val report =
            DeviceReport(
                sentAt = NOW,
                versionName = "0.9.1",
                counts7d = DeviceReport.Counts(errors = 1),
                failedCalls24h = 37,
                entries =
                    listOf(
                        DeviceReport.Entry(NOW - 1_000, DeviceReport.KIND_ERROR, "guide refresh failed", "HTTP 404", "", "Settings", true),
                    ),
            )

        val text = deviceReportText(context, "Living room TV", DeviceBuild(versionName = "0.9.1", buildType = "debug"), report)

        assertTrue(text, text.startsWith("Device Logs: Living room TV\nSent "))
        assertTrue(text, text.contains("0.9.1 · debug"))
        assertTrue(text, text.contains("1 error in 7 days"))
        assertTrue(text, text.contains("Failed server calls (24 h): 37"))
        assertTrue(text, text.contains("· Error · guide refresh failed\nHTTP 404\nThis build · Screen: Settings"))
        assertFalse(text, text.contains("safe mode"))
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        const val NOW = 1_800_000_000_000L
    }
}
