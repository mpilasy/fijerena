package org.njarasoa.fijerena.core.ui.deviceinfo

import android.app.usage.UsageStatsManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncManager.ScheduledWork
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.UiText
import java.util.TimeZone

class NetworkPowerInfoTest {
    private lateinit var originalZone: TimeZone

    @Before
    fun useUtc() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    private val at = 1_791_117_240_000L // 2026-10-04 12:34 UTC

    private fun UiText.res(): Int = (this as UiText.StringResource).resId

    private fun UiText.argList(): List<Any> = (this as UiText.StringResource).args.toList()

    private fun assertMissing(value: UiText) = assertEquals(R.string.device_info_missing, value.res())

    @Test
    fun `standby buckets have names, an unknown one shows its number`() {
        assertEquals(R.string.device_info_bucket_active, standbyBucketLabel(UsageStatsManager.STANDBY_BUCKET_ACTIVE))
        assertEquals(R.string.device_info_bucket_working_set, standbyBucketLabel(UsageStatsManager.STANDBY_BUCKET_WORKING_SET))
        assertEquals(R.string.device_info_bucket_frequent, standbyBucketLabel(UsageStatsManager.STANDBY_BUCKET_FREQUENT))
        assertEquals(R.string.device_info_bucket_rare, standbyBucketLabel(UsageStatsManager.STANDBY_BUCKET_RARE))
        assertEquals(R.string.device_info_bucket_restricted, standbyBucketLabel(UsageStatsManager.STANDBY_BUCKET_RESTRICTED))
        assertEquals(R.string.device_info_bucket_exempted, standbyBucketLabel(5))
        assertEquals(R.string.device_info_bucket_never, standbyBucketLabel(50))
        assertNull(standbyBucketLabel(99))
        assertEquals(UiText.DynamicString("99"), standbyBucketValue(99))
    }

    @Test
    fun `transport prefers wired, then Wi-Fi, then mobile data`() {
        val wifi = NetworkCapabilities.TRANSPORT_WIFI
        val ethernet = NetworkCapabilities.TRANSPORT_ETHERNET
        val cellular = NetworkCapabilities.TRANSPORT_CELLULAR
        val vpn = NetworkCapabilities.TRANSPORT_VPN
        assertEquals(R.string.device_info_transport_ethernet, transportLabel(setOf(wifi, ethernet)))
        assertEquals(R.string.device_info_transport_wifi, transportLabel(setOf(wifi)))
        assertEquals(R.string.device_info_transport_wifi, transportLabel(setOf(vpn, wifi)))
        assertEquals(R.string.device_info_transport_cellular, transportLabel(setOf(cellular)))
        assertEquals(R.string.device_info_transport_vpn, transportLabel(setOf(vpn)))
        assertEquals(R.string.device_info_transport_other, transportLabel(setOf(NetworkCapabilities.TRANSPORT_LOWPAN)))
        assertEquals(R.string.device_info_not_connected, transportValue(emptySet()).res())
        assertMissing(transportValue(null))
    }

    @Test
    fun `Wi-Fi band from frequency`() {
        assertEquals(R.string.device_info_band_2_4_ghz, wifiBand(2412))
        assertEquals(R.string.device_info_band_5_ghz, wifiBand(5180))
        assertEquals(R.string.device_info_band_6_ghz, wifiBand(5955))
        assertEquals(R.string.device_info_band_60_ghz, wifiBand(60480))
        assertNull(wifiBand(3000))
        val raw = wifiBandValue(3000)
        assertEquals(R.string.device_info_frequency_mhz, raw.res())
        assertEquals(listOf<Any>(3000), raw.argList())
        assertMissing(wifiBandValue(null))
    }

    @Test
    fun `private DNS off, automatic, or strict without naming its server`() {
        assertEquals(R.string.device_info_private_dns_off, privateDnsValue(false, null).res())
        assertEquals(R.string.device_info_private_dns_auto, privateDnsValue(true, null).res())
        val strict = privateDnsValue(true, "dns.example")
        assertEquals(R.string.device_info_private_dns_strict, strict.res())
        assertEquals(emptyList<Any>(), strict.argList())
        assertMissing(privateDnsValue(null, null))
    }

    @Test
    fun `bandwidth in Mbps from 1000 kbps, kbps below`() {
        assertEquals(R.string.device_info_mbps, bandwidthValue(23_400).res())
        assertEquals(listOf<Any>(23), bandwidthValue(23_400).argList())
        assertEquals(R.string.device_info_kbps, bandwidthValue(850).res())
        assertEquals(listOf<Any>(850), bandwidthValue(850).argList())
        assertMissing(bandwidthValue(null))
    }

