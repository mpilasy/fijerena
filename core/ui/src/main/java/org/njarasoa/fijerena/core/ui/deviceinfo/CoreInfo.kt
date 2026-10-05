package org.njarasoa.fijerena.core.ui.deviceinfo

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Debug
import android.os.StatFs
import android.view.Display
import kotlinx.coroutines.CancellationException
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.utils.UiText
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P1's sections: app, device, memory, storage, display. See
 * docs/plans/20261004_device-info-screen-plan.md → P1.
 */
suspend fun coreSections(
    context: Context,
    gitHash: String,
    buildTime: String,
): List<DeviceInfoSection> =
    listOf(
        appSection(context, gitHash, buildTime),
        deviceSection(context),
        memorySection(context),
        storageSection(context),
        displaySection(context),
    )

/** The value, or null when reading it fails (old API, refused, gone); never swallows cancellation. */
internal inline fun <T> readOrNull(block: () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

internal fun formatDateTime(epochMs: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))

/** "1.2 GB free of 3.0 GB". */
internal fun freeOfTotal(
    freeBytes: Long,
    totalBytes: Long,
): UiText =
    UiText.StringResource(R.string.device_info_free_of_format, NumberUtils.formatBytes(freeBytes), NumberUtils.formatBytes(totalBytes))

/** "3840×2160 @ 60 Hz"; the rate rounded to two decimals at most (23.976 → 23.98). */
internal fun modeText(
    width: Int,
    height: Int,
    refreshHz: Float,
): String = "$width×$height @ ${"%.2f".format(Locale.US, refreshHz).trimEnd('0').trimEnd('.')} Hz"

/** `Display.HdrCapabilities` type constants to names, in the order given; unknown ones as numbers. */
internal fun hdrTypeNames(types: IntArray): List<String> =
    types.distinct().map { type ->
        when (type) {
            1 -> "Dolby Vision"
            2 -> "HDR10"
            3 -> "HLG"
            4 -> "HDR10+"
            else -> "HDR type $type"
        }
    }

/**
 * A database's size with its `-wal`, `-shm` and `-journal` files, keyed by the main file's name.
 * [files] maps file name to size; companion files without their main file are left out.
 */
internal fun databaseSizes(files: Map<String, Long>): Map<String, Long> {
    val companions = listOf("-wal", "-shm", "-journal")
    return files.keys
        .filter { name -> companions.none { name.endsWith(it) } }
        .sorted()
        .associateWith { name -> files.getValue(name) + companions.sumOf { files[name + it] ?: 0L } }
}

private suspend fun appSection(
    context: Context,
    gitHash: String,
    buildTime: String,
): DeviceInfoSection {
    val pkg = readOrNull { context.packageManager.getPackageInfo(context.packageName, 0) }
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val providers = readOrNull { AppContainer.getInstance(context).providerRepository.getAllProvidersList() }
    val active = readOrNull { AppContainer.getInstance(context).providerRepository.getActiveProvider() }
    val profiles = readOrNull { ProfileRepository(context).count() }
    return DeviceInfoSection(
        R.string.device_info_section_app,
        listOf(
            infoRow(R.string.device_info_version, pkg?.let { "${it.versionName} (${it.longVersionCode})" }),
            infoRow(R.string.device_info_build, "$gitHash · $buildTime · ${if (debuggable) "debug" else "release"}"),
            infoRow(R.string.device_info_installed, pkg?.let { formatDateTime(it.firstInstallTime) }),
            infoRow(R.string.device_info_updated, pkg?.let { formatDateTime(it.lastUpdateTime) }),
            infoRow(R.string.device_info_active_source_type, active?.type),
            infoRow(R.string.device_info_source_count, providers?.size?.toString()),
            infoRow(R.string.device_info_profile_count, profiles?.toString()),
        ),
    )
}

