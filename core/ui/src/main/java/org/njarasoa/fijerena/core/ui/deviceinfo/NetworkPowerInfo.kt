package org.njarasoa.fijerena.core.ui.deviceinfo

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.suspendRunCatching
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncManager
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncManager.ScheduledWork
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.utils.UiText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Network, Power and background, and Sync sections of Device info: the active connection
 * (never its name or addresses), what Android allows this app in the background, and how the
 * catalog and guide syncs last ran. A value that can't be read shows "—". Call off the main
 * thread. See docs/plans/archive/20261004_device-info-screen-plan.md → P3.
 */
suspend fun networkPowerSections(context: Context): List<DeviceInfoSection> {
    val network = readNetwork(context)
    val power = readPower(context)
    val sync = readSync(context)
    return listOf(
        DeviceInfoSection(R.string.device_info_section_network, networkRows(network)),
        DeviceInfoSection(R.string.device_info_section_power, powerRows(power)),
        DeviceInfoSection(R.string.device_info_section_sync, syncRows(sync)),
    )
}

/** [transports] is null when unreadable, empty when there is no active network. */
internal data class NetworkFacts(
    val transports: Set<Int>?,
    val validated: Boolean?,
    val metered: Boolean?,
    val vpn: Boolean?,
    val privateDnsActive: Boolean?,
    val privateDnsServer: String?,
    val wifiLinkSpeedMbps: Int?,
    val wifiFrequencyMhz: Int?,
    val downstreamKbps: Int?,
)

/** [plugged] is `BatteryManager.EXTRA_PLUGGED`, [batteryPresent] `BatteryManager.EXTRA_PRESENT`. */
internal data class PowerFacts(
    val batteryOptimizationExempt: Boolean?,
    val standbyBucket: Int?,
    val backgroundRestricted: Boolean?,
    val batteryPresent: Boolean?,
    val plugged: Int?,
    val deviceIdle: Boolean?,
    val powerSave: Boolean?,
)

/** [catalogSync] is true for a source type the periodic catalog sync covers (Xtream). */
internal data class SourceSync(
    val name: String,
    val catalogSync: Boolean,
    val lastSyncMs: Long,
    val durationMs: Long,
    val error: String?,
)

internal data class GuideRefresh(
    val atMs: Long,
    val durationMs: Long,
    val sources: Int,
    val errors: Int,
)

/** [sources] is null when unreadable. */
internal data class SyncFacts(
    val sources: List<SourceSync>?,
    val guide: Result<GuideRefresh?>,
    val catalogJob: Result<ScheduledWork?>,
    val guideJob: Result<ScheduledWork?>,
)

// Hidden UsageStatsManager buckets, which an exempted app can still report.
private const val STANDBY_BUCKET_EXEMPTED = 5
private const val STANDBY_BUCKET_NEVER = 50

// BatteryManager.BATTERY_PLUGGED_DOCK, API 33.
private const val PLUGGED_DOCK = 8

private val TRANSPORTS =
    listOf(
        NetworkCapabilities.TRANSPORT_CELLULAR,
        NetworkCapabilities.TRANSPORT_WIFI,
        NetworkCapabilities.TRANSPORT_BLUETOOTH,
        NetworkCapabilities.TRANSPORT_ETHERNET,
        NetworkCapabilities.TRANSPORT_VPN,
        NetworkCapabilities.TRANSPORT_WIFI_AWARE,
        NetworkCapabilities.TRANSPORT_LOWPAN,
    )

/** The value, or null when the system refuses it (SecurityException, a missing service). */
private inline fun <T> orNull(read: () -> T?): T? =
    try {
        read()
    } catch (e: Exception) {
        // cancellation-ok: reads system services, no suspension point
        null
    }

private fun readNetwork(context: Context): NetworkFacts {
    val cm = orNull { context.getSystemService(ConnectivityManager::class.java) }
    val active = orNull { cm?.activeNetwork }
    val caps = orNull { active?.let { cm?.getNetworkCapabilities(it) } }
    val link = orNull { active?.let { cm?.getLinkProperties(it) } }
    // Wi-Fi details come with the capabilities from Android 12; below that they need ACCESS_WIFI_STATE.
    val wifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) orNull { caps?.transportInfo as? WifiInfo } else null
    val transports =
        when {
            cm == null -> null
            active == null -> emptySet()
            else -> caps?.let { c -> TRANSPORTS.filter { c.hasTransport(it) }.toSet() }
        }

    @Suppress("DEPRECATION") // allNetworks: still the only way to see a VPN this app doesn't use.
    val vpn =
        orNull {
            cm?.allNetworks?.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        }
    return NetworkFacts(
        transports = transports,
        validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        metered = orNull { active?.let { cm?.isActiveNetworkMetered } },
        vpn = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) true else vpn,
        privateDnsActive = link?.isPrivateDnsActive,
        privateDnsServer = link?.privateDnsServerName,
        wifiLinkSpeedMbps = wifi?.linkSpeed?.takeIf { it > 0 },
        wifiFrequencyMhz = wifi?.frequency?.takeIf { it > 0 },
        downstreamKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
    )
}