    @Test
    fun `Wi-Fi rows only on Wi-Fi`() {
        val facts =
            NetworkFacts(
                transports = setOf(NetworkCapabilities.TRANSPORT_WIFI),
                validated = true,
                metered = false,
                vpn = false,
                privateDnsActive = false,
                privateDnsServer = null,
                wifiLinkSpeedMbps = 866,
                wifiFrequencyMhz = 5180,
                downstreamKbps = 50_000,
            )
        val onWifi = networkRows(facts).map { it.label.res() }
        assertTrue(R.string.device_info_wifi_link_speed in onWifi)
        assertTrue(R.string.device_info_wifi_band in onWifi)
        assertEquals(8, onWifi.size)
        val wired = networkRows(facts.copy(transports = setOf(NetworkCapabilities.TRANSPORT_ETHERNET))).map { it.label.res() }
        assertEquals(6, wired.size)
        assertTrue(R.string.device_info_wifi_link_speed !in wired)
    }

    @Test
    fun `power source names`() {
        assertEquals(R.string.device_info_power_battery, powerSourceLabel(0))
        assertEquals(R.string.device_info_power_ac, powerSourceLabel(BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals(R.string.device_info_power_usb, powerSourceLabel(BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals(R.string.device_info_power_wireless, powerSourceLabel(BatteryManager.BATTERY_PLUGGED_WIRELESS))
        assertEquals(R.string.device_info_power_dock, powerSourceLabel(8))
        assertEquals(UiText.DynamicString("3"), powerSourceValue(3))
    }

    @Test
    fun `a source row is labelled with its name and shows time, seconds and OK`() {
        val row = syncRow("EN - My TV", catalogSync = true, lastSyncMs = at, durationMs = 41_600, error = null)
        assertEquals(UiText.DynamicString("EN - My TV"), row.label)
        assertEquals(R.string.device_info_sync_ok, row.value.res())
        assertEquals(listOf<Any>("2026-10-04 12:34", 42L), row.value.argList())
    }

    @Test
    fun `a failed sync keeps only the friendly first line, redacted`() {
        val error = "Network error http://user:secret@host/x\n\n[dev] UnknownHostException: Unable to resolve host \"server.example\""
        val row = syncRow("Mine", catalogSync = true, lastSyncMs = at, durationMs = 2_000, error = error)
        assertEquals(R.string.device_info_sync_failed, row.value.res())
        assertEquals(listOf<Any>("2026-10-04 12:34", 2L, "Network error http://***@host/x"), row.value.argList())
    }

    @Test
    fun `a source never synced, or of a type without catalog sync`() {
        assertEquals(R.string.device_info_sync_never, syncRow("X", true, 0, 0, null).value.res())
        assertEquals(R.string.device_info_sync_not_applicable, syncRow("J", false, 0, 0, null).value.res())
    }

    @Test
    fun `guide refresh result`() {
        assertEquals(R.string.device_info_guide_never, guideRefreshValue(null).res())
        val ok = guideRefreshValue(GuideRefresh(at, 90_000, sources = 3, errors = 0))
        assertEquals(R.string.device_info_guide_ok, ok.res())
        assertEquals(listOf<Any>("2026-10-04 12:34", 90L, 3), ok.argList())
        val failed = guideRefreshValue(GuideRefresh(at, 90_000, sources = 3, errors = 1))
        assertEquals(R.string.device_info_guide_failed, failed.res())
        assertEquals(listOf<Any>("2026-10-04 12:34", 90L, 3, 1), failed.argList())
    }

    @Test
    fun `work states have names, null is not scheduled`() {
        assertEquals(R.string.device_info_job_scheduled, workStateValue("ENQUEUED").res())
        assertEquals(R.string.device_info_job_running, workStateValue("RUNNING").res())
        assertEquals(R.string.device_info_job_not_scheduled, workStateValue(null).res())
        assertEquals(UiText.DynamicString("NEW_STATE"), workStateValue("NEW_STATE"))
    }

    @Test
    fun `sync section with no sources says so, and unreadable values show a dash`() {
        val facts =
            SyncFacts(
                sources = emptyList(),
                guide = Result.failure(IllegalStateException()),
                catalogJob = Result.success(ScheduledWork("ENQUEUED", at)),
                guideJob = Result.success(null),
            )
        val rows = syncRows(facts)
        assertEquals(6, rows.size)
        assertEquals(R.string.device_info_sources, rows[0].label.res())
        assertEquals(R.string.device_info_no_sources, rows[0].value.res())
        assertMissing(rows[1].value)
        assertEquals(R.string.device_info_job_scheduled, rows[2].value.res())
        assertEquals(UiText.DynamicString("2026-10-04 12:34"), rows[3].value)
        assertEquals(R.string.device_info_job_not_scheduled, rows[4].value.res())
        assertMissing(rows[5].value)
        assertMissing(syncRows(facts.copy(sources = null))[0].value)
    }

    @Test
    fun `doze counts as charging only with a battery present and power plugged in`() {
        assertEquals(false, chargingForDoze(batteryPresent = false, plugged = 1))
        assertEquals(true, chargingForDoze(batteryPresent = true, plugged = 1))
        assertEquals(false, chargingForDoze(batteryPresent = true, plugged = 0))
        assertEquals(null, chargingForDoze(batteryPresent = null, plugged = 1))
        assertEquals(null, chargingForDoze(batteryPresent = true, plugged = null))
    }
}
