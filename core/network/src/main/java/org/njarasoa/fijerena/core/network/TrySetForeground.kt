package org.njarasoa.fijerena.core.network

import android.app.ForegroundServiceStartNotAllowedException
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import kotlinx.coroutines.CancellationException
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * `setForeground(getForegroundInfo())` that never fails the run. Android 12+ refuses a foreground
 * start from a background-started worker (ForegroundServiceStartNotAllowedException), and Google
 * TV can refuse it too; the work itself still runs while the process is alive, so log it and carry
 * on without foreground status (R-11). Android's refusal is expected on every background start, so
 * only other failures go to [CrashLog].
 */
suspend fun CoroutineWorker.trySetForeground(tag: String) {
    try {
        setForeground(getForegroundInfo())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(tag, "doWork: setForeground refused — continuing without foreground (${e.javaClass.simpleName})", e)
        if (!isExpectedRefusal(e)) CrashLog.record("$tag setForeground", e)
    }
}

private fun isExpectedRefusal(e: Exception): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException
