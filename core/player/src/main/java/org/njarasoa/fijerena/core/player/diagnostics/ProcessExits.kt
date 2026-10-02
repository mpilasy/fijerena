package org.njarasoa.fijerena.core.player.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.util.Log

/**
 * Why this app's recent processes ended, as the system recorded it (API 30, this app's `minSdk`).
 * Catches what [CrashLog] can't: ANRs, native crashes, low-memory kills — none of which run any
 * code of ours on the way out. See docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-30.
 */
object ProcessExits {
    private const val TAG = "ProcessExits"
    private const val MAX_EXITS = 15
    private const val MAX_TRACE_LINES = 60
    private const val REASON_FREEZER = 14
    private const val REASON_PACKAGE_STATE_CHANGE = 15
    private const val REASON_PACKAGE_UPDATED = 16

    data class Exit(
        val timestampMs: Long,
        val reason: String,
        val description: String?,
        val processName: String,
        /** First lines of the ANR trace or native tombstone, when the system kept one. */
        val trace: String?,
    )

    /** Newest first. Blocking (reads traces) — call off the main thread. */
    fun recent(context: Context): List<Exit> {
        val am = context.getSystemService(ActivityManager::class.java)
        val exits =
            try {
                am?.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS).orEmpty()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read process exit reasons", e)
                emptyList()
            }
        return exits.map { info ->
            Exit(
                timestampMs = info.timestamp,
                reason = reasonName(info.reason),
                description = info.description,
                processName = info.processName,
                trace = traceOf(info),
            )
        }
    }

    private fun traceOf(info: ApplicationExitInfo): String? {
        val trace =
            try {
                info.traceInputStream?.bufferedReader()?.use { reader ->
                    reader.lineSequence().take(MAX_TRACE_LINES).joinToString("\n")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read exit trace", e)
                null
            }
        return trace
    }

    private fun reasonName(reason: Int): String =
        when (reason) {
            ApplicationExitInfo.REASON_ANR -> "ANR"

            ApplicationExitInfo.REASON_CRASH -> "CRASH"

            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"

            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"

            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"

            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"

            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"

            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"

            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"

            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"

            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"

            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"

            ApplicationExitInfo.REASON_OTHER -> "OTHER"

            // API 33+ reasons (ApplicationExitInfo.REASON_FREEZER etc.), kept as local constants so
            // a minSdk-30 build carries no InlinedApi warning.
            REASON_FREEZER -> "FREEZER"

            REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"

            REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"

            else -> "UNKNOWN($reason)"
        }
}
