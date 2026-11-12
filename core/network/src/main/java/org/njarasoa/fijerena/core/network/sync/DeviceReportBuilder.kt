package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import org.njarasoa.fijerena.core.player.diagnostics.ConnectionLog
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.diagnostics.ProcessExits
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.player.diagnostics.SafeMode
import org.njarasoa.fijerena.core.player.diagnostics.TraceCauses
import kotlin.math.abs

/**
 * Makes this device's [DeviceReport] from its own logs (the device diagnostics sync plan): the
 * crash log's entries ([CrashLog]), the system's record of how the app's processes ended
 * ([ProcessExits]) and the failed server calls ([ConnectionLog]).
 *
 * - An uncaught exception and an exit `CRASH` or `CRASH_NATIVE` are crashes, an exit `ANR` an ANR,
 *   an exit `LOW_MEMORY` or `EXCESSIVE_RESOURCE_USAGE` a system kill, every other crash log entry
 *   an error. Other exits (the user, an update, the app itself) are not problems and stay out.
 * - An exit `CRASH` within [SAME_CRASH_MS] of an uncaught exception is the same crash: only the
 *   entry with the stack trace stays.
 * - The newest [MAX_ENTRIES] of the last [ENTRY_DAYS] days, newer than Logs → Clear; then the
 *   oldest go until the JSON is at most [MAX_ENCODED_CHARS] characters.
 * - Every text goes through [Redact.report] and has a maximum length.
 */
object DeviceReportBuilder {
    const val MAX_ENTRIES = 20
    const val MAX_ENCODED_CHARS = 12_000
    const val SOURCE_MAX = 120
    const val MESSAGE_MAX = 200
    const val SCREEN_MAX = 60
    const val SAME_CRASH_MS = 10_000L
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val ENTRY_DAYS = 14
    private const val COUNT_DAYS = 7
    private const val UNCAUGHT_PREFIX = "uncaught on "

    /** What a report is made from: the logs as they read, and the facts of the device. */
    data class Input(
        val now: Long,
        val versionName: String,
        /** When the app was last installed or updated (`PackageInfo.lastUpdateTime`). */
        val lastUpdateTime: Long,
        /** When Logs was last cleared on the device, or 0: older entries stay out. */
        val clearedAt: Long,
        val safeModeAt: Long,
        /** [CrashLog.read]. */
        val crashLog: List<String>,
        /** [ProcessExits.recent]. */
        val exits: List<ProcessExits.Exit>,
        /** [ConnectionLog.read]. */
        val connections: List<String>,
    )

    /** This device's report now. Blocking (it reads the logs and the exit traces): call off the main thread. */
    fun build(
        context: Context,
        now: Long,
        clearedAt: Long,
    ): DeviceReport {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return build(
            Input(
                now = now,
                versionName = info.versionName.orEmpty(),
                lastUpdateTime = info.lastUpdateTime,
                clearedAt = clearedAt,
                safeModeAt = SafeMode.lastStartedAt(context),
                crashLog = CrashLog.read(),
                exits = ProcessExits.recent(context),
                connections = ConnectionLog.read(),
            ),
        )
    }

    fun build(input: Input): DeviceReport {
        val recorded = input.crashLog.map(CrashLog::parse)
        val uncaughtTimes = recorded.filter { it.source.startsWith(UNCAUGHT_PREFIX) }.map { it.timeMs }
        val fromLog = recorded.map { entryOf(it, input.lastUpdateTime) }
        val fromExits =
            input.exits
                .filterNot { exit -> exit.reason == "CRASH" && uncaughtTimes.any { abs(it - exit.timestampMs) <= SAME_CRASH_MS } }
                .mapNotNull { entryOf(it, input.lastUpdateTime) }
        val recent =
            (fromLog + fromExits)
                .filter { it.timeMs > input.clearedAt && it.timeMs >= input.now - ENTRY_DAYS * DAY_MS }
                .sortedByDescending { it.timeMs }
        val week = recent.filter { it.timeMs >= input.now - COUNT_DAYS * DAY_MS }
        val report =
            DeviceReport(
                sentAt = input.now,
                versionName = input.versionName,
                safeModeAt = input.safeModeAt,
                counts7d =
                    DeviceReport.Counts(
                        crashes = week.count { it.kind == DeviceReport.KIND_CRASH },
                        anrs = week.count { it.kind == DeviceReport.KIND_ANR },
                        killed = week.count { it.kind == DeviceReport.KIND_KILLED },
                        errors = week.count { it.kind == DeviceReport.KIND_ERROR },
                    ),
                failedCalls24h = failedCalls(input),
            )
        return report.copy(entries = fitting(report, recent.take(MAX_ENTRIES)))
    }

