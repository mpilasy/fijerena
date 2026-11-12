package org.njarasoa.fijerena.core.ui.sync

import android.content.Context
import android.content.res.Resources
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.sync.DeviceBuild
import org.njarasoa.fijerena.core.network.sync.DeviceReport
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaCodeText
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone

/** What a device's row says of its report (the device diagnostics sync plan). */
sealed interface DeviceReportSummary {
    /** No report, or an empty one (an older build, or the device's switch is off). */
    data object NotReported : DeviceReportSummary

    data object NoProblems : DeviceReportSummary

    data class Problems(
        val counts: DeviceReport.Counts,
    ) : DeviceReportSummary

    companion object {
        fun of(report: DeviceReport?): DeviceReportSummary =
            when {
                report == null || !report.isReported -> NotReported
                report.counts7d.total == 0 -> NoProblems
                else -> Problems(report.counts7d)
            }
    }
}

/** How long the page names a start in safe mode. */
private const val SAFE_MODE_SHOWN_MS = 14 * 24 * 60 * 60 * 1000L

/** When the device last started in safe mode, if that is at most 14 days before the report; else null. */
fun DeviceReport.recentSafeModeAt(): Long? = safeModeAt.takeIf { it > 0 && sentAt - it <= SAFE_MODE_SHOWN_MS }

/**
 * The line under a device's build line, for a Nerd, the same on TV and phone: "2 crashes, 1 ANR in
 * 7 days", "No problems in 7 days", or "Logs not reported yet".
 */
@Composable
fun DeviceReportLine(report: DeviceReport?) {
    val summary = DeviceReportSummary.of(report)
    CinemaText(
        deviceReportSummaryText(LocalResources.current, summary),
        role = TextRole.BodySmall,
        tone =
            when (summary) {
                DeviceReportSummary.NotReported -> TextTone.Tertiary
                DeviceReportSummary.NoProblems -> TextTone.Secondary
                is DeviceReportSummary.Problems -> TextTone.Warning
            },
    )
}

/**
 * The first block of the Device Logs page, the same on TV and phone: when the report was sent, the
 * build, the 7-day counts, the failed server calls and a recent start in safe mode.
 */
@Composable
fun DeviceLogsHeaderLines(
    build: DeviceBuild?,
    report: DeviceReport?,
) {
    val context = LocalContext.current
    if (report != null && report.isReported) {
        CinemaText(
            stringResource(R.string.device_logs_sent, deviceLogsTime(context, report.sentAt)),
            role = TextRole.Heading,
            tone = TextTone.Primary,
        )
    }
    DeviceBuildLine(build, older = false)
    DeviceReportLine(report)
    if (report != null && report.isReported) {
        CinemaText(
            stringResource(R.string.device_logs_failed_calls, report.failedCalls24h),
            role = TextRole.BodySmall,
            tone = TextTone.Secondary,
        )
        report.recentSafeModeAt()?.let {
            CinemaText(
                stringResource(R.string.device_logs_safe_mode, deviceLogsTime(context, it)),
                role = TextRole.BodySmall,
                tone = TextTone.Warning,
            )
        }
    }
}

/** One entry of the Device Logs page, the same on TV and phone. */
@Composable
fun DeviceLogsEntryLines(entry: DeviceReport.Entry) {
    val context = LocalContext.current
    val resources = LocalResources.current
    CinemaText(
        "${deviceLogsTime(context, entry.timeMs)} · ${deviceReportKindLabel(resources, entry.kind)}",
        role = TextRole.Heading,
        tone = if (entry.kind == DeviceReport.KIND_ERROR) TextTone.Primary else TextTone.Warning,
    )
    if (entry.source.isNotEmpty()) CinemaText(entry.source, role = TextRole.Body, tone = TextTone.Secondary)
    // Raw error text, masked on the device that sent it.
    if (entry.message.isNotEmpty()) CinemaCodeText(entry.message, role = TextRole.Body)
    if (entry.cause.isNotEmpty()) CinemaCodeText(entry.cause, role = TextRole.BodySmall, tone = TextTone.Secondary)
    CinemaText(entryFooter(resources, entry), role = TextRole.BodySmall, tone = TextTone.Tertiary)
}

/** "This build · Screen: Settings". */
private fun entryFooter(
    resources: Resources,
    entry: DeviceReport.Entry,
): String =
    listOfNotNull(
        resources.getString(if (entry.afterUpdate) R.string.device_logs_this_build else R.string.device_logs_before_update),
        entry.screen.ifEmpty { null }?.let { resources.getString(R.string.device_logs_screen, it) },
    ).joinToString(" · ")

/** [summary] in words, in the app's language. */
fun deviceReportSummaryText(
    resources: Resources,
    summary: DeviceReportSummary,
): String =
    when (summary) {
        DeviceReportSummary.NotReported -> {
            resources.getString(R.string.live_sync_device_logs_unknown)
        }

        DeviceReportSummary.NoProblems -> {
            resources.getString(R.string.live_sync_device_logs_none)
        }

        is DeviceReportSummary.Problems -> {
            resources.getString(R.string.live_sync_device_logs_counts, deviceReportCounts(resources, summary.counts))
        }
    }

/** "2 crashes, 1 ANR": each kind that has a problem, in a fixed order. */
fun deviceReportCounts(
    resources: Resources,
    counts: DeviceReport.Counts,
): String =
    listOf(
        R.plurals.live_sync_logs_crashes to counts.crashes,
        R.plurals.live_sync_logs_anrs to counts.anrs,
        R.plurals.live_sync_logs_killed to counts.killed,
        R.plurals.live_sync_logs_errors to counts.errors,
    ).filter { it.second > 0 }
        .joinToString(", ") { (id, count) -> resources.getQuantityString(id, count, count) }

/** The label of an entry's kind: Crash, ANR, Stopped by the system or Error. A kind from a later build reads as Error. */
fun deviceReportKindLabel(
    resources: Resources,
    kind: String,
): String =
    resources.getString(
        when (kind) {
            DeviceReport.KIND_CRASH -> R.string.device_logs_kind_crash
            DeviceReport.KIND_ANR -> R.string.device_logs_kind_anr
            DeviceReport.KIND_KILLED -> R.string.device_logs_kind_killed
            else -> R.string.device_logs_kind_error
        },
    )

/** A date and a time, as the device shows them. */
fun deviceLogsTime(
    context: Context,
    timeMs: Long,
): String = DateUtils.formatDateTime(context, timeMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)

/**
 * The whole report as plain text, for Copy and Share on the phone. Everything in it was masked on
 * the device that sent it (`Redact.report`).
 */
fun deviceReportText(
    context: Context,
    deviceName: String,
    build: DeviceBuild?,
    report: DeviceReport,
): String {
    val resources = context.resources
    val buildLine =
        listOfNotNull(
            report.versionName,
            build?.buildType?.ifEmpty { null },
            build?.androidRelease?.ifEmpty { null }?.let { "Android $it" },
        ).joinToString(" · ")
    val header =
        listOfNotNull(
            "${resources.getString(R.string.device_logs_title)}: $deviceName",
            resources.getString(R.string.device_logs_sent, deviceLogsTime(context, report.sentAt)),
            buildLine,
            deviceReportSummaryText(resources, DeviceReportSummary.of(report)),
            resources.getString(R.string.device_logs_failed_calls, report.failedCalls24h),
            report.recentSafeModeAt()?.let { resources.getString(R.string.device_logs_safe_mode, deviceLogsTime(context, it)) },
        )
    val entries =
        report.entries.map { entry ->
            listOfNotNull(
                "${deviceLogsTime(context, entry.timeMs)} · ${deviceReportKindLabel(resources, entry.kind)} · ${entry.source}",
                entry.message.ifEmpty { null },
                entry.cause.ifEmpty { null },
                entryFooter(resources, entry),
            ).joinToString("\n")
        }
    return (listOf(header.joinToString("\n")) + entries).joinToString("\n\n")
}
