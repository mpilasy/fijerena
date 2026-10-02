package org.njarasoa.fijerena.core.player.diagnostics

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Crash-loop safe mode. After [LaunchCounter.THRESHOLD] launches in a row whose process crashed (or
 * was killed) within [HEALTHY_AFTER_MS] of starting, the next launch
 * starts with [isActive] set: `FijerenaApplication` skips its risky startup work, and the nav
 * hosts skip theirs and show the safe-mode screen instead of their start destination. Leaving it
 * ([leave]) resets the counter and restarts the app normally. The counter lives in
 * `files/safemode/launches`; see docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-10.
 */
object SafeMode {
    /**
     * Process lifetime after which a launch counts as healthy. Measured from process start, not
     * from a screen being shown: processes started only for background work (WorkManager, the
     * playback service) never show one, and counting them as unfinished put a healthy app into
     * safe mode.
     */
    private const val HEALTHY_AFTER_MS = 30_000L

    @Volatile private var counter: LaunchCounter? = null

    /** Whether this process started in safe mode. Fixed for the life of the process. */
    @Volatile var isActive: Boolean = false
        private set

    /** Call in `Application.onCreate()`, right after [CrashLog.install]. */
    fun init(context: Context) {
        val launchCounter = LaunchCounter(File(context.filesDir, "safemode/launches"), System::currentTimeMillis)
        counter = launchCounter
        isActive = launchCounter.recordLaunch()
        // Never marked healthy in safe mode: only [leave] ends it.
        if (!isActive) {
            AppScopes.create("SafeMode.healthy", Dispatchers.IO).launch {
                delay(HEALTHY_AFTER_MS)
                launchCounter.markHealthy()
            }
        }
    }

    /**
     * "Continue": forget the unfinished launches and start the app again, in a fresh process so
     * the startup work this one skipped runs.
     */
    fun leave(activity: Activity) {
        counter?.markHealthy()
        restartProcess(activity)
    }
}
