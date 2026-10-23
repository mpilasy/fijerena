package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.sync.SyncKind

/** Live sync device names: one shared, synced record per device, blank for the server's name. */
class AppSettingsDeviceNamesTest {
    private val settings =
        AppSettings(
            mockk<Context> { every { getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences() },
        )

    private val key = AppSettings.deviceNameSettingKey(DEVICE)

    @Test
    fun `a name set here is kept trimmed and sent`() {
        settings.setDeviceName(DEVICE, "  Living room  ")

        assertEquals("Living room", settings.deviceName(DEVICE))
        assertEquals(JsonPrimitive("Living room"), settings.syncedSetting(key, SyncKind.SHARED))
    }

    @Test
    fun `a blank name goes back to the server's, and that is sent too`() {
        settings.setDeviceName(DEVICE, "Living room")
        settings.setDeviceName(DEVICE, " ")

        assertNull(settings.deviceName(DEVICE))
        assertEquals(JsonPrimitive(""), settings.syncedSetting(key, SyncKind.SHARED))
        assertEquals(mapOf(DEVICE to ""), settings.deviceNames())
    }

    @Test
    fun `a name received from another device is applied`() {
        settings.applyRemoteSetting(key, SyncKind.SHARED, JsonPrimitive("Bedroom"))

        assertEquals("Bedroom", settings.deviceName(DEVICE))
        assertNull(settings.deviceName("other"))
    }

    @Test
    fun `nothing is sent for a device never named`() {
        assertNull(settings.syncedSetting(key, SyncKind.SHARED))
    }

    @Test
    fun `a name is cut to its maximum length`() {
        settings.setDeviceName(DEVICE, "x".repeat(100))

        assertEquals(AppSettings.MAX_DEVICE_NAME_LENGTH, settings.deviceName(DEVICE)?.length)
    }

    private companion object {
        const val DEVICE = "0123456789abcdef0123456789abcdef"
    }
}