private fun readPower(context: Context): PowerFacts {
    val power = orNull { context.getSystemService(PowerManager::class.java) }
    val battery = orNull { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    return PowerFacts(
        batteryOptimizationExempt = orNull { power?.isIgnoringBatteryOptimizations(context.packageName) },
        standbyBucket = orNull { context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket },
        backgroundRestricted = orNull { context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted },
        batteryPresent = battery?.takeIf { it.hasExtra(BatteryManager.EXTRA_PRESENT) }?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true),
        plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)?.takeIf { it >= 0 },
        deviceIdle = orNull { power?.isDeviceIdleMode },
        powerSave = orNull { power?.isPowerSaveMode },
    )
}

private suspend fun readSync(context: Context): SyncFacts {
    val sources =
        suspendRunCatching {
            AppContainer.getInstance(context).providerRepository.getAllProvidersList().map {
                SourceSync(it.name, it.type == "XTREAM", it.lastSyncedAtMs, it.lastSyncDurationMs, it.lastSyncError)
            }
        }
    val guide =
        suspendRunCatching {
            SettingsDatabase
                .getInstance(context)
                .epgPipelineStatsDao()
                .getLatestStats()
                .first()
                ?.let { GuideRefresh(it.updatedAtMs, it.durationMs, it.sourcesProcessed, it.errors) }
        }
    val jobs = suspendRunCatching { ProviderSyncManager.scheduledSyncWork(context) }
    return SyncFacts(sources.getOrNull(), guide, jobs.map { it.first }, jobs.map { it.second })
}

internal fun networkRows(facts: NetworkFacts): List<DeviceInfoRow> {
    val wifiRows =
        if (facts.transports?.contains(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            listOf(
                infoRow(R.string.device_info_wifi_link_speed, facts.wifiLinkSpeedMbps?.let(::mbps) ?: missingValue()),
                infoRow(R.string.device_info_wifi_band, wifiBandValue(facts.wifiFrequencyMhz)),
            )
        } else {
            emptyList()
        }
    return listOf(
        infoRow(R.string.device_info_connection, transportValue(facts.transports)),
        infoRow(R.string.device_info_internet_validated, yesNo(facts.validated)),
        infoRow(R.string.device_info_metered, yesNo(facts.metered)),
        infoRow(R.string.device_info_vpn, yesNo(facts.vpn)),
        infoRow(R.string.device_info_private_dns, privateDnsValue(facts.privateDnsActive, facts.privateDnsServer)),
    ) + wifiRows + infoRow(R.string.device_info_downstream, bandwidthValue(facts.downstreamKbps))
}

/** The one transport that names the connection: wired first, then Wi-Fi, then mobile data. */
@StringRes
internal fun transportLabel(transports: Set<Int>): Int =
    when {
        NetworkCapabilities.TRANSPORT_ETHERNET in transports -> R.string.device_info_transport_ethernet
        NetworkCapabilities.TRANSPORT_WIFI in transports -> R.string.device_info_transport_wifi
        NetworkCapabilities.TRANSPORT_CELLULAR in transports -> R.string.device_info_transport_cellular
        NetworkCapabilities.TRANSPORT_BLUETOOTH in transports -> R.string.device_info_transport_bluetooth
        NetworkCapabilities.TRANSPORT_VPN in transports -> R.string.device_info_transport_vpn
        else -> R.string.device_info_transport_other
    }

internal fun transportValue(transports: Set<Int>?): UiText =
    when {
        transports == null -> missingValue()
        transports.isEmpty() -> UiText.StringResource(R.string.device_info_not_connected)
        else -> UiText.StringResource(transportLabel(transports))
    }

/** Null for a frequency outside the 2.4, 5, 6 and 60 GHz bands. */
@StringRes
internal fun wifiBand(frequencyMhz: Int): Int? =
    when (frequencyMhz) {
        in 2400..2500 -> R.string.device_info_band_2_4_ghz
        in 4900..5899 -> R.string.device_info_band_5_ghz
        in 5925..7125 -> R.string.device_info_band_6_ghz
        in 57000..71000 -> R.string.device_info_band_60_ghz
        else -> null
    }

internal fun wifiBandValue(frequencyMhz: Int?): UiText =
    when (frequencyMhz) {
        null -> {
            missingValue()
        }

        else -> {
            wifiBand(frequencyMhz)?.let { UiText.StringResource(it) }
                ?: UiText.StringResource(R.string.device_info_frequency_mhz, frequencyMhz)
        }
    }

/** Strict mode names its server; automatic mode has none. */
internal fun privateDnsValue(
    active: Boolean?,
    server: String?,
): UiText =
    when {
        active == null -> missingValue()
        !active -> UiText.StringResource(R.string.device_info_private_dns_off)
        server.isNullOrBlank() -> UiText.StringResource(R.string.device_info_private_dns_auto)
        else -> UiText.StringResource(R.string.device_info_private_dns_strict, server)
    }

internal fun bandwidthValue(kbps: Int?): UiText =
    when {
        kbps == null -> missingValue()
        kbps >= 1000 -> mbps((kbps + 500) / 1000)
        else -> UiText.StringResource(R.string.device_info_kbps, kbps)
    }

private fun mbps(value: Int): UiText = UiText.StringResource(R.string.device_info_mbps, value)

internal fun powerRows(facts: PowerFacts): List<DeviceInfoRow> =
    listOf(
        infoRow(R.string.device_info_battery_exempt, yesNo(facts.batteryOptimizationExempt)),
        infoRow(R.string.device_info_standby_bucket, facts.standbyBucket?.let(::standbyBucketValue) ?: missingValue()),
        infoRow(R.string.device_info_background_restricted, yesNo(facts.backgroundRestricted)),
        infoRow(R.string.device_info_power_source, facts.plugged?.let(::powerSourceValue) ?: missingValue()),
        infoRow(R.string.device_info_battery_present, yesNo(facts.batteryPresent)),
        infoRow(R.string.device_info_charging, yesNo(chargingForDoze(facts.batteryPresent, facts.plugged))),
        infoRow(R.string.device_info_device_idle, yesNo(facts.deviceIdle)),
        infoRow(R.string.device_info_battery_saver, yesNo(facts.powerSave)),
    )

/**
 * Whether Doze counts the device as charging: a battery present and a power source plugged in
 * (`DeviceIdleController` reads both from `ACTION_BATTERY_CHANGED`). A Shield has no battery, so
 * it never counts as charging and dozes on mains power (`dumpsys deviceidle` `mCharging=false`
 * beside `dumpsys battery` `AC powered: true`, `present: false` on darcy, 2026-10-05).
 * `BatteryManager.isCharging` said yes there, so it isn't used.
 */
internal fun chargingForDoze(
    batteryPresent: Boolean?,
    plugged: Int?,
): Boolean? = if (batteryPresent == null || plugged == null) null else batteryPresent && plugged != 0

/** Null for a bucket Android added after this was written; the row then shows the number. */
@StringRes
internal fun standbyBucketLabel(bucket: Int): Int? =
    when (bucket) {
        STANDBY_BUCKET_EXEMPTED -> R.string.device_info_bucket_exempted
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> R.string.device_info_bucket_active
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> R.string.device_info_bucket_working_set
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> R.string.device_info_bucket_frequent
        UsageStatsManager.STANDBY_BUCKET_RARE -> R.string.device_info_bucket_rare
        UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> R.string.device_info_bucket_restricted
        STANDBY_BUCKET_NEVER -> R.string.device_info_bucket_never
        else -> null
    }

internal fun standbyBucketValue(bucket: Int): UiText =
    standbyBucketLabel(bucket)?.let { UiText.StringResource(it) } ?: UiText.DynamicString(bucket.toString())

@StringRes
internal fun powerSourceLabel(plugged: Int): Int? =
    when (plugged) {
        0 -> R.string.device_info_power_battery
        BatteryManager.BATTERY_PLUGGED_AC -> R.string.device_info_power_ac
        BatteryManager.BATTERY_PLUGGED_USB -> R.string.device_info_power_usb
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.device_info_power_wireless
        PLUGGED_DOCK -> R.string.device_info_power_dock
        else -> null
    }

internal fun powerSourceValue(plugged: Int): UiText =
    powerSourceLabel(plugged)?.let { UiText.StringResource(it) } ?: UiText.DynamicString(plugged.toString())

internal fun syncRows(facts: SyncFacts): List<DeviceInfoRow> {
    val sourceRows =
        when {
            facts.sources == null -> listOf(infoRow(R.string.device_info_sources, missingValue()))
            facts.sources.isEmpty() -> listOf(infoRow(R.string.device_info_sources, UiText.StringResource(R.string.device_info_no_sources)))
            else -> facts.sources.map { syncRow(it.name, it.catalogSync, it.lastSyncMs, it.durationMs, it.error) }
        }
    return sourceRows +
        listOf(
            infoRow(R.string.device_info_guide_last_refresh, facts.guide.fold(::guideRefreshValue) { missingValue() }),
            infoRow(R.string.device_info_catalog_job, facts.catalogJob.fold({ workStateValue(it?.state) }) { missingValue() }),
            infoRow(R.string.device_info_catalog_job_next, nextRunValue(facts.catalogJob.getOrNull())),
            infoRow(R.string.device_info_guide_job, facts.guideJob.fold({ workStateValue(it?.state) }) { missingValue() }),
            infoRow(R.string.device_info_guide_job_next, nextRunValue(facts.guideJob.getOrNull())),
        )
}

/**
 * One source's last catalog sync, labelled with the source's own name. Only the first line of
 * [error] is kept: a developer-mode error carries the raw exception below it, which can name the
 * server.
 */
internal fun syncRow(
    name: String,
    catalogSync: Boolean,
    lastSyncMs: Long,
    durationMs: Long,
    error: String?,
): DeviceInfoRow {
    val value =
        when {
            lastSyncMs <= 0 && catalogSync -> {
                UiText.StringResource(R.string.device_info_sync_never)
            }

            lastSyncMs <= 0 -> {
                UiText.StringResource(R.string.device_info_sync_not_applicable)
            }

            error.isNullOrBlank() -> {
                UiText.StringResource(R.string.device_info_sync_ok, syncTime(lastSyncMs), roundedSeconds(durationMs))
            }

            else -> {
                UiText.StringResource(
                    R.string.device_info_sync_failed,
                    syncTime(lastSyncMs),
                    roundedSeconds(durationMs),
                    Redact.text(error.lineSequence().first().trim()),
                )
            }
        }
    return DeviceInfoRow(UiText.DynamicString(name), value)
}

internal fun guideRefreshValue(refresh: GuideRefresh?): UiText =
    when {
        refresh == null -> {
            UiText.StringResource(R.string.device_info_guide_never)
        }

        refresh.errors == 0 -> {
            UiText.StringResource(
                R.string.device_info_guide_ok,
                syncTime(refresh.atMs),
                roundedSeconds(refresh.durationMs),
                refresh.sources,
            )
        }

        else -> {
            UiText.StringResource(
                R.string.device_info_guide_failed,
                syncTime(refresh.atMs),
                roundedSeconds(refresh.durationMs),
                refresh.sources,
                refresh.errors,
            )
        }
    }

/** Null for a state WorkManager added after this was written; the row then shows its name. */
@StringRes
internal fun workStateLabel(state: String): Int? =
    when (state) {
        "ENQUEUED" -> R.string.device_info_job_scheduled
        "RUNNING" -> R.string.device_info_job_running
        "BLOCKED" -> R.string.device_info_job_blocked
        "SUCCEEDED" -> R.string.device_info_job_succeeded
        "FAILED" -> R.string.device_info_job_failed
        "CANCELLED" -> R.string.device_info_job_cancelled
        else -> null
    }

/** [state] is null when the job isn't enqueued. */
internal fun workStateValue(state: String?): UiText =
    when (state) {
        null -> UiText.StringResource(R.string.device_info_job_not_scheduled)
        else -> workStateLabel(state)?.let { UiText.StringResource(it) } ?: UiText.DynamicString(state)
    }

private fun nextRunValue(work: ScheduledWork?): UiText = work?.nextRunAtMs?.let { UiText.DynamicString(syncTime(it)) } ?: missingValue()

private fun syncTime(epochMs: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))

/** Rounded to the nearest second. */
private fun roundedSeconds(durationMs: Long): Long = (durationMs + 500) / 1000
