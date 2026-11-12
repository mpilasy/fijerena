package org.njarasoa.fijerena.core.network.sync

import kotlinx.serialization.Serializable

/**
 * A short summary of a live sync device's recent crashes, ANRs, system kills and recorded errors,
 * as it reports it to its group (the device diagnostics sync plan): a shared setting record
 * `device_report:<deviceId>` whose value is this, as JSON. End-to-end encrypted like every record.
 * Every field has a default, so a field added later reads as empty from an older build, and an
 * empty report (the device's switch is off) has no [versionName].
 *
 * [DeviceReportBuilder] makes it from the device's own logs, with every text masked
 * (`Redact.report`).
 */
@Serializable
data class DeviceReport(
    /** When the device wrote this report (its wall clock). */
    val sentAt: Long = 0,
    val versionName: String = "",
    /** When safe mode last started on the device, or 0. */
    val safeModeAt: Long = 0,
    /** The problems of the last 7 days, of all the entries, not only of [entries]. */
    val counts7d: Counts = Counts(),
    /** The server calls that failed in the last 24 h (Logs' connection entries). */
    val failedCalls24h: Int = 0,
    /** Newest first. */
    val entries: List<Entry> = emptyList(),
) {
    /** True for a report with content: a device whose switch is off sends one with nothing in it. */
    val isReported: Boolean get() = versionName.isNotEmpty()

    @Serializable
    data class Counts(
        val crashes: Int = 0,
        val anrs: Int = 0,
        val killed: Int = 0,
        val errors: Int = 0,
    ) {
        val total: Int get() = crashes + anrs + killed + errors
    }

    @Serializable
    data class Entry(
        val timeMs: Long = 0,
        /** [KIND_CRASH], [KIND_ANR], [KIND_KILLED] or [KIND_ERROR]; a later kind reads as an error. */
        val kind: String = KIND_ERROR,
        /** What recorded it: the crash log's source (`guide refresh failed`) or the exit reason. */
        val source: String = "",
        /** The first line of the top exception, or of the message. */
        val message: String = "",
        /** The first line of the root cause, when it is not [message]. */
        val cause: String = "",
        /** The route name of the screen at that time; empty when not known (ANRs, system kills). */
        val screen: String = "",
        /** True when it happened after the device's last app update: "This build". */
        val afterUpdate: Boolean = false,
    )

    companion object {
        const val KIND_CRASH = "crash"
        const val KIND_ANR = "anr"
        const val KIND_KILLED = "killed"
        const val KIND_ERROR = "error"
    }
}
