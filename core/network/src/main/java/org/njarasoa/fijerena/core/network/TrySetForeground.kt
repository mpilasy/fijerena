package org.njarasoa.fijerena.core.network

import android.util.Log
import androidx.work.CoroutineWorker
import kotlinx.coroutines.CancellationException
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * `setForeground(getForegroundInfo())` that never fails the run. Android 12+ refuses a foreground
 * start from a background-started worker (ForegroundServiceStartNotAllowedException), and Google
 * TV can refuse it too; the work itself still runs while the process is alive, so log it, record
 * it in [CrashLog] and carry on without foreground status (R-11).
 */
suspend fun CoroutineWorker.trySetForeground(tag: String) {
    try {
        setForeground(getForegroundInfo())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(tag, "doWork: setForeground refused — continuing without foreground (${e.javaClass.simpleName})", e)
        CrashLog.record("$tag setForeground", e)
    }
}
