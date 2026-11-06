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
import org.njarasoa.fijerena.core.network.sync.DeviceBuild
import org.njarasoa.fijerena.core.network.sync.SyncKind

/** Live sync device builds: one shared record per device, written by that device on a change only (the device builds plan). */
class AppSettingsDeviceBuildsTest {
    private val settings =
        AppSettings(
            mockk<Context> { every { getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences() },
        )

    private val key = AppSettings.deviceBuildSettingKey(DEVICE)
    private val build = DeviceBuild("0.9.20261106024835", 87, "debug", "tv", "SHIELD Android TV", "11", 30)

    @Test
    fun `a build is recorded and sent once, until it changes`() {
        assertTrue(settings.reportDeviceBuild(DEVICE, build))
        assertFalse(settings.reportDeviceBuild(DEVICE, build))
        assertTrue(settings.reportDeviceBuild(DEVICE, build.copy(versionCode = 88)))

        assertEquals(mapOf(DEVICE to build.copy(versionCode = 88)), settings.deviceBuilds())
        assertTrue(settings.syncedSetting(key, SyncKind.SHARED)!!.content.contains("\"versionCode\":88"))
    }

    @Test
    fun `a build received from another device is applied`() {
        val text = """{"versionName":"0.9.1","versionCode":5,"buildType":"release","app":"phone"}"""

        settings.applyRemoteSetting(key, SyncKind.SHARED, JsonPrimitive(text))

        assertEquals(
            DeviceBuild(versionName = "0.9.1", versionCode = 5, buildType = "release", app = "phone"),
            settings.deviceBuilds()[DEVICE],
        )
    }

    @Test
    fun `a field from a later build is ignored, and a bad or huge value is left out`() {
        settings.applyRemoteSetting(key, SyncKind.SHARED, JsonPrimitive("""{"versionName":"1.0","gpu":"x"}"""))
        settings.applyRemoteSetting(AppSettings.deviceBuildSettingKey("bad"), SyncKind.SHARED, JsonPrimitive("not json"))
        settings.applyRemoteSetting(AppSettings.deviceBuildSettingKey("huge"), SyncKind.SHARED, JsonPrimitive("x".repeat(2_000)))

        assertEquals(mapOf(DEVICE to DeviceBuild(versionName = "1.0")), settings.deviceBuilds())
    }

    @Test
    fun `nothing is sent for a device that never reported`() {
        assertNull(settings.syncedSetting(key, SyncKind.SHARED))
    }

    private companion object {
        const val DEVICE = "0123456789abcdef0123456789abcdef"
    }
}