private fun deviceSection(context: Context): DeviceInfoSection {
    val uiMode = context.getSystemService(UiModeManager::class.java)?.currentModeType
    val isTv = uiMode?.let { it == Configuration.UI_MODE_TYPE_TELEVISION }
    return DeviceInfoSection(
        R.string.device_info_section_device,
        listOf(
            infoRow(R.string.device_info_model, "${Build.MANUFACTURER} ${Build.MODEL}"),
            infoRow(R.string.device_info_codename, "${Build.DEVICE} / ${Build.PRODUCT}"),
            infoRow(R.string.device_info_android, "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            infoRow(R.string.device_info_security_patch, Build.VERSION.SECURITY_PATCH),
            infoRow(R.string.device_info_abis, Build.SUPPORTED_ABIS.joinToString(", ")),
            infoRow(
                R.string.device_info_form_factor,
                isTv?.let { UiText.StringResource(if (it) R.string.device_info_form_tv else R.string.device_info_form_mobile) }
                    ?: missingValue(),
            ),
            infoRow(R.string.device_info_device_type, readOrNull { DeviceDetector.detect().deviceType.name }),
        ),
    )
}

private fun memorySection(context: Context): DeviceInfoSection {
    val am = context.getSystemService(ActivityManager::class.java)
    val mem = am?.let { manager -> ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) } }
    val runtime = Runtime.getRuntime()
    val heapUsed = runtime.totalMemory() - runtime.freeMemory()
    return DeviceInfoSection(
        R.string.device_info_section_memory,
        listOf(
            infoRow(R.string.device_info_ram, mem?.let { freeOfTotal(it.availMem, it.totalMem) } ?: missingValue()),
            infoRow(R.string.device_info_low_memory_now, yesNo(mem?.lowMemory)),
            infoRow(R.string.device_info_low_memory_threshold, mem?.let { NumberUtils.formatBytes(it.threshold) }),
            infoRow(R.string.device_info_low_ram_device, yesNo(am?.isLowRamDevice)),
            infoRow(
                R.string.device_info_heap,
                UiText.StringResource(
                    R.string.device_info_used_of_format,
                    NumberUtils.formatBytes(heapUsed),
                    NumberUtils.formatBytes(runtime.maxMemory()),
                ),
            ),
            infoRow(R.string.device_info_heap_class, am?.let { "${it.memoryClass} MB / ${it.largeMemoryClass} MB" }),
            infoRow(R.string.device_info_native_heap, NumberUtils.formatBytes(Debug.getNativeHeapAllocatedSize())),
        ),
    )
}

private fun storageSection(context: Context): DeviceInfoSection {
    val stat = readOrNull { StatFs(context.filesDir.path) }
    val databases =
        readOrNull {
            databaseSizes(
                context.databaseList().associateWith { context.getDatabasePath(it).length() },
            )
        }.orEmpty()
    val databaseRows =
        databases.map { (name, bytes) ->
            DeviceInfoRow(
                UiText.StringResource(R.string.device_info_database_format, name),
                UiText.DynamicString(NumberUtils.formatBytes(bytes)),
            )
        }
    return DeviceInfoSection(
        R.string.device_info_section_storage,
        listOf(
            infoRow(R.string.device_info_internal_storage, stat?.let { freeOfTotal(it.availableBytes, it.totalBytes) } ?: missingValue()),
        ) + databaseRows +
            listOf(
                infoRow(
                    R.string.device_info_image_cache,
                    readOrNull { NumberUtils.formatBytes(directorySize(File(context.cacheDir, "image_cache"))) },
                ),
                infoRow(R.string.device_info_cache_total, readOrNull { NumberUtils.formatBytes(directorySize(context.cacheDir)) }),
                infoRow(R.string.device_info_files_total, readOrNull { NumberUtils.formatBytes(directorySize(context.filesDir)) }),
            ),
    )
}

private fun displaySection(context: Context): DeviceInfoSection {
    val display = readOrNull { context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) }
    val mode = display?.mode
    val modes =
        display
            ?.supportedModes
            ?.sortedWith(compareByDescending<Display.Mode> { it.physicalWidth * it.physicalHeight }.thenByDescending { it.refreshRate })
            ?.map { modeText(it.physicalWidth, it.physicalHeight, it.refreshRate) }
            ?.distinct()
    val hdrTypes = readOrNull { hdrTypes(display, mode) }
    return DeviceInfoSection(
        R.string.device_info_section_display,
        listOf(
            infoRow(R.string.device_info_display_mode, mode?.let { modeText(it.physicalWidth, it.physicalHeight, it.refreshRate) }),
            infoRow(R.string.device_info_density, "${context.resources.displayMetrics.densityDpi} dpi"),
            infoRow(R.string.device_info_display_modes, modes?.joinToString("\n")),
            infoRow(
                R.string.device_info_hdr,
                when {
                    hdrTypes == null -> missingValue()
                    hdrTypes.isEmpty() -> UiText.StringResource(R.string.device_info_hdr_none)
                    else -> UiText.DynamicString(hdrTypeNames(hdrTypes).joinToString(", "))
                },
            ),
        ),
    )
}

/** HDR types the display (API 34+: the current mode) accepts. */
@Suppress("DEPRECATION")
private fun hdrTypes(
    display: Display?,
    mode: Display.Mode?,
): IntArray? =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && mode != null -> mode.supportedHdrTypes
        else -> display?.hdrCapabilities?.supportedHdrTypes
    }

private fun directorySize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