    /** The report as it is stored and sent. */
    fun encode(report: DeviceReport): String = SyncPayloads.json.encodeToString(DeviceReport.serializer(), report)

    /** [entries] without the oldest ones that make [report] longer than [MAX_ENCODED_CHARS]. */
    private fun fitting(
        report: DeviceReport,
        entries: List<DeviceReport.Entry>,
    ): List<DeviceReport.Entry> {
        var kept = entries
        while (kept.isNotEmpty() && encode(report.copy(entries = kept)).length > MAX_ENCODED_CHARS) {
            kept = kept.dropLast(1)
        }
        return kept
    }

    private fun entryOf(
        entry: CrashLog.Entry,
        lastUpdateTime: Long,
    ): DeviceReport.Entry {
        val causes = TraceCauses.parse(entry.body)
        val message =
            causes
                ?.chain
                ?.first()
                ?.substringBefore('\n')
                ?: entry.body
                    .lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
        val cause = causes?.rootCause?.takeIf { it != message }.orEmpty()
        return DeviceReport.Entry(
            timeMs = entry.timeMs,
            kind = if (entry.source.startsWith(UNCAUGHT_PREFIX)) DeviceReport.KIND_CRASH else DeviceReport.KIND_ERROR,
            source = masked(entry.source, SOURCE_MAX),
            message = masked(message, MESSAGE_MAX),
            cause = masked(cause, MESSAGE_MAX),
            screen = masked(entry.screen.orEmpty(), SCREEN_MAX),
            afterUpdate = entry.timeMs > lastUpdateTime,
        )
    }

    /** Null for an exit that is not a problem (the user, an update, the app itself). */
    private fun entryOf(
        exit: ProcessExits.Exit,
        lastUpdateTime: Long,
    ): DeviceReport.Entry? {
        val kind =
            when (exit.reason) {
                "CRASH", "CRASH_NATIVE" -> DeviceReport.KIND_CRASH
                "ANR" -> DeviceReport.KIND_ANR
                "LOW_MEMORY", "EXCESSIVE_RESOURCE_USAGE" -> DeviceReport.KIND_KILLED
                else -> null
            }
        return kind?.let {
            DeviceReport.Entry(
                timeMs = exit.timestampMs,
                kind = it,
                source = "exit ${exit.reason}",
                message =
                    masked(
                        exit.description
                            ?.lineSequence()
                            ?.firstOrNull { line -> line.isNotBlank() }
                            .orEmpty(),
                        MESSAGE_MAX,
                    ),
                afterUpdate = exit.timestampMs > lastUpdateTime,
            )
        }
    }

    /** The connection log's failed server calls ("HTTP 403 from …", "No answer from …") of the last 24 h. */
    private fun failedCalls(input: Input): Int =
        input.connections.count { raw ->
            val header = raw.substringBefore('\n')
            val timeMs = header.substringBefore(' ').toLongOrNull() ?: 0L
            val what = header.substringAfter(' ', "")
            val failedCall = what.startsWith("HTTP ") || what.startsWith("No answer from ")
            failedCall && timeMs > input.clearedAt && timeMs >= input.now - DAY_MS
        }

    /** Masked first, so a URL is never cut before its host is masked; then cut to [max] characters. */
    private fun masked(
        text: String,
        max: Int,
    ): String {
        val clean = Redact.report(text.trim())
        return if (clean.length <= max) clean else clean.take(max - 1).trimEnd() + "…"
    }
}
